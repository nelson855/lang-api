import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

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

    def test_failure_takes_priority_over_blocked(self):
        self.assertEqual(self.module.conclusion(['BLOCKED', 'FAIL', 'PASS']), 'FAIL')
        self.assertEqual(self.module.exit_code('FAIL'), 1)
        self.assertEqual(self.module.conclusion(['PASS', 'CANCELLED']), 'PASS')

if __name__ == '__main__':
    unittest.main()
