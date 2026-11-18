# ADR 0005 — GitHub sync deferred, local git first
Status: Accepted (2026-11-18, chosen by owner)

The build session has no credential to create a GitHub repository. Work proceeds with local git: every
validated change is committed, and every milestone gets a tag. A local bare mirror (`../diviora-music-mirror.git`)
stands in for "origin", so restore verification (a fresh clone, then a build) can run now. When the owner provides an
empty GitHub repo, the agent runs `git remote set-url origin <url>` and then `git push --all && git push --tags`. That
publishes the full history and every checkpoint, and the CI workflow in `.github/workflows/ci.yml` activates.

## Update 2026-11-18
GitHub repo created by the owner: https://github.com/MS2022Singh/diviora. `origin` = GitHub, `mirror` = local bare
backup. Branches `main`, `develop` and `feat/*` are pushed. The build session's git proxy refuses **tag** pushes (HTTP 403), so
the tags `v0.0.1-bootstrap`, `v0.1.0-mvp` and `v0.2.0-catalog` exist locally and in the mirror only. Owner can publish them from a
machine with GitHub access: `git fetch origin && git push origin --tags` from a clone that has them, or create
them on GitHub at the commits listed in docs/STATE.md.
