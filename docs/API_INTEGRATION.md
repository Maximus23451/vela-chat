# API Integration Guide

Vela targets the **OpenAI-compatible** REST surface that LM Studio, Ollama,
OpenAI, OpenRouter, llama.cpp, and vLLM expose, plus a native **Agent2Agent (A2A)**
client for agent gateways. This document describes exactly what Vela sends and
expects. *(Updated for 2.5.0, 2026-09-15.)*

## Provider presets

| Preset | Default base URL | Key | `top_k` |
|---|---|---|---|
| LM Studio | `http://localhost:1234/v1` | optional | sent |
| Ollama | `http://localhost:11434/v1` | optional | sent |
| A2A Agent | `http://localhost:9900` | peer/profile token | n/a (A2A) |
| OpenAI | `https://api.openai.com/v1` | required | omitted |
| OpenRouter | `https://openrouter.ai/api/v1` | required | omitted |
| DeepSeek | `https://api.deepseek.com/v1` | required | omitted |
| Moonshot (Kimi) | `https://api.moonshot.ai/v1` | required | omitted |
| Zhipu (z.ai) | `https://api.z.ai/api/paas/v4` | required | omitted |
| Gemini | `https://generativelanguage.googleapis.com/v1beta/openai/` | required | omitted |
| Qwen | `https://dashscope-intl.aliyuncs.com/compatible-mode/v1` | required | omitted |
| Custom | `http://localhost:1234/v1` | optional | sent |

Source of truth: `ProviderType` in `domain/model/Models.kt` (`supportsTopK` decides the
last column). On a phone, `localhost` is the phone itself — use the server's LAN or
tailnet address.

## Endpoints used

| Purpose            | Method & path              | When |
|--------------------|----------------------------|------|
| List models        | `GET {baseUrl}/models`     | Connection test, model picker |
| Chat completion    | `POST {baseUrl}/chat/completions` | Every message |

`baseUrl` is taken from the active profile and normalised to end in a single
slash before the path is appended (so configure it **with** the `/v1` suffix, e.g.
`http://192.168.1.20:1234/v1`).

## Authentication
If a profile has an API key, Vela sends `Authorization: Bearer <key>`. LM Studio
and Ollama don't require a key, so the header is omitted when empty.

## Models response
```json
{ "object": "list", "data": [ { "id": "qwen2.5-7b-instruct", "owned_by": "lmstudio" } ] }
```
Unknown fields are ignored (lenient parsing).

## Chat completion request
```jsonc
{
  "model": "qwen2.5-7b-instruct",
  "messages": [
    { "role": "system", "content": "You are a helpful assistant." },
    { "role": "user", "content": "Summarize this PDF." }
  ],
  "temperature": 0.7,
  "top_p": 0.95,
  "top_k": 40,            // sent only to LM Studio / Ollama / Custom — strict cloud APIs reject it
  "max_tokens": 2048,     // omitted entirely when "Limit max tokens" is off
  "presence_penalty": 0,  // omitted when 0
  "frequency_penalty": 0, // omitted when 0
  "stream": true,
  "stream_options": { "include_usage": true }
}
```
Fields that are `null` are never serialized, so strict servers won't reject
extensions they don't understand.

### Multimodal (vision) messages
When a user message has image attachments, its `content` becomes an array of
content parts:
```json
{
  "role": "user",
  "content": [
    { "type": "text", "text": "What's in this picture?" },
    { "type": "image_url", "image_url": { "url": "data:image/jpeg;base64,/9j/4AAQ..." } }
  ]
}
```
Images are downscaled (max 1280px) and re-encoded before being base64-embedded.

### Documents (PDF / text)
PDF and text attachments are **not** sent as binary. Their extracted text is
inlined into the user message as:
```
[Attached document: report.pdf]
"""
…extracted text…
"""

<the user's question>
```
This keeps Vela compatible with text-only models and is the foundation for
RAG-style workflows.

