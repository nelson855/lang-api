"""构建归属；只持久化白名单字段，不输出输入中的秘密或扫描 snippet。"""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / 'portal-api/target/probe-raw/p210'
CONFIG_KEYS = tuple('lang.aggregation.' + key for key in (
    'baseline-version', 'page-size', 'max-pages', 'max-records', 'single-call-timeout',
    'total-timeout', 'max-live-log-range', 'cache-ttl', 'cache-maximum-size',
    'five-minutes-max-span', 'hour-max-span', 'day-max-span')) + (
    'management.endpoints.web.exposure.include', 'management.endpoint.health.show-details')
PROJECTION_KEYS = {'range', 'mode', 'durationMs', 'records', 'pages', 'upstreamCalls',
                   'outcome', 'cacheObserved', 'observationSource'}
FORBIDDEN = {'password', 'cookie', 'setcookie', 'authorization', 'userid', 'username',
             'email', 'ip', 'apikey', 'accesstoken', 'session', 'secret', 'prompt',
             'messages', 'requestbody', 'responsebody', 'content', 'rawrequest', 'rawresponse'}


def reject_secrets(data):
    if isinstance(data, dict):
        for key, value in data.items():
            normalized = re.sub('[^a-z0-9]', '', key.lower())
            if normalized in FORBIDDEN or normalized.endswith(('password', 'secret', 'token')):
                raise ValueError('检测到禁止持久化字段')
            reject_secrets(value)
    elif isinstance(data, list):
        for value in data:
            reject_secrets(value)


def project(data):
    reject_secrets(data)
    return {key: value for key, value in data.items() if key in PROJECTION_KEYS}


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def compare_artifacts(jar, running, dist):
    prefix = 'BOOT-INF/classes/static/'
    with zipfile.ZipFile(jar) as archive:
        bundled = {name[len(prefix):]: hashlib.sha256(archive.read(name)).hexdigest()
                   for name in archive.namelist() if name.startswith(prefix) and not name.endswith('/')}
    built = {str(p.relative_to(dist)): sha(p) for p in Path(dist).rglob('*') if p.is_file()}
    equal = bool(built) and bundled == built
    return dict(status='PASS' if sha(jar) == sha(running) and equal else 'FAIL',
                jarSha256=sha(jar), runningJarSha256=sha(running), frontendEqual=equal,
                frontendFiles=len(built), frontendSha256=hashlib.sha256(json.dumps(built, sort_keys=True).encode()).hexdigest())


def properties(text):
    return dict(line.split('=', 1) for line in text.splitlines()
                if '=' in line and not line.lstrip().startswith('#'))


def effective_config(root, profile):
    config = properties((Path(root) / 'application.properties').read_text())
    config.update(properties((Path(root) / f'application-{profile}.properties').read_text()))
    return {key: config[key] for key in CONFIG_KEYS if key in config}


def scan(data):
    """通过 Java source launcher 复用仓库现有 scanner，错误只返回通用原因。"""
    reject_secrets(data)
    repository = Path('/Users/nelson/software/apache-maven-3.8.4/repository/com/fasterxml/jackson/core')
    jars = []
    for artifact in ('jackson-databind', 'jackson-core', 'jackson-annotations'):
        candidates = sorted((repository / artifact).glob('*/*.jar'))
        candidates = [p for p in candidates if not p.name.endswith(('-sources.jar', '-javadoc.jar'))]
        if not candidates:
            raise RuntimeError('敏感扫描依赖缺失')
        jars.append(str(candidates[-1]))
    classpath = os.pathsep.join([str(ROOT / 'portal-api/target/classes'), *jars])
    result = subprocess.run(['java', '--class-path', classpath,
                             str(ROOT / 'integration-tests/aggregation/ScanEvidence.java')],
                            input=json.dumps(data, ensure_ascii=False), capture_output=True, text=True)
    if result.returncode:
        raise ValueError('持久化敏感扫描失败')


def capture_build(jar, dist, container, upstream):
    RAW.mkdir(parents=True, exist_ok=True)
    def docker(*args):
        return subprocess.check_output(['docker', *args], stderr=subprocess.DEVNULL, text=True).strip()
    running = RAW / 'running.jar'
    subprocess.run(['docker', 'cp', f'{container}:/app/app.jar', str(running)],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    result = compare_artifacts(jar, running, dist)
    app = json.loads(docker('inspect', container))[0]
    newapi = json.loads(docker('inspect', upstream))[0]
    image = json.loads(docker('image', 'inspect', newapi['Image']))[0]
    env = dict(entry.split('=', 1) for entry in app['Config']['Env'] if '=' in entry)
    profile = env.get('SPRING_PROFILES_ACTIVE', '')
    if profile not in {'dev', 'test', 'prod'}:
        raise ValueError('运行 profile 无法确认')
    with zipfile.ZipFile(running) as archive:
        prefix = 'BOOT-INF/classes/'
        config = properties(archive.read(prefix + 'application.properties').decode())
        config.update(properties(archive.read(prefix + f'application-{profile}.properties').decode()))
    effective = {key: env.get(key.upper().replace('.', '_').replace('-', ''), config.get(key)) for key in CONFIG_KEYS}
    # 外部配置/命令行可能改写任意键，无法确认时阻塞，不能推断有效配置。
    overrides = any(key in env for key in ('SPRING_APPLICATION_JSON', 'SPRING_CONFIG_LOCATION', 'SPRING_CONFIG_ADDITIONAL_LOCATION', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS'))
    overrides = overrides or any(str(arg).startswith('--') for arg in (app['Config'].get('Cmd') or []))
    overrides = overrides or bool(app.get('Mounts'))
    digests = image.get('RepoDigests', [])
    result.update(environment='local-docker', profile=profile, imageId=app['Image'],
                  newApiImageId=newapi['Image'], newApiVersion=newapi['Config']['Image'],
                  newApiDigest=digests[0] if digests else '', effectiveConfig=effective,
                  configConfirmed=not overrides)
    if result['status'] != 'FAIL' and (overrides or not digests):
        result['status'] = 'BLOCKED'
    return result
