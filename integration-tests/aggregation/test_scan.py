import importlib.util
from pathlib import Path
import unittest
spec=importlib.util.spec_from_file_location('evidence',Path(__file__).with_name('evidence.py'))
mod=importlib.util.module_from_spec(spec); spec.loader.exec_module(mod)

class ScannerTests(unittest.TestCase):
    def test_existing_scanner_rejects_bearer_in_allowlisted_measurement(self):
        with self.assertRaises(ValueError):
            mod.scan({'actual':'Bearer abcdefghijklmnopqrstuvwxyz'})
        mod.scan({'status':'PASS','durationMs':12,'jarSha256':'a'*64})

if __name__=='__main__': unittest.main()
