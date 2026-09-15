# AGENTS.md — V.E.L.A. (`com.vela.chat`)

Reference for AI coding agents (and humans) working in this repository. Read this file
first; see [README.md](README.md) for features and setup, [CONTRIBUTING.md](CONTRIBUTING.md)
for conventions, and [docs/API_INTEGRATION.md](docs/API_INTEGRATION.md) for exactly what
the app sends to each kind of server.

## What this is

V.E.L.A. ("Versatile Engine for Local AI") — a single-module, offline-first, Material 3
Android chat client for LM Studio, Ollama and any OpenAI-compatible provider (OpenAI,
OpenRouter, DeepSeek, Moonshot, Zhipu, Gemini, Qwen, custom servers), with Tailscale
discovery and A2A (Agent2Agent) support. Version and versionCode live in
`app/build.gradle.kts` `defaultConfig`. Room DB is at **version 3**.

## ⚠️ Security check — mandatory before every commit, push and release

This repository is public. Anyone working on it — human or agent — must check for
passwords, API keys, tokens and any other compromising data **before** committing,
pushing, or publishing anything (release notes, screenshots, issues). Git history is
permanent: once pushed, a secret must be treated as exposed.

```bash
scripts/check-secrets.sh            # staged + untracked files — before `git commit`
scripts/check-secrets.sh --tree     # every tracked file — before `git push` / a release
scripts/check-secrets.sh --history  # every commit — before making a fork or copy public
```

It exits non-zero on a hit. It checks generic patterns (passwords and tokens as
literals, private keys, common API-key formats, signing config) plus `detect-secrets`
when `uvx` is available. Add your own private patterns (your IP addresses, hostnames,
names) to a git-ignored file and point `SECRET_PATTERNS_FILE` at it, or keep them in
`docs/secret-patterns.local.txt`. A reviewed false positive gets a trailing
`// pragma: allowlist secret` comment; never weaken the patterns instead.

**Never commit or publish:**
- Passwords, API keys, bearer/A2A tokens, OAuth secrets, private keys, keystores
  (`*.jks`), signing properties, `local.properties`, environment files, backups or exports.
- Real network details: IP addresses, hostnames, gateway URLs. Use RFC 5737 addresses
  (`192.0.2.x`) in docs and tests.
- Personal data: names, emails, local paths like `/home/<user>`, device identifiers.
- Screenshots or logs showing real conversations, contacts, files, keys or addresses.
- Private notes belong in git-ignored `docs/*.local.md` / `docs/*.local.txt`.

**Also read your diff** (`git diff --cached`) and your commit messages — no scanner
recognizes every secret. **If a secret was already pushed:** don't just delete the line;
revoke and reissue the credential, because it stays in history and in every clone.

## Build & test

```bash
./gradlew :app:assemblePublicDebug      --no-configuration-cache   # debug APK (com.vela.chat.debug)
./gradlew :app:assemblePublicRelease    --no-configuration-cache   # release APK (com.vela.chat)
./gradlew :app:testPublicDebugUnitTest  --no-configuration-cache   # JVM unit tests (9 suites, 55 tests)
```

- Always build with `--no-configuration-cache` — the configuration cache has served
  stale builds. Build one variant at a time; unflavored tasks such as `assembleDebug`
  build every flavor and can exhaust the default Gradle heap.
- Build the **`public`** flavor. The `personal` flavor is a private edition whose extra
  sources are not part of this repository.
- Toolchain: AGP 8.7.2, Kotlin 2.0.21, compileSdk/targetSdk 35, minSdk 26, **Java 17**.
  Versions in `gradle/libs.versions.toml`. Current androidx/Compose/Hilt releases (and
  `net.zetetic:sqlcipher-android` ≥ 4.18.0) need compileSdk 36–37 / AGP 9.x — that
  migration is deferred, so don't blind-bump dependencies.
- `scripts/provision-build-env.sh` installs JDK 17 and the Android SDK on Linux.
- Signing: credentials come from a git-ignored properties file (see
  `keystore.properties.example`) or `VELA_*` environment variables; without them,
  release builds fall back to debug signing.
- **R8/minify and release lint are deliberately disabled** — do not re-enable without
  on-device validation.
- Debug and release are different apps on a device (`com.vela.chat.debug` suffix).
- On-device testing: `adb install -r`, drive the UI with `adb shell input`, and prefer
  `adb shell uiautomator dump` bounds over guessing coordinates from screenshots.

