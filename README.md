# V.E.L.A. — Versatile Engine for Local AI

**V.E.L.A.** is a modern, Material 3 Android chat client for **LM Studio** and any
**OpenAI-compatible** server (Ollama, OpenAI, OpenRouter, llama.cpp, vLLM, DeepSeek,
Moonshot, Zhipu, Gemini, Qwen, …) — with **Tailscale mesh discovery** and **A2A (Agent2Agent)
support** for talking to remote autonomous agents over your own tailnet. It combines
the usability of OpenWebUI / ChatGPT with secure, encrypted key management — all
on-device, offline-first, and tuned for low-end phones.

> Built with Kotlin, Jetpack Compose, Hilt, Room, Retrofit/OkHttp, and Coroutines/Flow.
>
> **Current version: 2.5.1** — see [CHANGELOG.md](CHANGELOG.md) for the release history.
> Download the APK from [Releases](https://github.com/Maximus23451/vela-chat/releases/latest).

---

## ✨ Features

### Chat & OpenAI compatibility
- Talks to the OpenAI-compatible endpoints: `GET /models`, `POST /chat/completions`.
- **Server-Sent Events token streaming** with a Stop button and live typing indicator.
- Configurable **base URL**, **model**, **API key**, and per-provider presets (LM
  Studio, Ollama, OpenAI, OpenRouter, DeepSeek, Moonshot, Zhipu, Gemini, Qwen, Custom).
- Works on **local networks** (your PC's LAN IP), **tailnets**, and **remote servers**
  (HTTPS).
- Provider-aware request building (e.g. `top_k` is only sent to servers that accept
  it, since it's a llama.cpp/LM Studio sampler extension outside the OpenAI schema).
- **Vision** messages (image attachments) via the multimodal content-parts schema;
  PDFs and text files are extracted/inlined as context.

### Agent2Agent (A2A) — talk to remote autonomous agents
- **A2A client**: agent-card discovery (`/.well-known/agent-card.json`, legacy
  `agent.json` fallback), JSON-RPC `message/send` (blocking + poll) and
  `message/stream` (SSE), lenient parsing across A2A dialect versions.
- **Peer tables**: one A2A profile can reach several agent gateways, each under its
  own bearer-token identity — switch which peer you're talking to per conversation,
  reply bubbles show who actually answered.
- **Agent "arms"**: a regular (non-A2A) chat profile can carry enabled peers too —
  instead of the phone wearing an agent's badge, you talk to a local/cloud model and
  *it* messages the remote agent as a tool call, under the peer's own identity, and
  relays the answer back in-persona.
- **Tailscale-aware discovery**: the tailnet scanner probes port 9900 for A2A agent
  cards alongside the usual OpenAI-compatible ports.

### Agent personas
- 8 built-in personalities (Hermes default, Coder, Tutor, Brainstormer, Concise,
  Writer, Analyst, Roleplay) — each a system prompt plus an optional temperature
  nudge. Pick one on the empty-chat screen or per conversation; one is always the
  default. Add your own in Settings → Agent personalities.

### Modern chat interface
- Clean **Material 3** design with light/dark/system themes, **dynamic color**
  (Android 12+), **15 theme presets** (Nord, Dracula, Catppuccin, Gruvbox, Tokyo
  Night, Rosé Pine, One Dark, Monokai, ChatGPT, Claude, OpenWebUI, Midnight Blue,
  Solarized, High Contrast, plus Material You), and an **AMOLED black** mode. Accent
  color customization applies under Material You; named presets keep their own
  faithful, curated colors.
- **Markdown rendering** with headings, lists, GFM tables, blockquotes, links —
  dependency-free parser with an LRU cache.
- **Syntax-highlighted code blocks** with a one-tap **Copy** button.
- **Message editing & regeneration**, copy, delete, translate, summarize, and
  read-aloud (TTS).
- **Conversation search**, **chat history drawer**, **folders** (with colors &
  icons), **pinning**, rename, archive (with a dedicated archive screen), delete —
  destructive actions always offer undo.
- Streaming responses, typing indicators, a reasoning/thinking section for reasoning
  models.
- **Windowed long chats** — smooth scrolling via 60-message windows with a "load
  older" step.
- **Web search** (6 providers: SearXNG, Brave, Tavily, SerpAPI, Google Programmable
  Search, DuckDuckGo) wired through the same tool-calling loop agent arms use.
- **Global search**, **model dashboard** (per-profile model cards: quantization,
  parameter count, family, size, latency), **token stats** (live + final tok/s).

### Security & backup
- API keys and A2A peer tokens encrypted at rest with **Android Keystore +
  EncryptedSharedPreferences** (AES-256, hardware-backed where available). Never
  stored in the Room database in plaintext.
- **Conversation database encrypted at rest** with SQLCipher, keyed by a random
  passphrase generated on-device and held in the same Keystore-backed store.
  Existing installs are migrated once on first launch, with a row-count check before
  the original is replaced.
- **Optional HTTPS for local servers**, self-signed certificates included: Test
  Connection shows the certificate's SHA-256 fingerprint, you trust it once, and a
  certificate that changes afterward is rejected (trust-on-first-use pinning).
- **App lock**: PIN (salted PBKDF2-SHA256 hash) or biometric unlock via the framework
  BiometricPrompt, auto-lock timer, optional `FLAG_SECURE` screenshot blocker. Falls
  back safely — the lock mode self-heals if it can no longer be satisfied (e.g. the
  device's screen lock was removed after enabling biometric mode) rather than
  trapping you outside your own data.
- **Full encrypted backup**: one passphrase-protected export of *everything* —
  conversations, messages, API profiles with their keys, A2A peer tables with their
  tokens, personas, prompts, the app-lock PIN hash, and settings. AES-256-GCM, key
  derived via PBKDF2 (300k iterations) from a passphrase that's never stored — lose
  it, lose the backup. Restoring preserves every original id, so it's idempotent and
  safe to re-run. Also imports the older pre-2.3 keyless backup format. Saved through
  a "Save as" picker to a file you choose. **Profile export with keys** uses the same
  passphrase encryption.

### Attachments
- **Image** attachments → downscaled base64 data URLs for vision models.
- **PDF** attachments → text extracted on-device (PDFBox, off the main thread) and
  inlined.
- **Text/code** files → inlined as context. Multi-select file picker.

### Voice
- **Voice input** (speech-to-text) fills the composer via the platform
  SpeechRecognizer.
- **Text-to-speech read-aloud** with language, speed, and pitch options.

### Export & import
- **Export** any conversation to **Markdown**, **JSON**, or **PDF**; **import** from
  JSON.
- **Prompts**: categories, favorites, `{{variable}}` fill-in dialogs, JSON
  import/export.

---

## 🏗 Architecture

Single-module app using **MVVM + a thin clean-architecture split**.

```
com.vela.chat
├── data
│   ├── local        Room database (v3), entities, DAOs, mappers, Migrations
│   ├── remote       Retrofit API, DTOs, SSE stream client, request factory
│   ├── a2a          A2A client (agent-card discovery, message/send + stream, peer tokens)
│   ├── tailscale    Tailnet detection, MagicDNS, peer + A2A-agent scanning
│   ├── backup       BackupCrypto — AES-256-GCM passphrase encryption for full backups
│   ├── repository   Conversation / Chat / ApiProfile / Persona / Library repositories
│   ├── secure       Keystore-backed SecureStore for API keys & A2A peer tokens
│   ├── settings     DataStore-backed AppSettings
│   ├── attachment   Image/PDF/text ingestion
│   └── export       Markdown / JSON / PDF chat export
├── di               Hilt modules (Database, Network)
├── domain.model     Plain Kotlin domain models
├── util             VelaConstants, AppLockController, PromptVariables, CrashLogger
└── ui
    ├── theme/nova       Nova design tokens, colors, 15 palettes, theme
    ├── components/nova  GlassSurface, top bars, rows, dots, skeletons
    ├── components       Reusable UI + the self-contained Markdown renderer
    ├── chat             Chat screen, ViewModel, message list, input bar, top bar
    ├── personas         Agent persona picker + editor
    ├── screens/tailscale · models · search
    ├── lock             App lock screen
    ├── drawer           Conversation drawer + archive screen
    ├── settings         Sectioned settings hub, Security (app lock + full backup)
    ├── appearance / parameters / profiles / prompts / voice / websearch
    └── navigation       Compose Navigation graph
```

**Data flow:** Compose UI → ViewModel (`StateFlow`) → Repository → Room / DataStore /
Network (plus the `data/tailscale` and `data/a2a` sidecars). Room `Flow`s drive the UI
reactively; streaming tokens are held in an in-memory overlay and persisted once on
completion to keep DB writes cheap.

See [docs/API_INTEGRATION.md](docs/API_INTEGRATION.md) for exactly what the app sends
to each kind of server. **[AGENTS.md](AGENTS.md)** is the most useful file for a new
contributor (human or AI) — read it first.

---

## 🚀 Getting started

### Prerequisites
- Android Studio Ladybug (2024.2) or newer — or just a JDK + Android SDK for the CLI
- **JDK 17** (the build pins this exactly; a newer system JDK will not work)
- Android SDK Platform 35, Build-Tools 34.0.0
- An LM Studio server (or Ollama, or any OpenAI-compatible server) running with a
  model loaded — optional at build time, needed to actually chat

### Clone
```bash
git clone https://github.com/Maximus23451/vela-chat.git
cd vela-chat
```

### Linux/macOS quick setup
```bash
scripts/provision-build-env.sh
```
This installs JDK 17 (Temurin) to `~/tools/jdk17` and the Android SDK
(platform 35, build-tools 34.0.0, platform-tools) to `~/tools/android-sdk` — no
`sudo` needed. Then pin the JDK for Gradle so it doesn't fall back to your system
Java:
```bash
mkdir -p ~/.gradle
echo "org.gradle.java.home=$HOME/tools/jdk17" >> ~/.gradle/gradle.properties
```
And point the Android SDK at `local.properties` (create it if the provisioning
script didn't):
```bash
echo "sdk.dir=$HOME/tools/android-sdk" > local.properties
```

### Configure LM Studio (or your server of choice)
1. Open **LM Studio → Developer / Local Server** tab.
2. Load a model and **Start Server** (default `http://localhost:1234`).
3. Enable **"Serve on local network"** so your phone can reach it.
4. Note your computer's LAN IP, e.g. `192.168.1.20`.

### Build & run
```bash
# from the project root — always pass --no-configuration-cache, see note below
./gradlew :app:assemblePublicDebug --no-configuration-cache   # build the debug APK
./gradlew :app:installDebug --no-configuration-cache     # install on a connected device/emulator
./gradlew :app:testPublicDebugUnitTest --no-configuration-cache # run the JVM unit test suite (9 suites)
```
On Windows use `gradlew.bat`. Or just open the folder in Android Studio and Run —
Android Studio manages the configuration cache setting itself.

> **Always pass `--no-configuration-cache`** on the command line. The project enables
> Gradle's configuration cache in `gradle.properties`, but it has served stale builds
> in this repo's history — not worth the risk until re-verified.

### Release builds & signing
Signing credentials are **never** hard-coded and **never committed**. They're read
from a git-ignored `keystore.properties` at the project root (or `VELA_*`
environment variables):

```properties
storeFile=vela-release.jks
storePassword=********
keyAlias=vela-key
keyPassword=********
```
Copy [`keystore.properties.example`](keystore.properties.example) to
`keystore.properties` and fill it in — generate a keystore with:
```bash
keytool -genkeypair -v -keystore vela-release.jks -alias vela-key \
  -keyalg RSA -keysize 2048 -validity 10000
```
Then:
```bash
./gradlew :app:assemblePublicRelease --no-configuration-cache   # produces a signed app-public-release.apk
```
If no keystore is configured, the `release` variant **falls back to debug signing**
so the build still succeeds for local testing (not suitable for store distribution).

Debug and release are **two separate apps on a device** (`com.vela.chat.debug` vs.
`com.vela.chat`) — installing the debug variant never touches your release app's
data, and vice versa.

### First launch
1. V.E.L.A. auto-creates an **"LM Studio (local)"** profile pointing at
   `http://localhost:1234/v1`.
2. Open **Settings → API Profiles**, edit it, and set the **Base URL** to your
   computer's IP (e.g. `http://192.168.1.20:1234/v1`).
3. Tap **Test connection** to load the model list, pick a model, **Save**.
4. Start chatting.

> **Reaching your server:**
> - **Physical device:** use your computer's LAN IP, e.g. `http://192.168.1.20:1234/v1`
>   (LM Studio must have *Serve on local network* enabled, same Wi-Fi).
> - **Android emulator:** the host machine is `10.0.2.2`, so use
>   `http://10.0.2.2:1234/v1`.
> - **Tailnet:** install the official Tailscale app, connect, then use
>   Settings → Tailscale → *Scan for AI servers* .
>   The scan also probes for A2A agent gateways on port 9900.
> - **Remote:** prefer HTTPS. Self-signed certificates work too — see [Security](#-security).

### Setting up an A2A (Agent2Agent) connection
If you're running a [Hermes-style agent gateway](docs/API_INTEGRATION.md) (or any
A2A-compliant server) on your tailnet:
1. **Settings → API Profiles → Add profile**, set provider type to **A2A Agent**,
   point the base URL at the gateway (default port **9900**).
2. If the gateway needs a bearer token, add it as the profile's API key — or, for
   multi-gateway setups, add named **peers** in the profile editor (each peer gets
   its own token; a token *is* the identity on the gateway, so never reuse one
   peer's token for another).
3. To let a *regular* chat profile message an agent as a tool instead of wearing its
   badge directly, add the same peer under that profile's **"Agent contacts (A2A
   tools)"** section in the profile editor.

### On-device debugging workflow
```bash
ADB=~/tools/android-sdk/platform-tools/adb
$ADB install -r app/build/outputs/apk/public/release/app-public-release.apk   # release == the "real" app
$ADB shell am start -n com.vela.chat/.MainActivity
$ADB exec-out screencap -p > shot.png                            # inspect before tapping
```
Crash reports persist at `/sdcard/Android/data/com.vela.chat/files/vela-crash.log`
and are shown on next launch until dismissed (dismissing archives, doesn't delete —
still exportable from Settings).

---

## 🔌 API integration

Vela speaks the OpenAI REST dialect. Example streaming request it sends:

```http
POST {baseUrl}/chat/completions
Authorization: Bearer <key, if set>
Content-Type: application/json

{
  "model": "your-model",
  "messages": [
    { "role": "system", "content": "You are a helpful assistant." },
    { "role": "user", "content": "Hello!" }
  ],
  "temperature": 0.7,
  "top_p": 0.95,
  "top_k": 40,
  "max_tokens": 2048,
  "stream": true,
  "stream_options": { "include_usage": true }
}
```

Vision messages use the multimodal content-parts schema; documents are inlined as
text. A2A wire format (agent-card discovery, JSON-RPC envelopes) is documented
separately in [docs/API_INTEGRATION.md](docs/API_INTEGRATION.md).

---

## 🔐 Security

- API keys and A2A peer tokens are stored via `EncryptedSharedPreferences` with an
  AES-256 master key in the Android Keystore (hardware-backed when available) —
  fail-closed: if the Keystore is unusable, secrets stay in-memory for the session
  rather than ever being written unencrypted to disk.
- The chat database is **SQLCipher-encrypted at rest** (random passphrase in the
  Keystore-backed store). Pre-2.5 installs are migrated once; a failed migration
  leaves the original file untouched and retries on the next launch.
- Secrets and the chat database are excluded from Android's cloud auto-backup and
  device-transfer (`backup_rules.xml`, `data_extraction_rules.xml`).
- **App lock**: PIN stored as a salted PBKDF2-SHA256 hash, or biometric unlock via
  the framework BiometricPrompt; optional `FLAG_SECURE` screen protection. The lock
  mode self-heals to unlocked if it can no longer be satisfied, rather than trapping
  you outside your own data.
- **Full backup is passphrase-encrypted** (AES-256-GCM, PBKDF2-derived key) precisely
  *because* it now includes every secret in the app — treat the exported file with
  the same care as the keys themselves. Profile export "with keys" is encrypted the
  same way.
- Web search providers that only accept their API key as a URL query parameter
  (SerpAPI, Google Programmable Search) go through a dedicated HTTP client with no
  request logging, so a key never ends up in Logcat even in debug builds.
- `usesCleartextTraffic` is enabled on purpose, to support LAN/tailnet HTTP servers
  (LM Studio, local gateways). HTTPS works for local servers too: certificates that
  pass normal CA validation are trusted as usual, and a self-signed one is trusted only
  after you approve its SHA-256 fingerprint — pinned per host, so a changed
  certificate is rejected. Use HTTPS for anything leaving your own network.
- Messages are **not end-to-end encrypted**: the model has to read your prompt to
  answer it.

---

## 🧪 Tech stack

| Concern        | Choice |
|----------------|--------|
| Language       | Kotlin 2.0 |
| UI             | Jetpack Compose, Material 3 + Nova design system |
| DI             | Hilt |
| Persistence    | Room (v3, real migrations), DataStore |
| Networking     | Retrofit + OkHttp (SSE) |
| Serialization  | kotlinx.serialization |
| Async          | Coroutines + Flow |
| Secure storage | AndroidX Security (Keystore) |
| Documents      | PDFBox-Android (import), framework PdfDocument (export), Coil |
| Tests          | JUnit4, hand-rolled fakes (no mocking framework) — 9 suites, 55 tests |

---

## 📸 Screenshots
Screenshots of the chat screen, drawer, and API profile editor are on the
[landing page](https://maximus23451.github.io/vela-web/).

---

## 🗺 Roadmap
- Embedded Tailscale engine (`libtailscale`)
- Real Mermaid diagram rendering (currently styled code-block chrome, not a real
  renderer)
- R8/lint re-enable — currently disabled in the release build pending a suspected
  release-only crash investigation (see `app/build.gradle.kts`)
- AGP 9.x / OkHttp 5.x migration — the rest of the dependency graph has moved to
  require AGP 9, deliberately deferred as its own dedicated pass
- Certificate trust for A2A peers on other hosts and for self-hosted web search
  (SearXNG) over self-signed HTTPS — the 2.5.0 trust dialog covers profile base URLs only
- Native Anthropic (Messages API) client — Claude has no first-party
  OpenAI-compatible endpoint, so it needs its own client rather than a preset
- Baseline profiles, tablet two-pane layout, background sync

---

## 🤝 Contributing
Start with [CONTRIBUTING.md](CONTRIBUTING.md) for environment setup and conventions.
AI coding agents should read [AGENTS.md](AGENTS.md) first — it's the authoritative
reference for repo layout, conventions, the mandatory secret check, and gotchas.

---

## 📄 License
Released under the MIT License. See [LICENSE](LICENSE).

Not affiliated with LM Studio, OpenAI, Anthropic, Google, Alibaba, or OpenWebUI.
