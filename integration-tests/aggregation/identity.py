"""真实双身份验收：凭据仅在内存/受保护输入中使用，输出只含断言和计数。"""
import http.cookiejar
from datetime import datetime
import json
from pathlib import Path
import secrets
import subprocess
import urllib.error
import urllib.parse
import urllib.request
from evidence import scan


class IdentityFailure(Exception):
    pass


class IdentityBlocked(Exception):
    pass


def verify_samples(rows, own_marker, foreign_marker):
    if not own_marker or own_marker == foreign_marker:
        raise IdentityFailure('样本标记不能区分两名测试身份')
    if not rows or not any(row.get('keyName') == own_marker for row in rows):
        raise IdentityFailure('缺少本用户非空成功样本')
    if any(row.get('keyName') == foreign_marker for row in rows):
        raise IdentityFailure('结果混入另一测试身份的样本')
    return len(rows)


def local_url(base):
    parsed = urllib.parse.urlsplit(base)
    if parsed.scheme != 'http' or parsed.hostname not in {'localhost', '127.0.0.1'} or parsed.path not in {'', '/'} or parsed.query or parsed.fragment or parsed.username:
        raise IdentityBlocked('身份验收仅接受本地回环HTTP入口')
    return base.rstrip('/')


class Session:
    def __init__(self, base, origin=None):
        self.base = local_url(base)
        self.origin = origin or self.base
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.csrf = None

    def request(self, path, method='GET', body=None, headers=None):
        headers = dict(headers or {})
        if method != 'GET':
            if self.csrf is None:
                status, payload = self.request('/portal/api/auth/csrf')
                if status != 200:
                    raise IdentityFailure('CSRF初始化失败')
                self.csrf = payload['data']['token']
            headers.update({'Origin': self.origin, 'X-XSRF-TOKEN': self.csrf, 'Content-Type': 'application/json'})
        data = json.dumps(body).encode() if body is not None else None
        request = urllib.request.Request(self.base + path, data=data, headers=headers, method=method)
        try:
            with self.opener.open(request, timeout=35) as response:
                return response.status, json.load(response)
        except urllib.error.HTTPError as response:
            try:
                return response.code, json.loads(response.read())
            except ValueError:
                return response.code, {}

    def data(self, path, method='GET', body=None):
        status, payload = self.request(path, method, body)
        if status != 200 or payload.get('error'):
            code = payload.get('error', {}).get('code', 'HTTP_ERROR')
            raise IdentityFailure('接口检查未通过：' + str(status) + '/' + code)
        return payload.get('data')

    def login(self, account):
        return self.data('/portal/api/auth/login', 'POST',
                         {key: account[key] for key in ('username', 'password')})

    def cookie(self):
        return '; '.join(cookie.name + '=' + cookie.value for cookie in self.jar)


def log_calls(container):
    # 隔离实例只有验收客户端访问；只将次数投影到报告，完整日志不输出。
    result = subprocess.run(['docker', 'logs', container], capture_output=True, text=True, timeout=15)
    if result.returncode:
        raise IdentityBlocked('临时实例日志观测不可用')
    return sum('event=new_api_call operation=log-self ' in line
               for line in (result.stdout + result.stderr).splitlines())


def replay_protected_paths(window):
    """退出后必须一并失效的受保护接口：Dashboard、账户、流水、日志与 Key。

    使用与前面缓存预热完全相同的查询参数，确保聚合缓存命中路径也无法绕过撤销。
    """
    query = urllib.parse.urlencode({'startTime': window['start'], 'endTime': window['end'],
                                    'granularity': 'HOUR', 'timezone': 'UTC'})
    logs = urllib.parse.urlencode({'startTime': window['start'], 'endTime': window['end']})
    return [
        ('dashboard', '/portal/api/dashboard/stats?' + query),
        ('account-summary', '/portal/api/account/consumption-summary?' + query),
        ('account-transactions', '/portal/api/account/transactions?' + query + '&type=CONSUMPTION'),
        ('request-logs', '/portal/api/request-logs?' + logs),
        ('api-keys', '/portal/api/api-keys'),
        ('usage-summary', '/portal/api/usage/summary?' + query),
    ]


