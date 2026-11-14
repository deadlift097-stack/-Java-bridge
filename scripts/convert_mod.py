#!/usr/bin/env python3
"""Turn ordinary mod jars into ModHost-loadable ones.
usage: convert_mod.py <d8> <android.jar> <api.jar> <in-dir> <out-dir>
For every <in-dir>/*.jar: dex it with d8 and write <name>.json from fabric.mod.json entrypoints."""
import json
import pathlib
import subprocess
import sys
import zipfile

d8, android_jar, api_jar, src, out = sys.argv[1:6]
src, out = pathlib.Path(src), pathlib.Path(out)
out.mkdir(parents=True, exist_ok=True)
report = []


def entrypoints(meta):
    names = []
    for key in ("main", "client"):
        for item in meta.get("entrypoints", {}).get(key, []):
            v = item if isinstance(item, str) else item.get("value", "")
            if v and "::" not in v:
                names.append(v)
    return names


jars = sorted(src.glob("*.jar"))
if not jars:
    report.append("no jars found in mods-in/")
for jar in jars:
    name = jar.stem
    with zipfile.ZipFile(jar) as z:
        files = z.namelist()
        uses_mc = any(f.endswith(".class") and b"net/minecraft/" in z.read(f) for f in files)
        meta = json.loads(z.read("fabric.mod.json")) if "fabric.mod.json" in files else None
    if meta is None:
        report.append(f"{name}: SKIPPED, no fabric.mod.json (write {name}.json by hand: id + main)")
        continue
    eps = entrypoints(meta)
    if not eps:
        report.append(f"{name}: SKIPPED, no usable entrypoints (method references like Class::method are not supported)")
        continue
    cmd = [d8, "--release", "--min-api", "26", "--no-desugaring",
           "--lib", android_jar, "--classpath", api_jar, "--output", str(out / f"{name}.jar"), str(jar)]
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        report.append(f"{name}: d8 FAILED\n{r.stderr[-1500:]}")
        continue
    (out / f"{name}.json").write_text(json.dumps({"id": meta.get("id", name), "entrypoints": eps}, indent=2))
    if uses_mc:
        report.append(f"{name}: converted, but it uses Minecraft Java classes (net.minecraft.*). "
                      "It will load, then fail at start with a clear log message; it cannot work on Bedrock unchanged.")
    else:
        report.append(f"{name}: converted OK, entrypoints: {', '.join(eps)}")

(out / "REPORT.txt").write_text("\n".join(report) + "\n")
print("\n".join(report))
