# ADR 0001 — Native, local-first Kotlin stack
Status: Accepted (2026-11-18)

Decision: Kotlin + Jetpack Compose + Material 3 + AndroidX Media3 + Room + DataStore + AlarmManager.
Local playback and the Routine scheduler must work offline with no accounts. Streaming providers are optional
adapters added later behind a capability matrix.
