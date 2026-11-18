# Contributing
- Branches: `feat/*`, `fix/*`, `chore/*` off `develop`; `main` holds released milestones only.
- Conventional Commits (`feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:`, `ci:`).
- Every change: build + unit tests green before commit (`./gradlew check assembleDebug`).
- All work is done from the command line; no GUI-only steps.
- Never commit secrets. Reuse only permissively licensed code and record it in `THIRD_PARTY_NOTICES.md`.
