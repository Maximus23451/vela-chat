## Summary

<!-- What does this PR do, and why? Reference issues where relevant. -->

## Change type

- [ ] Bug fix
- [ ] New feature
- [ ] Refactor (no behavior change)
- [ ] Documentation
- [ ] CI / build
- [ ] Other (please describe)

## Testing

- [ ] `./gradlew :app:assemblePublicDebug --no-configuration-cache` succeeds
- [ ] `./gradlew :app:testPublicDebugUnitTest --no-configuration-cache` — unit tests pass
- [ ] Docs updated where applicable (README / CHANGELOG / docs/*)
- [ ] Follows the conventions in [AGENTS.md](../AGENTS.md)
- [ ] Room schema changes ship a real migration in `data/local/Migrations.kt` (or N/A)
- [ ] No new Gradle dependencies introduced without prior discussion (or N/A)
- [ ] No open work markers (unresolved-work placeholder comments) left in code or docs
- [ ] `versionCode`/`versionName` bumped if this is a release (or N/A)
- [ ] `scripts/check-secrets.sh --tree` passes and I read the diff for passwords, keys, tokens, real IPs and personal data

## Screenshots / recordings (UI changes)

<!-- Attach before/after captures if the change is user-visible. -->
