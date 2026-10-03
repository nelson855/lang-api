"""第二阶段验收；退出码只评价所选集合，上线条件单独判定。"""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import subprocess
import uuid

from evidence import capture_build, scan, project
from performance import sample, summarize
from transport import fetch, measure
import urllib.parse

ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / 'portal-api/target/probe-raw/p210'
MAVEN = '/Users/nelson/software/apache-maven-3.8.4/bin/mvn'
SETTINGS = '/Users/nelson/software/apache-maven-3.8.4/conf/settings.xml'
# 每个集合是封闭清单；前提缺失仍输出对应检查项。
CHECKS = {
    'contract': {
        'backend': '后端时间、保护、缓存、安全、观测契约',
        'frontend': '前端加载、空、错误、不可用、部分与取消字段契约',
        'browser-contract': '浏览器可控传输主流程及窄屏契约',
        'tool-tests': '验收入口、判定、证据与性能分类自测',
        'observability': '现有指标在真实聚合调用链产生，拒绝及HTTP计数含义明确',
    },
    'local': {
        'build': '运行 JAR、镜像、前端、上游版本与有效配置一致',
        'unauthenticated': '未登录受保护接口拒绝且监控不公开',
        'identity': '专用双用户身份隔离与清理恢复',
        'browser-real': '当前产物真实浏览器四页面及登录刷新',
        'reconciliation': '固定范围真实 Token、日志与定价对账',
        'upstream-evidence': '原阶段统计、金额、趋势、流水、时区、增强元数据证据',
    },
    'performance': {
        'build': '运行产物归属确认',
        'performance-smoke': '真实 1h/24h/7d 冷热明细与 30d 保护',
        'performance-baseline': '代表性规模每范围各 20 次冷、热有效请求',
        'performance-target': '已确认负载、部署资源及正式性能目标比较',
    },
}


def conclusion(states):
    states = list(states)
    if 'FAIL' in states:
        return 'FAIL'
    if not states or 'BLOCKED' in states:
        return 'BLOCKED'
    if any(s not in {'PASS', 'CANCELLED'} for s in states):
        raise ValueError('未知验收状态')
    return 'PASS'


def exit_code(state):
    return {'PASS': 0, 'FAIL': 1, 'BLOCKED': 2}[state]


def check(identifier, expected, state='BLOCKED', actual='未执行', reason='缺少适用证据', evidence_type='none', location=''):
    return dict(id=identifier, expected=expected, actual=actual, status=state,
                reason=reason, evidenceType=evidence_type, location=location)


def command_check(identifier, expected, command, cwd=ROOT):
    RAW.mkdir(parents=True, exist_ok=True)
    log = RAW / f'{identifier}.log'
    try:
        with log.open('w') as output:
            env = dict(os.environ, PATH=str(ROOT / 'frontend/node') + os.pathsep + os.environ.get('PATH', ''))
            result = subprocess.run(command, cwd=cwd, env=env, stdout=output, stderr=subprocess.STDOUT, timeout=1200)
        state = 'PASS' if result.returncode == 0 else 'FAIL'
        return check(identifier, expected, state, f'exit={result.returncode}',
                     '命令执行完成' if state == 'PASS' else '详见忽略目录诊断', 'controlled-contract', str(log.relative_to(ROOT)))
    except (OSError, subprocess.TimeoutExpired):
        return check(identifier, expected, reason='工具不可用或执行超时', location=str(log.relative_to(ROOT)))


