# Restore and rollback guide

Tags: `v0.N.0-<name>` = milestones, `cp-YYYYMMDD-HHMM` = checkpoints before risky changes. Tags are never deleted.

```bash
git fetch --all --tags
git tag -l --sort=-creatordate | head            # newest restore points
git log --oneline --decorate --graph -20
git switch -c recovery/<name> <tag>             # safe: new branch at the known-good state
git restore --source <tag> -- <path>            # bring back specific files only
git revert <bad-commit>                         # undo on a shared branch without rewriting history
scripts/verify-restore.sh <tag>                 # Linux/macOS: fresh clone → build → tests
powershell -File scripts/verify-restore.ps1 -Ref <tag>   # Windows
```
`git reset --hard` and force-pushes are used only with the owner's explicit approval, after a backup tag.

## Connecting GitHub (pending, ADR 0005)
```bash
git remote set-url origin https://github.com/<owner>/diviora-music.git
git push -u origin --all
git push origin --tags
```