def run_identity(base, credential_file, start, end, container, origin=None, fail_after_keys=False):
    try:
        bounds = [datetime.fromisoformat(value.replace('Z', '+00:00')) for value in (start, end)]
        if any(value.tzinfo is None or value.microsecond for value in bounds) or not 0 < (bounds[1]-bounds[0]).total_seconds() <= 3600:
            raise ValueError()
    except (ValueError, TypeError):
        raise IdentityBlocked('需要带时区、秒精度、不超过一小时的固定范围') from None
    credential_file = Path(credential_file)
    if not credential_file.exists() or credential_file.stat().st_mode & 0o077:
        raise IdentityBlocked('需要权限为600的专用身份文件')
    accounts = json.loads(credential_file.read_text())
    if len(accounts) != 2 or any(not a.get('sample_key_name') for a in accounts):
        raise IdentityBlocked('需要两名专用身份及各自真实成功样本标记')
    sessions = [Session(base, origin), Session(base, origin)]
    keys = []
    observations = []
    primary_failure = None
    cleanup_ok = True
    saved_cookies = []
    saved_range = None
    suffix = secrets.token_hex(4)
    try:
        profiles = [session.login(account) for session, account in zip(sessions, accounts)]
        if profiles[0]['id'] == profiles[1]['id']:
            raise IdentityFailure('登录身份未区分')
        for index, session in enumerate(sessions):
            name = 'p210-b2-check-' + suffix + '-' + str(index)
            # 名称先登记；创建成功但读取ID失败时也能通过名称清理。
            keys.append((session, name))
            session.data('/portal/api/api-keys', 'POST',
                         {'name': name, 'unlimited': False, 'remaining': 0, 'models': [], 'ips': []})
        if fail_after_keys:
            raise IdentityFailure('受控失败恢复演练')
        owned = []
        for session, name in keys:
            rows = session.data('/portal/api/api-keys')['items']
            matching = [row for row in rows if row['name'] == name]
            if len(matching) != 1:
                raise IdentityFailure('本用户测试Key未唯一出现')
            owned.append(matching[0]['id'])
        for index, session in enumerate(sessions):
            foreign = owned[1-index]
            if any(row['id'] == foreign for row in session.data('/portal/api/api-keys')['items']):
                raise IdentityFailure('Key列表混入其他用户资源')
            status, _ = session.request('/portal/api/api-keys/' + str(foreign))
            observations.append({'assertion': 'cross-user-key-detail-not-found',
                                 'passed': status == 404, 'httpStatus': status})
        observations.append({'assertion': 'nonempty-key-list-and-cross-user-detail', 'passed': True})
        params = urllib.parse.urlencode({'startTime': start, 'endTime': end})
        log_rows = [session.data('/portal/api/request-logs?' + params)['items'] for session in sessions]
        for index, rows in enumerate(log_rows):
            verify_samples(rows, accounts[index]['sample_key_name'], accounts[1-index]['sample_key_name'])
        observations.append({'assertion': 'nonempty-real-log-isolation', 'passed': True,
                             'records': [len(rows) for rows in log_rows]})
        query = urllib.parse.urlencode({'startTime': start, 'endTime': end, 'granularity': 'HOUR', 'timezone': 'UTC'})
        paths = ['/portal/api/dashboard/stats?' + query,
                 '/portal/api/account/consumption-summary?' + query,
                 '/portal/api/account/transactions?' + query + '&type=CONSUMPTION']
        before = log_calls(container)
        cold = [[session.data(path) for path in paths] for session in sessions]
        after_cold = log_calls(container)
        hot = [[session.data(path) for path in paths] for session in sessions]
        after_hot = log_calls(container)
        if cold != hot or after_hot != after_cold or after_cold <= before:
            raise IdentityFailure('非空结果重复查询或缓存上游观测不符')
        transaction_ids = []
        for index, results in enumerate(cold):
            verify_samples(results[0]['recentRequests']['items'],
                           accounts[index]['sample_key_name'], accounts[1-index]['sample_key_name'])
            count = int(results[1]['recordCount']['value'])
            quota = sum(int(row['quota']) for row in log_rows[index])
            if count != len(log_rows[index]) or int(results[1]['quotaTotal']['value']) != quota:
                raise IdentityFailure('账户快照与该身份的独立日志不一致')
            items = results[2]['items']
            if not items or sum(int(item['amount']) for item in items) != quota:
                raise IdentityFailure('消费流水与该身份的日志不一致')
            transaction_ids.append({item['transactionId'] for item in items})
        if transaction_ids[0] & transaction_ids[1]:
            raise IdentityFailure('消费流水标识跨用户混入')
        if cold[0][1]['quotaTotal']['value'] == cold[1][1]['quotaTotal']['value']:
            raise IdentityBlocked('两名身份聚合数值相同，需要可区分的消费样本')
        observations.append({'assertion': 'dashboard-account-and-transaction-cache-isolation',
                             'passed': True, 'coldLogCalls': after_cold-before, 'hotLogCalls': after_hot-after_cold,
                             'observationSource': 'isolated-container-upstream-call-events'})
        for index, session in enumerate(sessions):
            for path in paths + ['/portal/api/api-keys']:
                status, _ = session.request(path + ('&' if '?' in path else '?') + 'userId=' + str(profiles[1-index]['id']))
                if status != 400:
                    raise IdentityFailure('伪造用户参数未拒绝')
            cookie = session.cookie()
            forged = cookie.replace('LANG_UID=' + str(profiles[index]['id']), 'LANG_UID=' + str(profiles[1-index]['id']))
            anonymous = Session(base, origin)
            status, _ = anonymous.request('/portal/api/profile', headers={'Cookie': forged})
            if status != 401:
                raise IdentityFailure('伪造身份Cookie未拒绝')
            status, _ = anonymous.request('/portal/api/profile', headers={'Cookie': 'LANG_SESSION=invalid; LANG_UID=' + str(profiles[index]['id'])})
            if status != 401:
                raise IdentityFailure('无效会话未拒绝')
        observations.append({'assertion': 'forged-query-cookie-and-invalid-session', 'passed': True})
        saved_cookies = [session.cookie() for session in sessions]
        saved_range = {'start': start, 'end': end}
    except (IdentityFailure, IdentityBlocked, OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError) as failure:
        primary_failure = failure
    finally:
        # 正常及失败路径均按登记名称找回资源；账号与业务日志保留。
        for session, name in keys:
            try:
                rows = session.data('/portal/api/api-keys')['items']
                for row in rows:
                    if row['name'] == name:
                        session.data('/portal/api/api-keys/' + str(row['id']), 'DELETE')
                if any(row['name'] == name for row in session.data('/portal/api/api-keys')['items']):
                    cleanup_ok = False
            except Exception:
                cleanup_ok = False
        for session in sessions:
            try:
                session.data('/portal/api/auth/logout', 'POST')
                if session.request('/portal/api/profile')[0] != 401:
                    cleanup_ok = False
            except Exception:
                cleanup_ok = False
    observations.append({'assertion': 'key-cleanup-and-browser-cookie-clearing', 'passed': cleanup_ok})
    for cookie in saved_cookies:
        replay = Session(base, origin)
        statuses = {'profile': replay.request('/portal/api/profile', headers={'Cookie': cookie})[0]}
        # 覆盖其余受保护接口：这些路径此前已被该会话预热（含聚合缓存），
        # 若撤销只在 profile 生效而在缓存路径失效，等于旧会话仍可用。
        replay_paths = replay_protected_paths(saved_range) if saved_range else []
        for name, path in replay_paths:
            statuses[name] = replay.request(path, headers={'Cookie': cookie})[0]
        blocked = {name: code for name, code in statuses.items() if code != 401}
        observations.append({'assertion': 'logged-out-session-replay', 'passed': not blocked,
                             'httpStatus': statuses['profile'], 'protectedEndpoints': len(statuses),
                             'nonUnauthorizedEndpoints': sorted(blocked)})
    if not cleanup_ok:
        raise IdentityFailure('测试Key或会话清理未通过')
    if primary_failure:
        if fail_after_keys and isinstance(primary_failure, IdentityFailure) and str(primary_failure) == '受控失败恢复演练':
            result = {'status': 'PASS', 'observationSource': 'real-local-controlled-failure',
                      'checks': [{'assertion': 'injected-failure-after-key-creation', 'passed': True},
                                 {'assertion': 'temporary-keys-removed-and-browser-cookies-cleared', 'passed': True}],
                      'accountsRetained': True, 'temporaryKeysRemoved': True,
                      'sessionsCookiesCleared': True, 'sessionRevocationVerified': False}
            scan(result)
            return result
        raise primary_failure
    result = {'status': 'PASS' if all(row['passed'] for row in observations) else 'FAIL',
              'observationSource': 'real-local-dual-user', 'checks': observations,
              'accountsRetained': True, 'temporaryKeysRemoved': True, 'sessionsCookiesCleared': True,
              'sessionRevocationVerified': all(row['passed'] for row in observations if row['assertion'] == 'logged-out-session-replay'),
              'range': {'start': start, 'end': end, 'timezone': 'UTC'}}
    scan(result)
    return result
