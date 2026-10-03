"""最近秩百分位；保护拒绝不计容量，未知缓存/规模/资源不能发布基线。"""
import math
import time
from datetime import datetime, timedelta, timezone
import json
import urllib.error
import urllib.parse
import urllib.request


def percentile(values, quantile):
    if not values or not 0 < quantile <= 1:
        raise ValueError('百分位参数或样本无效')
    return sorted(values)[math.ceil(len(values) * quantile) - 1]


def summarize(samples, representative=False, resources_confirmed=False):
    ranges = {}
    publish = representative and resources_confirmed
    for period in ('1h', '24h', '7d'):
        ranges[period] = {}
        for mode in ('cold', 'hot'):
            selected = [s for s in samples if s.get('range') == period and s.get('mode') == mode and s.get('outcome') == 'success']
            valid = [s for s in selected if s.get('cacheObserved') == ('miss' if mode == 'cold' else 'hit')
                     and isinstance(s.get('durationMs'), (int, float)) and s['durationMs'] >= 0
                     and all(isinstance(s.get(k), int) and s[k] >= 0 for k in ('records', 'pages', 'upstreamCalls'))]
            eligible = publish and len(valid) >= 20
            row = dict(validSamples=len(valid), successfulSamples=len(selected), p50Ms=None, p95Ms=None,
                       status='PASS' if eligible else 'BLOCKED')
            if eligible:
                values = [s['durationMs'] for s in valid]
                row.update(p50Ms=percentile(values, .50), p95Ms=percentile(values, .95))
            ranges[period][mode] = row
    return dict(ranges=ranges, status='PASS' if all(r['status'] == 'PASS' for modes in ranges.values() for r in modes.values()) else 'BLOCKED',
                targetStatus='BLOCKED', errors=sum(s.get('outcome') == 'error' for s in samples),
                protected=sum(s.get('outcome') == 'protected' for s in samples))

def sample(request, count, ttl_seconds, end, sleep=time.sleep):
    if count < 1 or ttl_seconds <= 0:
        raise ValueError('采样数量与实际 TTL 必须为正数')
    end_time = datetime.fromisoformat(end.replace('Z', '+00:00'))
    if end_time.tzinfo is None:
        raise ValueError('采样结束时间必须带时区')
    end_time = end_time.astimezone(timezone.utc)
    def iso(value):
        return value.isoformat(timespec='seconds').replace('+00:00', 'Z')
    samples = []
    for period, hours in [('1h', 1), ('24h', 24), ('7d', 168), ('30d', 720)]:
        query = dict(startTime=iso(end_time-timedelta(hours=hours)), endTime=iso(end_time),
                     timezone='UTC', granularity='HOUR' if hours <= 168 else 'DAY')
        if period == '30d':
            result = dict(request(query), range=period, mode='cold')
            if result.get('outcome') != 'protected':
                result['outcome'] = 'error'
            samples.append(result)
            continue
        for _ in range(count):
            sleep(ttl_seconds + 1)
            for mode in ('cold', 'hot'):
                samples.append(dict(request(query), range=period, mode=mode))
    return samples
