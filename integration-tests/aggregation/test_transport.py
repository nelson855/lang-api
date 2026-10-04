import importlib.util
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Thread
import unittest
spec=importlib.util.spec_from_file_location('transport',Path(__file__).with_name('transport.py'))
mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)

class TransportTests(unittest.TestCase):
    def test_real_http_classifies_success_rejection_and_upstream_error_without_payload(self):
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                code=400 if self.path=='/reject' else 502 if self.path=='/error' else 200
                self.send_response(code);self.end_headers()
                self.wfile.write(json.dumps({'requestId':'req-fixture', 'data':{'secret':'not persisted'} if code==200 and self.path!='/malformed' else None, 'error':None if code==200 else {'code':'INVALID_ARGUMENT' if code==400 else 'UPSTREAM_ERROR','message':'upstream rejected'}}).encode())
            def log_message(self,*args): pass
        server=ThreadingHTTPServer(('127.0.0.1',0),Handler);thread=Thread(target=server.serve_forever);thread.start()
        try:
            base=f'http://127.0.0.1:{server.server_port}'
            ok=mod.measure(base+'/ok','private')
            self.assertEqual(ok.get('outcome'),'success')
            self.assertNotIn('secret',ok)
            self.assertNotIn('cookie',ok)
            self.assertEqual(mod.measure(base+'/malformed','private').get('outcome'),'error')
            self.assertIsNone(ok.get('pages')) # 没有真实观测时禁止推断页数
            self.assertEqual(mod.measure(base+'/reject','private').get('outcome'),'protected')
            self.assertEqual(mod.measure(base+'/error','private').get('outcome'),'error')
        finally:
            server.shutdown();thread.join();server.server_close()

if __name__=='__main__':unittest.main()
