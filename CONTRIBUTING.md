# Contributing to V.E.L.A.

Thanks for helping build V.E.L.A. This guide covers environment setup, the build
commands that work, and the conventions every change must follow. AI coding agents:
read [AGENTS.md](AGENTS.md) **first**.

## Environment setup

Prerequisites: JDK 17, Android SDK (platform 35).

On Linux, `scripts/provision-build-env.sh` installs a local Temurin JDK 17 and the
Android SDK into `~/tools`:

```bash
./scripts/provision-build-env.sh
```

Then pin the JDK for Gradle in `~/.gradle/gradle.properties`:

```properties
org.gradle.java.home=/home/<you>/tools/jdk17
```

`local.properties` in the repo root must contain your `sdk.dir` (Android Studio creates
it automatically). With Android Studio, just open the project.

## Building

```bash
./gradlew :app:assemblePublicDebug      --no-configuration-cache   # debug APK
./gradlew :app:assemblePublicRelease    --no-configuration-cache   # release APK
./gradlew :app:testPublicDebugUnitTest  --no-configuration-cache   # JVM unit tests
```

- **Always pass `--no-configuration-cache`** — the configuration cache has served stale
  builds. If an edit "doesn't take", run `./gradlew --stop` and rebuild.
- Build the `public` flavor. The `personal` flavor is a private edition; its extra
  sources aren't part of this repository.
- R8/minify and release lint are deliberately disabled (see `app/build.gradle.kts`) — do
  not re-enable without on-device validation.
- Debug and release are different apps on a device (`com.vela.chat.debug` vs
  `com.vela.chat`).
- Release signing reads a git-ignored `keystore.properties` (copy
  [keystore.properties.example](keystore.properties.example)); without it, release builds
  fall back to debug signing.

## Conventions

- **Security check before every commit.** Run `scripts/check-secrets.sh` (and `--tree`
  before pushing), and read your own diff. Never commit passwords, API keys, tokens,
  keystores, real IP addresses or personal data — see [AGENTS.md](AGENTS.md).
- **Never write a secret unencrypted** — not in an export, a log, or a doc.
- **No new Gradle dependencies without discussion.** The dependency surface is
  deliberately small; open an issue first.
- **Room changes need real migrations** in `data/local/Migrations.kt`. The database is
  SQLCipher-encrypted — access it only through Room.
- **Versioning:** bump `versionCode` (+1) and `versionName` for every release.
- **ViewModel property order:** properties read in `init` must be declared above `init`.
- **Never serialize null-valued request fields** — strict cloud APIs reject extra fields
  (why `top_k` is provider-gated).
- **Cleartext HTTP is enabled on purpose** for LAN servers. HTTPS to local servers uses
  trust-on-first-use pinning (`data/net/`); never "fix" a certificate error with a
  trust-all TrustManager.
- **Reuse the shared `AppJson`** instance for JSON.
- **KDoc on public APIs**, and no unresolved-work placeholder comments in main sources.
- Update [CHANGELOG.md](CHANGELOG.md) for user-visible changes.

## Submitting changes

1. Fork or branch and make the change following the conventions above.
2. Verify the debug build and `:app:testPublicDebugUnitTest`, both with
   `--no-configuration-cache`.
3. Open a PR using [the template](.github/pull_request_template.md).

## Reporting issues

Use the issue templates. Bug reports should include the crash log from Settings →
Diagnostics → Export crash log — and check that it contains no keys or addresses you
don't want to share.
