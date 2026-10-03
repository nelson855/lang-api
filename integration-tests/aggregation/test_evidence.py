import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('evidence', Path(__file__).with_name('evidence.py'))
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

class EvidenceTests(unittest.TestCase):
    def test_old_running_jar_fails_even_with_same_tag(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); jar=root/'app.jar'; running=root/'running.jar'; dist=root/'dist'; dist.mkdir()
            (dist/'index.html').write_text('current')
            with zipfile.ZipFile(jar,'w') as z: z.writestr('BOOT-INF/classes/static/index.html','current')
            with zipfile.ZipFile(running,'w') as z: z.writestr('BOOT-INF/classes/static/index.html','old')
            self.assertEqual(mod.compare_artifacts(jar,running,dist)['status'],'FAIL')
            self.assertEqual(mod.compare_artifacts(jar,jar,dist)['status'],'PASS')
            (dist/'new.js').write_text('missing in jar')
            self.assertEqual(mod.compare_artifacts(jar,jar,dist)['status'],'FAIL')

    def test_projection_rejects_secret_in_unknown_field_before_saving(self):
        for field in ['password','cookie','Authorization','userId','request_body']:
            with self.subTest(field=field):
                with self.assertRaises(ValueError):
                    mod.project({'durationMs': 12, 'extra': {field:'private'}})
        self.assertEqual(mod.project({'durationMs':12, 'unknown':'discard', 'records':9}), {'durationMs':12,'records':9})

    def test_configuration_is_allowlisted_and_profile_overrides(self):
        root=Path(__file__).resolve().parents[2]/'portal-api/src/main/resources'
        config=mod.effective_config(root,'test')
        self.assertEqual(config.get('lang.aggregation.cache-maximum-size'),'100')
        self.assertEqual(config.get('lang.aggregation.max-live-log-range'),'168h')
        self.assertNotIn('lang.auth.cookie.session-name',config)

if __name__ == '__main__': unittest.main()