def release_matrix(checks=()):
    states = {row['id']: row['status'] for row in checks}
    reasons = {
        1: '跨日/DST 上游证据、小时趋势、stat 时间过滤、流水与新基线未闭合',
        3: '请求总数、成功率、正式金额与30d 原目标受阻',
        4: '双趋势、完整最近请求与原指标目标受阻',
        5: '正式金额、TOPUP/REFUND 与容量证据受阻',
        6: '真实充值/退款、正式金额与30d 原目标受阻',
        7: '增强元数据与增强计费可信来源缺失',
        8: '真实增强模型来源缺失，合成展示不解除阻塞',
        10: '真实环境、代表性规模、部署资源与正式性能目标缺失',
    }
    rows = []
    for n in range(1, 11):
        required = ['backend', 'observability'] if n == 2 else ['backend', 'frontend', 'browser-real', 'reconciliation'] if n == 9 else []
        state = conclusion(states.get(key, 'BLOCKED') for key in required) if required else 'BLOCKED'
        rows.append(dict(id=f'P2-{n:02}', status=state,
                         reason=reasons.get(n, '本轮适用检查结论；缺项不得用历史证据代替'),
                         location='integration-tests/aggregation/matrix.md'))
    rows.extend([dict(id='P2-09-protocol', status='CANCELLED', reason='用户于 2026-10-03 明确取消', location='docs/18_LANG-P2-09-请求日志协议与TTFT与费用信息增强说明.md#9'),
                 dict(id='P2-09-ttft', status='CANCELLED', reason='用户于 2026-10-03 明确取消', location='docs/18_LANG-P2-09-请求日志协议与TTFT与费用信息增强说明.md#9')])
    return rows


