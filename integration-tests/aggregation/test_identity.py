"""验证双身份判定不会把空列表或混入记录当作通过。"""
import unittest
from identity import verify_samples, IdentityFailure


class IsolationTests(unittest.TestCase):
    def test_nonempty_distinct_samples_pass(self):
        self.assertEqual(verify_samples([{'keyName': 'own'}], 'own', 'other'), 1)

    def test_empty_samples_cannot_prove_isolation(self):
        with self.assertRaises(IdentityFailure):
            verify_samples([], 'own', 'other')

    def test_mixed_foreign_record_fails(self):
        with self.assertRaises(IdentityFailure):
            verify_samples([{'keyName': 'own'}, {'keyName': 'other'}], 'own', 'other')

    def test_two_identical_markers_cannot_prove_isolation(self):
        with self.assertRaises(IdentityFailure):
            verify_samples([{'keyName': 'same'}], 'same', 'same')
