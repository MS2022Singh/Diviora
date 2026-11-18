# ADR 0003 — Toolchain and SDK levels
Status: Accepted (2026-11-18)

- Gradle wrapper 9.x, Android Gradle Plugin 9.5.x (built-in Kotlin), Kotlin 2.4.x, KSP 2.3.x, JDK 17 toolchain
  target (builds run on JDK 17 or 21).
- minSdk 26, compileSdk 36, targetSdk 36 (Google Play's current requirement level at time of writing;
  re-verify before each release).
- applicationId `com.diviora.music` (placeholder; owner selected "custom" without supplying a value).
  Change it in `gradle.properties` (`diviora.applicationId`) before the first Play upload — it cannot be changed after.
