#!/usr/bin/env python3
"""仅供测试使用的 New API Relay fixture（LANG-P1-07 任务 1.1）。

只依赖 Python 标准库，不引入第三方包。用固定查询参数控制行为，
供网关普通 JSON / SSE / 超时 / 取消 / Header 契约测试使用。

- GET  /v1/models：返回固定模型列表 JSON。
- POST /v1/chat/completions：请求体含 "stream": true 时以
  text/event-stream 分段返回，否则返回普通 JSON。
- 查询参数（同时适用于上面两条路径）：
  status=N      指定返回状态码（默认 200）。
  delay=S       响应前等待 S 秒（模拟慢上游 / 触发网关超时）。
  disconnect=1  直接中断连接（模拟上游断连，不返回完整响应）。
  chunks=N      SSE 数据块数量（默认 3）。
  chunk_delay=S 每个 SSE 块之间等待 S 秒（默认 0.1）。
- GET /__control/health：fixture 自身健康检查，返回 ok。
- GET /__control/counts：返回各路径请求计数（request_count）。
- POST /__control/reset：计数与取消记录清零。
- GET /__control/cancelled：返回已观测到的上游取消记录。
- GET /__control/last：返回上一次模型请求的 Header 回显
  （Authorization 只保留 scheme 与是否存在标记，不记录完整 Key；
  同时报告 Cookie / 伪造转发头的出现情况与网关 X-Request-Id）。

说明：这是在测试容器或本机运行的假上游，只使用假 Key，
不连接任何真实供应商。
"""
import json
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

STATE_LOCK = threading.Lock()
REQUEST_COUNT = {}
CANCELLED = []
LAST_REQUEST = {}


def _bump(path):
    with STATE_LOCK:
        REQUEST_COUNT[path] = REQUEST_COUNT.get(path, 0) + 1
        return REQUEST_COUNT[path]


