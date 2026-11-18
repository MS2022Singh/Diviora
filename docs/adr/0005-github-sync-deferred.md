# ADR 0005 — GitHub sync deferred, local git first
Status: Accepted (2026-11-18, chosen by owner)

The build session has no credential to create a GitHub repository. Work proceeds with local git: every
validated change is committed, and every milestone gets a tag. A local bare mirror (`../diviora-music-mirror.git`)
stands in for "origin", so restore verification (a fresh clone, then a build) can run now. When the owner provides an
empty GitHub repo, the agent runs `git remote set-url origin <url>` and then `git push --all && git push --tags`. That
publishes the full history and every checkpoint, and the CI workflow in `.github/workflows/ci.yml` activates.
