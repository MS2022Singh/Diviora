#!/usr/bin/env python3
"""Validate a DIVIORA catalog file before publishing it. Usage: scripts/validate-catalog.py catalog.json"""
import json, sys
p = sys.argv[1]
data = open(p, "rb").read()
errors, warnings = [], []
if len(data) > 2 * 1024 * 1024: errors.append("file is larger than 2 MB")
c = json.loads(data)
if c.get("version") != 1: errors.append("version must be 1")
if not str(c.get("name", "")).strip(): errors.append("name is required")
ids = set()
for i, it in enumerate(c.get("items", [])):
    where = f"items[{i}] ({it.get('id')})"
    if not it.get("id") or it["id"] in ids: errors.append(f"{where}: id missing or duplicate")
    ids.add(it.get("id"))
    if not it.get("title"): errors.append(f"{where}: title missing")
    if not str(it.get("url", "")).lower().startswith(("https://", "http://")): errors.append(f"{where}: url must be http(s)")
    elif it["url"].lower().startswith("http://"): warnings.append(f"{where}: http:// works but https:// is recommended")
    if it.get("type", "audio") not in ("audio", "video", "stream"): errors.append(f"{where}: type must be audio|video|stream")
    for host in ("youtube.com", "youtu.be", "spotify.com", "gaana.com", "music.amazon", "jiosaavn.com"):
        if host in str(it.get("url", "")): errors.append(f"{where}: {host} page links are not media files and cannot play")
for p_ in c.get("playlists", []):
    for ref in p_.get("itemIds", []):
        if ref not in ids: warnings.append(f"playlist {p_.get('id')}: unknown item id {ref}")
for w in warnings: print("WARN ", w)
for e in errors: print("ERROR", e)
print(f"{len(c.get('items', []))} items, {len(c.get('playlists', []))} playlists:", "INVALID" if errors else "OK")
sys.exit(1 if errors else 0)