class RelayHandler(BaseHTTPRequestHandler):
    server_version = "RelayFixture/1"
    protocol_version = "HTTP/1.1"

    def log_message(self, *args):
        pass

    def _query(self):
        return parse_qs(urlparse(self.path).query)

    def _send_json(self, code, obj):
        body = json.dumps(obj).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        # 测试专用上游 Header：网关必须按隐藏清单剥离，客户端不得看到。
        self.send_header("ETag", '"fixture-etag"')
        self.send_header("X-Cache", "HIT from fixture")
        self.send_header("X-Request-Id", self.headers.get("X-Request-Id", "fixture-request-id"))
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _maybe_delay_or_disconnect(self, query):
        if "delay" in query:
            time.sleep(float(query["delay"][0]))
        if query.get("disconnect", ["0"])[0] == "1":
            self.connection.close()
            return True
        return False

    def _snapshot_request(self, route):
        headers = {k: v for k, v in self.headers.items()}
        auth = headers.get("Authorization", "")
        scheme = auth.split(" ", 1)[0] if auth else ""
        forbidden = [h for h in ("Cookie", "New-Api-User", "Forwarded",
                                 "X-Forwarded-For", "X-Forwarded-Proto",
                                 "X-Forwarded-Host")
                     if h in headers]
        snapshot = {
            "route": route,
            "method": self.command,
            "request_count": _bump(route),
            "has_authorization": bool(auth),
            "auth_scheme": scheme,
            "has_cookie": "Cookie" in headers,
            "forbidden_headers_present": forbidden,
            "content_type": headers.get("Content-Type", ""),
            "accept": headers.get("Accept", ""),
            "x_request_id": headers.get("X-Request-Id", ""),
        }
        with STATE_LOCK:
            LAST_REQUEST.clear()
            LAST_REQUEST.update(snapshot)
        return snapshot

    def _maybe_reject_test_key(self):
        cases = {
            "Bearer sk-test-disabled": (401, "AUTHENTICATION_FAILED"),
            "Bearer sk-test-expired": (401, "AUTHENTICATION_FAILED"),
            "Bearer sk-test-exhausted": (429, "RATE_LIMITED"),
            "Bearer sk-test-restricted": (403, "FORBIDDEN"),
        }
        selected = cases.get(self.headers.get("Authorization", ""))
        if selected is None:
            return False
        status, code = selected
        request_id = self.headers.get("X-Request-Id", "fixture-request-id")
        self._send_json(status, {
            "requestId": request_id,
            "error": {
                "code": code,
                "message": "fixture restriction",
                "type": "gateway_error",
            },
        })
        return True

    def do_GET(self):
        parsed = urlparse(self.path)
        query = self._query()
        if parsed.path == "/__control/health":
            self._send_json(200, {"status": "ok"})
            return
        if parsed.path == "/__control/counts":
            with STATE_LOCK:
                self._send_json(200, {"request_count": dict(REQUEST_COUNT)})
            return
        if parsed.path == "/__control/cancelled":
            with STATE_LOCK:
                self._send_json(200, {"cancelled": list(CANCELLED)})
            return
        if parsed.path == "/__control/last":
            with STATE_LOCK:
                self._send_json(200, dict(LAST_REQUEST))
            return
        if parsed.path == "/v1/models":
            if self._maybe_delay_or_disconnect(query):
                return
            status = int(query.get("status", ["200"])[0])
            if status != 200:
                self._send_json(status, {"error": "fixture injected status"})
                return
            snapshot = self._snapshot_request("models")
            self._send_json(200, {
                "object": "list",
                "data": [{"id": "fixture-model", "object": "model"}],
                "x_request_id": snapshot["x_request_id"],
            })
            return
        self._send_json(404, {"error": "unknown fixture path"})

    def do_POST(self):
        parsed = urlparse(self.path)
        query = self._query()
        if parsed.path == "/__control/reset":
            length = int(self.headers.get("Content-Length", 0) or 0)
            if length:
                self.rfile.read(length)
            with STATE_LOCK:
                REQUEST_COUNT.clear()
                CANCELLED.clear()
                LAST_REQUEST.clear()
            self._send_json(200, {"status": "reset"})
            return
        if parsed.path != "/v1/chat/completions":
            self._send_json(404, {"error": "unknown fixture path"})
            return
        length = int(self.headers.get("Content-Length", 0) or 0)
        raw = self.rfile.read(length) if length else b"{}"
        try:
            payload = json.loads(raw.decode("utf-8") or "{}")
        except ValueError:
            payload = {}
        if self._maybe_reject_test_key():
            return
        if self._maybe_delay_or_disconnect(query):
            return
        status = int(query.get("status", ["200"])[0])
        if status != 200:
            self._send_json(status, {"error": "fixture injected status"})
            return
        snapshot = self._snapshot_request("chat/completions")
        if payload.get("stream"):
            self._serve_sse(query, snapshot)
        else:
            self._send_json(200, {
                "id": "chatcmpl-fixture",
                "object": "chat.completion",
                "model": payload.get("model", "fixture-model"),
                "choices": [{"index": 0, "message": {
                    "role": "assistant", "content": "fixture reply"}}],
                "x_request_id": snapshot["x_request_id"],
            })

    def _serve_sse(self, query, snapshot):
        chunks = int(query.get("chunks", ["3"])[0])
        gap = float(query.get("chunk_delay", ["0.1"])[0])
        disconnect = query.get("disconnect", ["0"])[0]
        # 流式响应无 Content-Length，发送完成后必须关闭连接，
        # 否则 HTTP/1.1 keep-alive 会让客户端一直等待。
        self.close_connection = True
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-store")
        self.send_header("ETag", '"fixture-etag"')
        self.send_header("X-Cache", "HIT from fixture")
        self.send_header("X-Request-Id", snapshot["x_request_id"])
        self.end_headers()
        try:
            for i in range(chunks):
                if disconnect == "mid" and i == 1:
                    self.connection.close()
                    return
                line = 'data: {"index":%d,"delta":{"content":"chunk-%d"}}\n\n' % (i, i)
                self.wfile.write(line.encode("utf-8"))
                self.wfile.flush()
                time.sleep(gap)
            self.wfile.write(b"data: [DONE]\n\n")
            self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError):
            with STATE_LOCK:
                CANCELLED.append({
                    "route": "chat/completions",
                    "at": time.time(),
                    "request_count": snapshot["request_count"],
                })


def main():
    port = int(os.environ.get("FIXTURE_PORT", "3000"))
    server = ThreadingHTTPServer(("0.0.0.0", port), RelayHandler)
    print("relay fixture listening", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
