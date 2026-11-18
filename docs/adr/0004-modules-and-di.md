# ADR 0004 — Initial module layout and DI
Status: Accepted (2026-11-18)

The blueprint's full module tree (~40 modules) is the target. The first milestones use a smaller split so the
build stays fast and simple while the core works:

- `:core:model` (pure Kotlin/JVM) — domain models shared everywhere.
- `:scheduler:recurrence` (pure Kotlin/JVM) — recurrence and next-occurrence maths, unit-tested with fixed clocks.
- `:app` — Compose UI, Room database, Media3 playback service, alarm receivers.

Dependency injection is a hand-written `AppContainer` (no Hilt yet). That avoids extra annotation processing.
Features are split out of `:app` into their own modules once their APIs settle.