## Architecture

MVVM + light clean architecture, one `:app` module:
**Compose UI → ViewModel (`StateFlow<UiState>`) → Repository → Room / DataStore / OkHttp network.**
DI is Hilt everywhere (`VelaApplication.kt` `@HiltAndroidApp`, `MainActivity.kt`
`@AndroidEntryPoint`, `@HiltViewModel` per screen). Repositories are `@Singleton` with
constructor injection (no module needed); only Room/OkHttp/Retrofit/DataStore go through
`di/DatabaseModule.kt`, `di/NetworkModule.kt`, and `data/tailscale/TailscaleModule.kt`.

### Key areas

| Area | Where | Notes |
|---|---|---|
| Domain models | `domain/model/Models.kt` | `Role`, `Message` (+ per-message token stats, favorite/pin), `Conversation` (+ `personaId`), `GenerationParams`, `ProviderType` (11 presets incl. `A2A_AGENT`; `supportsTopK` gates non-standard `top_k`), `ApiProfile`, `AgentPersona`, `TokenStats`, `ModelCatalogEntry` |
| Room DB | `data/local/` + `di/DatabaseModule.kt` | `VelaDatabase` **version 3** with real migrations in `data/local/Migrations.kt` (`MIGRATION_1_2`: stats/flags/columns + model_cache; `MIGRATION_2_3`: personas table + `conversations.personaId`). `fallbackToDestructiveMigration()` only as last resort. **Every schema change must ship a migration.** **Encrypted at rest with SQLCipher** (`SupportOpenHelperFactory`); key = random 32-byte hex passphrase from `DatabasePassphrase` (SecureStore). `DatabaseEncryptionMigrator` converts a pre-existing plaintext `vela.db` once (WAL checkpoint → `ATTACH … KEY` + `sqlcipher_export` → row-count check → swap); on failure it leaves the plaintext file untouched and `DatabaseModule` opens it **without** the cipher factory rather than crash. Never open `vela.db` with anything but the Room instance. Two on-device-only gotchas: call `System.loadLibrary("sqlcipher")` before any SQLCipher use (the lib doesn't load itself), and open an `ATTACH` source with `CREATE_IF_NECESSARY` (attached DBs inherit its flags) |
| Repositories | `data/repository/` | `ConversationRepository` (runCatching-guarded; windowed messages, stats, JSON import), `ChatRepository` (`resolveTarget`/`stream`/`complete` — **branches to A2A for `A2A_AGENT` profiles**/`searchComplete`), `ApiProfileRepository`, `PersonaRepository` (agent personalities + built-in seeding), `LibraryRepository` |
| A2A client | `data/a2a/` | `A2aClient.kt` (agent-card discovery, `message/send` blocking + `message/stream` SSE, 600 s read budget, 429 backoff retry; the bearer token only goes to the card URL when it matches the configured gateway's scheme/host/port — `resolveEndpoint`, `A2aEndpointTest`) + `A2aModels.kt` (`A2aJson` lenient parser — handles v1.0 `ROLE_USER`/`TASK_STATE_*` and pre-1.0 dialects; **unit-tested against verbatim Hermes gateway shapes**). Stream errors/empty streams surface as failures, never blank bubbles. **Peer tables** (2.1.0): an `A2A_AGENT` profile can carry named peers (`A2aPeer`, DataStore JSON + per-peer SecureStore tokens) — one profile reaches several gateways with separate identities; peer choice is per conversation, bubbles are attributed via `ChatTarget.peerName` |
| Tailscale | `data/tailscale/` | Session-integration model (rides the official Tailscale app's VPN): `TailnetManager`, `PeerScanner` (OpenAI port-probe **and A2A agent-card probe on port 9900**), `AiServerHeuristics` |
| Agent personas | `data/repository/PersonaRepository.kt`, `ui/personas/` | 8 built-ins seeded (missing built-ins re-added on update; user edits never touched), picker on empty chat + config sheet, default-persona star. Precedence: explicit conversation prompt → persona → global default |
| Settings | `data/settings/SettingsRepository.kt` | DataStore prefs (`vela_settings`). Per-profile custom models, collapsed folders, per-**(conversation, gateway)** A2A contextIds, tailscale peers (`host|port|providerType`) — all deliberately out of Room |
| Networking | `data/remote/` | Retrofit `OpenAiApi.kt` with dynamic `@Url`; SSE in `ChatStreamClient.kt` → `StreamEvent.Token/Reasoning/Completed/Failed`; `RequestFactory.kt`; DTOs in `dto/OpenAiDtos.kt` |
| TLS trust | `data/net/` + `di/NetworkModule.kt` | `TofuTrustManager` on the shared OkHttp client: system-CA-valid certs pass untouched; otherwise the leaf's SHA-256 must match a per-host pin in `TrustedCertStore`, else `UntrustedCertificateException` (found through cause chains via `findUntrustedCert`). The profile editor's Test Connection turns that into a "New certificate" dialog (`ProfileEditViewModel.pendingCertTrust`) → pin → retry. Hostname verification is **not** relaxed — a self-signed cert must still name the host/IP being dialed. Clients built with `okHttpClient.newBuilder()` (A2A, PeerScanner) inherit it; the `@NoLogOkHttpClient` (web search) does **not**, and peers on another host have no trust UI yet |
| Secrets | `data/secure/SecureStore.kt` | `EncryptedSharedPreferences`, `api_key_<profileId>` (+ generic `secret_<key>`: app-lock PIN hash, `db_passphrase`, `cert_pin_<host>`); **fail-closed** with in-memory fallback — note that on the fallback path the DB passphrase is per-process too |
| App lock | `util/AppLockController.kt`, `ui/lock/AppLockScreen.kt` | PIN (PBKDF2-SHA256, 120k iterations, random salt) + framework BiometricPrompt (API 28+) / confirm-credential (26/27); auto-lock via lifecycle; **no androidx.biometric dependency** |
| Serialization | `data/AppJson.kt` | Single shared lenient `Json`. **Always reuse it** — a strict instance breaks server payloads |
| Attachments | `data/attachment/AttachmentProcessor.kt` | Images → downscaled base64; PDF → PDFBox (init moved off the main thread); text inlined |
| UI | `ui/` | Single `NavHost` in `VelaApp.kt`; routes: `chat` (start), `settings`, `appearance`, `parameters`, `profiles`, `profile_edit`, `prompts`, `web_search`, `voice`, `tailscale`, `models`, `search`, `security`, `archive`, `personas`. Markdown is dependency-free (`BlockParser` → `MarkdownText`, GFM tables, mermaid-as-code, LRU parse cache) |
| Nova design system | `ui/theme/nova/`, `ui/components/nova/` | `NovaTheme` (wraps compat `VelaTheme`), `NovaTokens`, `LocalNovaColors`, `GlassSurface`, `NovaTopBar/EmptyState/SkeletonList/SettingsRow/StatusDot/...`, `rememberHaptics()` |
| Theme | `ui/theme/` | 15 color presets + Material You dynamic; AMOLED; `VelaTheme(settings)` delegates to NovaTheme |
| Voice | `util/SpeechToTextController.kt`, `util/TextToSpeechController.kt` | Platform SpeechRecognizer / TTS driven by settings |
| Crash handling | `util/CrashLogger.kt` | Installed as the **first line of `Application.onCreate`**; `vela-crash.log` in external files dir; `MainActivity` shows `CrashReportScreen` |
| Export | `data/export/` | `ChatExporter` (Markdown/JSON) + `PdfExporter` (framework `PdfDocument`). **Profile export** (`ApiProfileRepository.exportProfiles`): "With keys" is wrapped in the same `BackupCrypto` envelope behind a passphrase (shared `ui/components/PassphraseDialog`); "Without keys" stays plain JSON. Never add an export path that writes a secret unencrypted |
| Full backup | `data/backup/BackupCrypto.kt`, `ui/settings/BackupManager.kt` | AES-256-GCM, key derived from a user passphrase (PBKDF2WithHmacSHA256, 300k iterations); backs up **everything** — conversations, messages, API keys, A2A peer tokens, personas, prompts, app-lock PIN hash, settings — with original ids preserved (restore is idempotent). Imports the old pre-2.3 keyless plaintext format too, auto-detected. Export is written through `CreateDocument` in `SecurityScreen` — **never pass large payloads in an Intent extra** (2.3.0–2.4.0 did, and hit Binder's ~1 MB limit silently) |

### Chat streaming model (important)

`ChatViewModel` renders streaming tokens as an **in-memory overlay**
(`ChatUiState.streaming`, throttled to `VelaConstants.STREAM_THROTTLE_MS = 80`). The final
text is persisted once. SSE is consumed via
`callbackFlow { … awaitClose { call.cancel() } }.flowOn(Dispatchers.IO)`. Chat history uses
**windowed loading** (60 messages per window, `loadOlder()` grows it). Token stats come from
`StreamEvent.Completed(usage)` + elapsed time and are persisted per message.

## Conventions & gotchas

- **Versioning policy**: bump `versionCode` (+1) and `versionName` for *every* release.
- **ViewModel property order matters**: properties collected in `init` must be declared
  *above* `init` (`Dispatchers.Main.immediate` collection starts synchronously during
  construction — this caused a real launch NPE). `pendingProfileId`/`activeProfile` follow
  this rule — preserve it.
- **Regex rule (caused a real crash)**: Android's ICU-backed regex engine rejects a bare
  `}` outside a character class; the desktop JVM accepts it. **Always escape `{` and `}` in
  regexes.** See `util/PromptVariables.kt`.
- **Room**: avoid schema changes; when needed, add a real migration to
  `data/local/Migrations.kt`. JSON-encoded columns (`paramsJson`, `attachmentsJson`).
- **Never serialize null-valued request fields** (`explicitNulls = false` + `takeIf` in
  `RequestFactory`) — strict cloud APIs 400 on extras; `top_k` is provider-gated.
- **Cleartext HTTP is enabled on purpose** (LAN/tailnet LM Studio). Don't "fix" it.
  HTTPS to local servers is supported *in addition*, via trust-on-first-use pinning —
  never "fix" a self-signed-cert failure with a trust-all `TrustManager`.
- **No TODO/FIXME/HACK markers** in main sources.
- **A2A identities are tokens**: never reuse one peer's token for another profile — the
  gateway maps token → identity, so a reused token authenticates as the wrong agent. 401 means identity, not network (the client words it that way).
- **A2A dialect drift**: gateways in the wild mix v1.0 and pre-1.0 shapes; keep parsing
  lenient (`A2aJson`) and keep `message/send`/`message/stream` method names (v1.0 accepts
  them as aliases). See `data/a2a/A2aModels.kt` tests for the verbatim shapes.
- Known gaps: placeholder app icon; no instrumented tests.

## Tests

Nine JVM suites in `app/src/test/java/com/vela/chat/` (55 tests, all green in both
flavors):
`MarkdownParserTest` (9, incl. GFM tables + cache), `PromptVariablesTest` (5, incl. the
ICU regex regression), `A2aJsonTest` (7, both A2A dialects + verbatim Hermes envelopes),
`A2aPeerTest` (8, peer-table codec + resolution), `A2aEndpointTest` (5, token only sent to the configured origin), `ChatExporterTest` (2),
`SettingsAndProfilePersistenceTest` (5), `BackupCryptoTest` (8, AES-GCM round trip,
wrong-passphrase/tamper detection), `BackupManagerTest` (6, full export→import round
trip + legacy-format fallback, hand-rolled fakes — follow that style, no mocking
framework). Run: `./gradlew :app:testPublicDebugUnitTest`. No DI/ViewModel/instrumented
coverage (the two Backup suites are the deepest fake-based integration coverage in
the repo, closest to a real DI graph short of Robolectric). **No tests yet** for
`DatabaseEncryptionMigrator` (needs the native SQLCipher lib → instrumented test) or
`TofuTrustManager`/encrypted profile export (JVM-testable — worth adding).

## Roadmap

- JVM tests for `TofuTrustManager` and encrypted profile export/import; an instrumented
  test for the SQLCipher migration.
- Certificate trust for A2A peers on other hosts and for the web-search client.
- Native Anthropic Messages API client (no OpenAI-compatible endpoint exists for Claude).
- Embedded `libtailscale` engine behind `TailnetEngine`; real Mermaid renderer; R8/lint
  re-enable after on-device validation; baseline profiles; Ollama pull cancellation.
- Natural extension seams: `ChatRepository` (completion modes), `RequestFactory` (payloads),
  `ChatStreamClient`/`StreamEvent` (event kinds), `A2aClient`/`A2aJson` (A2A behavior),
  `SettingsRepository.Keys` (settings), `SearchProvider`+`WebSearchClient` (search providers),
  `TailnetEngine` (tailnet transports), `PersonaRepository.defaultPersonas()` (built-ins).
