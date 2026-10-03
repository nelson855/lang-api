"""受控 HTTP 测量，不返回原始正文、头部或身份。"""
import json
import time
import urllib.error
import urllib.request


def fetch(url, cookie=''):
    headers = {'Cookie': cookie} if cookie else {}
    request = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=35) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()


def measure(url, cookie):
    start = time.perf_counter()
    outcome = 'error'
    try:
        status, raw = fetch(url, cookie)
        payload = json.loads(raw)
        if status == 200 and isinstance(payload.get('data'), dict) and payload.get('error') is None:
            outcome = 'success'
        elif status == 400 and isinstance(payload.get('error'), dict) and payload['error'].get('code') == 'INVALID_ARGUMENT':
            outcome = 'protected'
    except (OSError, ValueError):
        pass
    return dict(durationMs=round((time.perf_counter()-start)*1000, 3), outcome=outcome,
                records=None, pages=None, upstreamCalls=None, cacheObserved='unknown',
                observationSource='client-http-only')
