import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).with_name('acceptance.py')

class EntryTests(unittest.TestCase):
    def test_all_keeps_missing_real_prerequisites_and_exit_two(self):
        with tempfile.TemporaryDirectory() as tmp:
            result = subprocess.run([sys.executable, str(SCRIPT), 'all', '--plan-only', '--output', tmp], capture_output=True, text=True)
            self.assertEqual(result.returncode, 2, result.stderr)
            report = json.loads((Path(tmp) / 'report.json').read_text())
            self.assertEqual(report['contractConclusion'], 'BLOCKED')
            self.assertEqual(report['releaseConclusion'], 'BLOCKED')
            self.assertTrue({'build', 'identity', 'reconciliation', 'performance-smoke', 'performance-baseline', 'performance-target'} <= {c['id'] for c in report['checks']})
            self.assertIn('第二阶段上线条件', (Path(tmp) / 'report.md').read_text())

    def test_contract_selection_cannot_claim_release(self):
        with tempfile.TemporaryDirectory() as tmp:
            result = subprocess.run([sys.executable, str(SCRIPT), 'contract', '--plan-only', '--output', tmp], capture_output=True, text=True)
            self.assertEqual(result.returncode, 2)
            report = json.loads((Path(tmp) / 'report.json').read_text())
            self.assertEqual(report['releaseConclusion'], 'BLOCKED')
            self.assertFalse(any(c['id'] == 'identity' for c in report['checks']))

class DecisionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location('acceptance', SCRIPT)
        cls.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.module)

    def test_sensitive_report_is_rejected_before_either_file_is_saved(self):
        with tempfile.TemporaryDirectory() as tmp:
            report = dict(runId='run', time='now', suite='contract', contractConclusion='FAIL', releaseConclusion='BLOCKED', checks=[self.module.check('safety', 'no secrets', actual='Bearer abcdefghijklmnopqrstuvwxyz')], releaseRequirements=[])
            with self.assertRaises(ValueError):
                self.module.write_report(report, Path(tmp))
            self.assertFalse((Path(tmp)/'report.json').exists())
            self.assertFalse((Path(tmp)/'report.md').exists())

    def test_release_matrix_uses_current_proof_without_closing_external_gaps(self):
        rows=self.module.release_matrix([self.module.check('backend', 'contract', 'PASS'), self.module.check('observability', 'metrics', 'PASS')])
        states={row['id']:row['status'] for row in rows}
        self.assertEqual(states['P2-02'],'PASS')
        self.assertEqual(states['P2-01'],'BLOCKED')
        self.assertEqual(states['P2-09-protocol'],'CANCELLED')
        self.assertEqual(self.module.conclusion(states.values()),'BLOCKED')

    def test_observability_executes_current_chain_and_keeps_failures(self):
        with patch.object(self.module, 'command_check', return_value=self.module.check('observability', 'metrics', 'FAIL')) as execute:
            result = self.module.observability_check('metrics')
            self.assertEqual(result['status'], 'FAIL')
            self.assertIn('-Dtest=AggregationObservabilityChainTests', execute.call_args.args[2])

    def test_observability_missing_current_evidence_stays_blocked(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(self.module, 'ROOT', Path(tmp)):
            with patch.object(self.module, 'command_check', return_value=self.module.check('observability', 'metrics', 'PASS')):
                self.assertEqual(self.module.observability_check('metrics')['status'], 'BLOCKED')

    def test_observability_invalid_evidence_fails(self):
        with tempfile.TemporaryDirectory() as tmp, patch.object(self.module, 'ROOT', Path(tmp)):
            def execute(*args):
                source = Path(tmp) / 'portal-api/target/p210-observability.json'
                source.parent.mkdir(parents=True)
                source.write_text('{"observationSource":"wrong","scenarios":[]}')
                return self.module.check('observability', 'metrics', 'PASS')
            with patch.object(self.module, 'command_check', side_effect=execute), patch.object(self.module, 'scan'):
                self.assertEqual(self.module.observability_check('metrics')['status'], 'FAIL')

    def test_failure_takes_priority_over_blocked(self):
        self.assertEqual(self.module.conclusion(['BLOCKED', 'FAIL', 'PASS']), 'FAIL')
        self.assertEqual(self.module.exit_code('FAIL'), 1)
        self.assertEqual(self.module.conclusion(['PASS', 'CANCELLED']), 'PASS')

    def test_identity_missing_credentials_stays_blocked(self):
        with patch.dict('os.environ', {}, clear=True):
            self.assertEqual(self.module.identity_check('isolation', 'http://127.0.0.1:18081',
                                                       'isolated', 'end', Path('.'))['status'], 'BLOCKED')

    def test_identity_keeps_real_failure_and_scans_before_saving(self):
        with tempfile.TemporaryDirectory() as tmp, patch.dict('os.environ',
                {'P210_IDENTITY_FILE': 'private-input', 'P210_IDENTITY_START': 'start'}):
            evidence = dict(status='FAIL', checks=[dict(assertion='logout-replay', passed=False)],
                            observationSource='real-local-dual-user')
            with patch.object(self.module, 'run_identity', return_value=evidence) as execute:
                result = self.module.identity_check('isolation', 'http://127.0.0.1:18081', 'isolated', 'end', Path(tmp))
                self.assertEqual(result['status'], 'FAIL')
                self.assertEqual(execute.call_count, 1)
                self.assertEqual(json.loads((Path(tmp) / 'identity.json').read_text())['status'], 'FAIL')
            with patch.object(self.module, 'run_identity', return_value=dict(evidence, password='forbidden')):
                self.assertEqual(self.module.identity_check('isolation', 'http://127.0.0.1:18081',
                                                           'isolated', 'end', Path(tmp))['status'], 'FAIL')

if __name__ == '__main__':
    unittest.main()
