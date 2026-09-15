---
name: Feature request
about: Suggest an idea or improvement for V.E.L.A.
labels: enhancement
---

**Problem to solve**

What are you trying to do, and what gets in the way today?

**Proposed solution**

What should happen, concretely?

**Alternatives considered**

Other approaches, features in other apps you'd compare against, workarounds you use now.

**Offline-first check**

V.E.L.A. is 100% offline-first — the only network traffic is to servers the
user configures (LAN / tailnet / cloud API profiles). No analytics, telemetry
or remote config, ever. Does this feature respect that constraint?

**Scope check (for contributors)**

- The project adds **no new Gradle dependencies** without discussion.
- Room schema changes must ship a real migration in
  `data/local/Migrations.kt` — per-profile/UI state belongs in DataStore.

**Additional context**

Mockups, links, or related issues.
