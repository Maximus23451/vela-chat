# Changelog

All notable changes to V.E.L.A. are documented here. Format loosely follows
[Keep a Changelog](https://keepachangelog.com/).

## [2.5.1] — Security fixes (2026-09-15)

### Security
- Hardened app-lock PIN verification.
- A2A agent tokens are only sent to the gateway you configured.

## [2.5.0] — Encryption at rest and optional local HTTPS (2026-09-15)

### Security
- **Conversation database encrypted at rest** with SQLCipher, using a key generated on
  the device and kept in the Android Keystore-backed secure store. Existing installs are
  migrated automatically on first launch.
- **Optional HTTPS for local servers** (LM Studio, Ollama, A2A, Custom), including
  self-signed certificates: review the certificate's SHA-256 fingerprint once in Test
  Connection; a certificate that changes later is rejected.
- **Profile export with keys is now encrypted** with a passphrase, like the full backup.

### Fixed
- Full backup export for large histories; backups are now saved through a "Save as"
  file picker.

## [2.4.1] — Bugfix (2026-09-15)

### Fixed
- Backup export.

## [2.4.0] — Gemini and Qwen (2026-09-14)

### Added
- **Gemini** and **Qwen** provider presets (OpenAI-compatible endpoints), joining OpenAI,
  OpenRouter, DeepSeek, Moonshot and Zhipu.

## [2.3.3] — Default personas (2026-09-14)

### Changed
- The base app seeds eight built-in personas: Hermes, Coder, Tutor, Brainstormer,
  Concise, Writer, Analyst, Roleplay.

## [2.3.2] — Bugfixes and hardening (2026-09-14)

### Fixed
- Search-provider keys are never written to debug logs.
- App lock recovers if its PIN or device screen lock is no longer available.
- Backup import hardening; right-to-left layout icons.

## [2.3.1] — Bugfixes (2026-09-14)

### Fixed
- Accent color picker visibility; backup import safety.

## [2.3.0] — Full encrypted backup (2026-09-14)

### Added
- One passphrase-encrypted backup (AES-256-GCM) of conversations, messages, profiles
  with their keys, A2A peers and tokens, personas, prompts, app-lock PIN and settings.
  Older backups still import.

## [2.2.1] — Bugfix (2026-09-13)

### Fixed
- Crash when opening the biometric app lock.

## [2.2.0] — Agent "arms" (2026-09-13)

### Added
- Chat models can call remote A2A agents as tools and relay their answers.

## [2.1.0] — A2A peer tables (2026-09-13)

### Added
- One A2A profile can reach several gateways, each with its own identity token.

## [2.0.0] — "Nova" (2026-09-12)

### Added
- Nova design system and redesigned chat: windowed history, token statistics, message
  actions, swipe gestures, Markdown tables.
- A2A (Agent2Agent) client, agent personas, Tailscale discovery, model dashboard, global
  search, archive, prompt library with variables.
- App lock (PIN / biometric / auto-lock), secure screens, PDF export, JSON import.

## [1.4.2] (versionCode 8)

- Model bar: merged catalogue (server `/models` ∪ persisted per-profile custom
  model IDs ∪ currently saved model), **Add model ID…** for ids the catalogue
  omits (e.g. GLM-4.x-Flash), removable custom entries, always-openable menu,
  and `/models` failures no longer hiding saved models. 1.4.1 carried the
  groundwork for this bar.

## [1.4.0]

- **DeepSeek, Moonshot (Kimi) and Zhipu (z.ai)** provider presets, with
  `ProviderType.supportsTopK` gating the non-standard `top_k` field (fixes
  strict cloud APIs rejecting unknown fields).
- Collapsible folders (state persisted in DataStore, not Room) and bulk
  **multi-select** (move/archive/delete many chats; folder deletion detaches
  its chats).
- **Voice input** — mic button in the composer (platform `SpeechRecognizer`,
  RECORD_AUDIO runtime permission).

## [1.3.0]

- Multi-provider **web search**: SearXNG, Brave, Tavily, SerpAPI, Google PSE,
  DuckDuckGo (keys in SecureStore under `search_<provider>`).
- Text-to-speech options: installed-voice language picker, speed and pitch.
- Composer cursor fix.

## [1.2.0]

- **Web search** via the OpenAI tool-calling loop (non-streaming, max 4 tool
  iterations) with a `web_search` tool.
- Animated "Thinking…" indicator for reasoning models.
- Auto-scroll respects manual scrolling + jump-to-bottom button.

## [1.1.1]

- Security hardening, crash-log export, performance fixes.

## [1.1.0]

- Rebrand to V.E.L.A., theme system (presets / dynamic color / AMOLED),
  settings screen, startup crash fix.

## [1.0.0]

- Initial release: OpenAI-compatible chat client (LM Studio & friends), SSE
  streaming, Markdown rendering, API profiles with Keystore-backed keys,
  folders, export, attachments.
