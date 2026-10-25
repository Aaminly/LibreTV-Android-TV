#!/usr/bin/env python3
"""Local integration fixture, never an alternative production backend.
Usage: python3 tools/auth-fixture.py MEDIA_DIRECTORY REQUEST_LOG
MEDIA_DIRECTORY contains generated sample.mp4, encrypted/fmp4.m3u8 and poster.png.
"""
import json
import mimetypes
import re
import sys
import time
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, quote, urlparse

media = Path(sys.argv[1]).resolve()
log = Path(sys.argv[2])
records = []
state = {"mode": "password", "token": "fixture-session-v1", "delay": 0}
base = "http://10.0.2.2:18892"


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def send(self, value, code=200, content_type="application/json", headers=None):
        data = json.dumps(value, ensure_ascii=False).encode() if isinstance(value, (dict, list)) else value
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(data)))
        for key, val in (headers or {}).items():
            self.send_header(key, val)
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):
        data = json.loads(self.rfile.read(int(self.headers.get("Content-Length", "0"))))
        self.handle_request(data)

    def do_GET(self):
        self.handle_request({})

    def handle_request(self, data):
        path = urlparse(self.path).path
        cookie = self.headers.get("Cookie", "")
        valid = cookie == "ltv_session=" + state["token"]
        record = {"path": path, "port": self.server.server_port, "target": parse_qs(urlparse(self.path).query).get("url", [""])[0], "cookie_valid": valid, "has_cookie": bool(cookie), "range": self.headers.get("Range", "")}
        records.append(record)
        with log.open("a") as f:
            f.write(json.dumps(record) + "\n")
        if path == "/fixture/log":
            return self.send(records)
        if path == "/api/redirect":
            return self.send(b"",302,"application/octet-stream", {"Location": "http://10.0.2.2:18893/api/destination"})
        if path == "/api/destination":
            return self.send((media / "poster.png").read_bytes(),content_type="image/png")
        if path == "/fixture/mode":
            state.update(data)
            return self.send({"ok": True})
        if path == "/api/auth":
            if data.get("password") != "libretv-qa-cookie":
                return self.send({"error": "密码错误"}, 401)
            return self.send({"success": True}, headers={"Set-Cookie": "ltv_session=" + state["token"] + "; Path=/; HttpOnly; Max-Age=3600"})
        if path == "/api/status":
            time.sleep(state["delay"])
            return self.send({"passwordRequired": state["mode"] == "password", "verified": valid or state["mode"] == "open", "defaultSources": [{"key": "qa", "name": "本地契约测试源", "url": base + "/source"}]})
        if state["mode"] == "missing":
            return self.send({"error": "服务器未配置 PASSWORD 环境变量"}, 503)
        if state["mode"] == "password" and not valid:
            return self.send({"error": "未登录"}, 401)
        video = {"vodId": "qa", "name": "测试影片", "pic": base + "/media/poster.png", "sourceKey": "qa", "sourceName": "本地契约测试源", "sourceUrl": base + "/source", "remarks": "30秒生成测试音画"}
        if path == "/api/search":
            return self.send({"list": [video], "failures": []})
        if path == "/api/hot-list":
            return self.send({"items": []})
        if path == "/api/detail":
            return self.send({"episodes": ["加密HLS$" + base + "/media/encrypted.m3u8", "分段MP4$" + base + "/media/fmp4.m3u8", "MP4$" + base + "/media/sample.mp4"], "videoInfo": {"title": "测试影片", "desc": "生成音画用于实际解码验证，不代表第三方内容源"}})
        if path == "/api/proxy" and state.get("resource_unauthorized"):
            return self.send({"error": "上游资源要求授权"},401)
        if path == "/api/proxy":
            target = parse_qs(urlparse(self.path).query).get("url", [""])[0]
            name = urlparse(target).path.removeprefix("/media/")
            if not re.fullmatch(r"[\w.-]+", name) or not (media / name).is_file():
                return self.send({"error": "未知测试媒体"}, 404)
            content = (media / name).read_bytes()
            if name.endswith(".m3u8"):
                text = content.decode()
                def proxy(filename):
                    return base + "/api/proxy?url=" + quote(base + "/media/" + filename, safe="")
                text = re.sub(r'URI="([^\"]+)"', lambda m: 'URI="' + proxy(m[1]) + '"', text)
                text = "\n".join(proxy(line) if line and not line.startswith("#") else line for line in text.splitlines())
                return self.send(text.encode(), content_type="application/vnd.apple.mpegurl")
            content_type = mimetypes.guess_type(name)[0] or "application/octet-stream"
            request_range = self.headers.get("Range", "")
            if request_range:
                match = re.fullmatch(r"bytes=(\d+)-(\d*)", request_range)
                if not match:
                    return self.send(b"", 416, "application/octet-stream")
                start = int(match[1]); end = min(int(match[2]) if match[2] else len(content)-1, len(content)-1)
                return self.send(content[start:end+1], 206, content_type, {"Content-Range": f"bytes {start}-{end}/{len(content)}", "Accept-Ranges": "bytes"})
            return self.send(content, content_type=content_type)
        self.send({"error": "未知路由"}, 404)


threading.Thread(target=ThreadingHTTPServer(("127.0.0.1", 18893), Handler).serve_forever, daemon=True).start()
ThreadingHTTPServer(("127.0.0.1", 18892), Handler).serve_forever()