def write_report(report, output):
    scan(report)
    output.mkdir(parents=True, exist_ok=True)
    (output / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    lines = ['# 第二阶段聚合验收报告', '', f"运行：{report['runId']}；集合：{report['suite']}；UTC：{report['time']}", '',
             f"所选现行契约检查：**{report['contractConclusion']}**", '',
             f"第二阶段上线条件：**{report['releaseConclusion']}**", '',
             '部分集合和模拟传输结果不能证明完整真实验收或容量达标。', '',
             '| 检查 | 状态 | 预期 | 实测 | 类型 | 原因 / 位置 |', '|---|---|---|---|---|---|']
    def cell(value):
        return str(value).replace('|', '\\|').replace('\n', ' ')
    for row in report['checks']:
        lines.append('| ' + ' | '.join(cell(row[k]) for k in ['id', 'status', 'expected', 'actual', 'evidenceType']) + f" | {cell(row['reason'])} {cell(row['location'])} |")
    lines += ['', '| 阶段条件 | 状态 | 依据 |', '|---|---|---|']
    lines += [f"| {r['id']} | {r['status']} | {r['reason']}；{r['location']} |" for r in report['releaseRequirements']]
    (output / 'report.md').write_text('\n'.join(lines) + '\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('suite', choices=['contract', 'local', 'performance', 'all'])
    parser.add_argument('--jar', type=Path, default=ROOT / 'portal-api/target/portal-api-0.1.0-SNAPSHOT.jar')
    parser.add_argument('--container', default='lang-api-lang-api-1')
    parser.add_argument('--upstream-container', default='lang-api-new-api-1')
    parser.add_argument('--base-url', default='http://localhost:8081')
    parser.add_argument('--samples', type=int, default=1)
    parser.add_argument('--end', default=datetime.now(timezone.utc).isoformat(timespec='seconds'))
    parser.add_argument('--plan-only', action='store_true', help='仅输出前提清单，所有未运行项 BLOCKED')
    parser.add_argument('--output', type=Path, default=ROOT / 'portal-api/target/p210-report')
    args = parser.parse_args()
    suites = list(CHECKS) if args.suite == 'all' else [args.suite]
    chosen = {}
    for suite in suites:
        chosen.update(CHECKS[suite])
    commands = {
        'backend': ([MAVEN, '-s', SETTINGS, '-pl', 'portal-api', 'test'], ROOT),
        'frontend': ([str(ROOT / 'frontend/node/node'), str(ROOT / 'frontend/node/node_modules/npm/bin/npm-cli.js'), 'test', '--', '--maxWorkers=2'], ROOT / 'frontend'),
        'browser-contract': ([str(ROOT / 'frontend/node/node'), str(ROOT / 'frontend/node/node_modules/npm/bin/npm-cli.js'), 'run', 'test:e2e'], ROOT / 'frontend'),
        'tool-tests': (['python3', '-m', 'unittest', 'discover', '-s', 'integration-tests/aggregation', '-p', 'test_*.py'], ROOT),
    }
    checks = []
    build = None
    if not args.plan_only and 'build' in chosen:
        try:
            build = capture_build(args.jar, ROOT / 'frontend/dist', args.container, args.upstream_container)
            scan(build)
        except (OSError, ValueError, subprocess.SubprocessError, KeyError):
            build = dict(status='BLOCKED', reason='Docker、构建或安全配置前提不足')
    samples = None
    for identifier, expected in chosen.items():
        if not args.plan_only and identifier == 'build':
            checks.append(check(identifier, expected, build['status'], '见构建清单',
                                '运行产物与前端摘要及非敏感配置核对', 'real-build', 'report.json#build'))
        elif not args.plan_only and identifier == 'unauthenticated' and build and build['status'] == 'PASS':
            try:
                statuses = [fetch(args.base_url + path)[0] for path in (
                    '/portal/api/dashboard/stats', '/portal/api/account/consumption-summary',
                    '/portal/api/account/transactions', '/portal/api/request-logs', '/actuator/metrics', '/actuator/env')]
                state = 'PASS' if statuses[:4] == [401]*4 and all(x in {401, 403, 404} for x in statuses[4:]) else 'FAIL'
                checks.append(check(identifier, expected, state, str(statuses), '匿名请求与监控关闭核验', 'real-local'))
            except OSError:
                checks.append(check(identifier, expected, reason='本地 HTTP 不可达'))
        elif not args.plan_only and identifier == 'performance-smoke' and build and build['status'] == 'PASS' and os.environ.get('P210_COOKIE'):
            try:
                ttl = build['effectiveConfig']['lang.aggregation.cache-ttl']
                if not ttl.endswith('s'):
                    raise ValueError('无法确认秒级缓存 TTL')
                samples = sample(lambda query: project(measure(args.base_url + '/portal/api/dashboard/stats?' + urllib.parse.urlencode(query), os.environ['P210_COOKIE'])),
                                 args.samples, float(ttl[:-1]), args.end)
                state = 'PASS' if all(s['outcome'] == ('protected' if s['range'] == '30d' else 'success') for s in samples) else 'FAIL'
                checks.append(check(identifier, expected, state, f'{len(samples)} 次；仅冒烟', '仅客户端观测，缓存及上游计数未确认', 'real-small-sample', 'report.json#samples'))
            except (ValueError, OSError, KeyError):
                checks.append(check(identifier, expected, reason='采样前提无效'))
        elif not args.plan_only and identifier in commands:
            command, cwd = commands[identifier]
            checks.append(command_check(identifier, expected, command, cwd))
        elif not args.plan_only and identifier == 'observability':
            checks.append(check(identifier, expected, reason='指标API与测试注册表已验证，但生产调用链未调用recordProtection；拒绝计数证据缺失，需后续观测变更', evidence_type='source-and-controlled-contract', location='integration-tests/aggregation/matrix.md'))
        else:
            checks.append(check(identifier, expected, reason='仅列前提，未执行' if args.plan_only else '缺少当前构建、专用身份或经核验的真实证据'))
    report = dict(runId=str(uuid.uuid4()), time=datetime.now(timezone.utc).isoformat(), suite=args.suite,
                  checks=checks, contractConclusion=conclusion(c['status'] for c in checks),
                  releaseRequirements=release_matrix(checks), releaseConclusion='BLOCKED')
    report['releaseConclusion'] = conclusion(r['status'] for r in report['releaseRequirements'])
    if build is not None:
        report['build'] = build
    if samples is not None:
        report['samples'] = samples
        report['performance'] = summarize(samples)
    write_report(report, args.output)
    print(f"现行契约={report['contractConclusion']}；第二阶段上线条件={report['releaseConclusion']}")
    return exit_code(report['contractConclusion'])

if __name__ == '__main__':
    raise SystemExit(main())
