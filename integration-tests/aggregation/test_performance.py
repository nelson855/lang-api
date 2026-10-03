import importlib.util
from pathlib import Path
import unittest
spec=importlib.util.spec_from_file_location('performance',Path(__file__).with_name('performance.py'))
mod=importlib.util.module_from_spec(spec); spec.loader.exec_module(mod)

class PerformanceTests(unittest.TestCase):
    def test_nearest_rank_uses_hand_derived_percentiles(self):
        self.assertEqual(mod.percentile(list(range(1,21)),.50),10)
        self.assertEqual(mod.percentile(list(range(1,21)),.95),19)
        self.assertEqual(mod.percentile([30,10,20],.95),30)

    def test_protection_and_errors_never_count_as_valid_latency_samples(self):
        samples=[dict(range='24h',mode='cold',durationMs=n,outcome='success',records=100,pages=5,upstreamCalls=1,cacheObserved='miss') for n in range(1,21)]
        samples += [dict(range='24h',mode='cold',durationMs=999,outcome='error'),dict(range='30d',mode='cold',durationMs=1,outcome='protected')]
        result=mod.summarize(samples,representative=True,resources_confirmed=True)
        self.assertIn('24h',result['ranges'])
        row=result['ranges']['24h']['cold']
        self.assertEqual(row['validSamples'],20)
        self.assertEqual(row['p95Ms'],19)
        self.assertEqual(result['status'],'BLOCKED') # 1h/7d/hot 缺失
        self.assertNotIn('30d',result['ranges'])
        self.assertEqual(result['protected'],1)
        self.assertEqual(result['errors'],1)

    def test_small_real_data_cannot_publish_baseline_or_meet_unconfirmed_target(self):
        samples=[dict(range=r,mode=m,durationMs=n,outcome='success',records=9,pages=1,upstreamCalls=1,cacheObserved='miss' if m=='cold' else 'hit') for r in ['1h','24h','7d'] for m in ['cold','hot'] for n in range(1,21)]
        small=mod.summarize(samples,representative=False,resources_confirmed=True)
        self.assertEqual(small['status'],'BLOCKED')
        self.assertIsNone(small['ranges']['1h']['cold']['p95Ms'])
        baseline=mod.summarize(samples,representative=True,resources_confirmed=True)
        self.assertEqual(baseline['status'],'PASS')
        self.assertEqual(baseline['targetStatus'],'BLOCKED')
        samples[0]['cacheObserved']='unknown'
        self.assertEqual(mod.summarize(samples,True,True)['status'],'BLOCKED')

if __name__=='__main__': unittest.main()

class SamplingTests(unittest.TestCase):
    def test_cold_waits_ttl_and_hot_reuses_query_without_wait(self):
        calls=[]; waits=[]
        def request(query):
            calls.append(query)
            return dict(durationMs=12, outcome='success', records=9, pages=1, upstreamCalls=1, cacheObserved='unknown')
        samples=mod.sample(request, count=2, ttl_seconds=30, end='2026-10-03T00:00:00Z', sleep=waits.append)
        self.assertEqual(len(samples),13) # 3 ranges * 2 cold/hot pairs + 30d rejection
        self.assertEqual(waits,[31]*6)
        self.assertEqual(calls[0],calls[1])
        self.assertEqual(calls[0]['startTime'],'2026-10-02T23:00:00Z')
        self.assertEqual(calls[-1]['startTime'],'2026-09-03T00:00:00Z')
        self.assertEqual(samples[-1]['outcome'],'error') # 30d accepted incorrectly
