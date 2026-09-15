#!/usr/bin/env python3
"""Mock Hermes-style A2A agent for testing Vela's A2A client.

Speaks the same dialect as the Nous Research Hermes gateway:
  - Agent card at /.well-known/agent-card.json (and legacy agent.json)
  - JSON-RPC 2.0 at POST / with methods SendMessage / message/send,
    message/stream (SSE), tasks/get
  - task result with artifacts carrying text parts
Bind 0.0.0.0 so tailnet peers can reach it. No auth (test only).
"""
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = 9900
AGENT_CARD = {
    "name": "MockHermes",
    "description": "Test A2A agent standing in for a Hermes Agent gateway",
    "url": None,  # filled at request time
    "protocolVersion": "1.0",
    "capabilities": {"streaming": True, "pushNotifications": False},
    "defaultInputModes": ["text"],
    "defaultOutputModes": ["text"],
    "skills": [
        {"id": "chat", "name": "chat", "description": "Talk with the agent"},
        {"id": "echo", "name": "echo", "description": "Echo test"},
    ],
}

CONTEXTS = {}  # contextId -> turn count


def make_message(text, context_id=None):
    msg = {
        "kind": "message",
        "messageId": f"m-{time.time_ns()}",
        "role": "ROLE_AGENT",
        "parts": [{"kind": "text", "text": text}],
    }
    if context_id:
        msg["contextId"] = context_id
    return msg


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        print("[mock-a2a]", self.address_string(), fmt % args)

    def _send_json(self, payload, status=200):
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path.startswith("/.well-known/agent-card.json") or self.path.startswith("/.well-known/agent.json"):
            card = dict(AGENT_CARD)
            card["url"] = f"http://{self.headers.get('Host', f'0.0.0.0:{PORT}')}/"
            self._send_json(card)
        else:
            self._send_json({"error": "not found"}, 404)

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        try:
            req = json.loads(self.rfile.read(length) or b"{}")
        except json.JSONDecodeError:
            self._send_json({"jsonrpc": "2.0", "id": None, "error": {"code": -32700, "message": "parse error"}})
            return

        method = req.get("method", "")
        params = req.get("params", {}) or {}
        message = params.get("message", {}) or {}
        context_id = message.get("contextId")
        user_text = ""
        for part in message.get("parts", []):
            if isinstance(part, dict) and "text" in part:
                user_text += part["text"]
        turns = CONTEXTS.get(context_id, 0) + 1 if context_id else 1

        reply = (
            f"MockHermes here (turn {turns}"
            + (f", continuing context {context_id[:8]}" if context_id else ", new context")
            + f"). You said: {user_text!r}. Everything checks out on my side."
        )

        if method in ("message/stream", "SendStreamingMessage"):
            self._handle_stream(req, reply, context_id)
        elif method in ("message/send", "SendMessage"):
            context_id = context_id or f"ctx-{time.time_ns()}"
            CONTEXTS[context_id] = turns
            self._send_json({
                "jsonrpc": "2.0", "id": req.get("id"),
                "result": {
                    "id": f"task-{time.time_ns()}",
                    "contextId": context_id,
                    "status": {"state": "TASK_STATE_COMPLETED"},
                    "artifacts": [{"artifactId": "a1", "name": "reply",
                                   "parts": [{"kind": "text", "text": reply}]}],
                },
            })
        elif method in ("tasks/get", "GetTask"):
            self._send_json({
                "jsonrpc": "2.0", "id": req.get("id"),
                "result": {"id": params.get("id"), "status": {"state": "TASK_STATE_COMPLETED"},
                           "artifacts": [{"parts": [{"kind": "text", "text": reply}]}]},
            })
        else:
            self._send_json({"jsonrpc": "2.0", "id": req.get("id"),
                             "error": {"code": -32601, "message": f"method not found: {method}"}})

    def _handle_stream(self, req, reply, context_id):
        context_id = context_id or f"ctx-{time.time_ns()}"
        CONTEXTS[context_id] = CONTEXTS.get(context_id, 0) + 1
        task_id = f"task-{time.time_ns()}"
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()

        def event(payload):
            self.wfile.write(f"data: {json.dumps(payload)}\n\n".encode())
            self.wfile.flush()

        event({"jsonrpc": "2.0", "id": req.get("id"), "result": {
            "task": {"id": task_id, "contextId": context_id,
                     "status": {"state": "TASK_STATE_WORKING"}}}})
        # Stream the reply in two chunks as artifact updates.
        for chunk in (reply[: len(reply) // 2], reply[len(reply) // 2:]):
            time.sleep(0.3)
            event({"jsonrpc": "2.0", "id": req.get("id"), "result": {
                "artifactUpdate": {"taskId": task_id, "contextId": context_id,
                                   "artifact": {"artifactId": "a1", "name": "reply",
                                                "parts": [{"kind": "text", "text": chunk}]}},
                "final": False}})
        event({"jsonrpc": "2.0", "id": req.get("id"), "result": {
            "statusUpdate": {"taskId": task_id, "contextId": context_id,
                             "status": {"state": "TASK_STATE_COMPLETED"}},
            "final": True}})


if __name__ == "__main__":
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print(f"[mock-a2a] listening on 0.0.0.0:{PORT}")
    server.serve_forever()