## Streaming (SSE)
With `stream: true`, the server returns `text/event-stream`. Vela reads it line
by line:
```
data: {"choices":[{"delta":{"content":"Hello"}}]}
data: {"choices":[{"delta":{"content":" world"}}]}
data: {"choices":[{"delta":{},"finish_reason":"stop"}],"usage":{...}}
data: [DONE]
```
- `choices[0].delta.content` chunks are appended to the visible answer.
- `choices[0].delta.reasoning_content` (reasoning models) populates the
  collapsible **Reasoning** section.
- A chunk containing `usage` updates the token counter.
- `data: [DONE]` (or the stream closing) ends generation.

Cancelling (Stop) cancels the underlying OkHttp call and persists whatever was
streamed so far.

## Non-streaming
If streaming is disabled in Settings, Vela issues the same request with
`stream: false` and reads `choices[0].message.content`.

## HTTPS and certificates

- Any profile can use `https://`. Certificates that validate against the system CA
  store (every cloud preset, or a local server with a real certificate) work with no
  prompt.
- A **self-signed** certificate fails system validation, so the first Test Connection
  shows a "New certificate" dialog with the leaf certificate's SHA-256 fingerprint
  (same format as `openssl x509 -noout -fingerprint -sha256`). Trusting it pins that
  fingerprint to the host; later connections succeed silently.
- If the certificate for a pinned host **changes**, the handshake is rejected before
  any request (or key) is sent, and the dialog appears again.
- Hostname verification is not relaxed: the certificate's SAN must include the host or
  IP in the base URL.
- A chat request that hits an untrusted certificate fails with "New certificate for
  <host> — open this profile and tap Test Connection to review and trust it."
- **Known gaps (2.5.0):** the trust dialog only appears from a profile's Test
  Connection, and pins are per host — so an A2A **peer** on a different host than its
  profile's base URL can't be trusted from the UI yet. **Web search** uses a separate
  no-logging HTTP client without the pinning trust manager, so a self-signed `https://`
  SearXNG fails with an SSL error. A2A calls and the Tailscale scanner do use the pinned
  client.

## Agent2Agent (A2A) profiles

For the `A2A Agent` preset Vela speaks A2A instead of the OpenAI dialect
(`data/a2a/`; shapes verified against live Hermes gateways, see `A2aJsonTest`):

- **Discovery:** `GET {baseUrl}/.well-known/agent-card.json`, falling back to the legacy
  `agent.json`. Cards advertising loopback URLs are ignored.
- **Calls:** JSON-RPC 2.0 `POST {baseUrl}/` with `message/send` (blocking, then polled)
  or `message/stream` (SSE). v1.0 gateways accept these as aliases of
  `SendMessage`/`SendStreamingMessage`; both v1.0 and pre-1.0 response dialects parse.
- **Auth:** `Authorization: Bearer <token>`. The token *is* the identity on the gateway —
  never reuse one across peers or profiles. No token → `401 {"error":{"code":-32050}}`,
  which Vela reports as an identity failure, not a network one.
- **Replies** arrive in `result.task.status.message.parts[].text` and/or
  `result.task.artifacts[].parts[].text`; streaming gateways wrap them in
  `statusUpdate`/`artifactUpdate` envelopes.
- **Context:** a `contextId` is kept per (conversation, gateway).
- **Peers:** a profile can list several gateways (peer table), each with its own token;
  any chat profile can also expose peers to the model as tools ("arms").
- Read budget 600 s; `429` responses back off and retry.

## Error handling
HTTP errors are mapped to friendly messages, e.g.:
- `401/403` → "Authentication failed — check your API key."
- `404` → "Endpoint not found — verify the base URL and that a model is loaded."
- Connection failures → host/timeout guidance.
- Untrusted/changed certificate → the "New certificate" flow above.
The server's `error.message` is appended when present.

## Timeouts
- Connect: 20s, Write: 30s, **Read: none** (token streams may be long-lived).
- A2A: 600 s read budget per call.

## Tips for LM Studio
- Start the server from the **Developer** tab and **load a model**.
- Toggle **Serve on local network** to reach it from a physical device.
- The model id Vela sends must match a loaded model; use **Test connection** to
  fetch the exact ids.
