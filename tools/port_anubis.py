#!/usr/bin/env python3
"""Copy decompiled Anubis sources (reference/anubis) into Hafnium with package rewrites and a GPL attribution header.

Usage: tools/port_anubis.py <anubis path without .java, relative to net/anubis/client> [<target package>] ...
The result is a starting point; each file is then adapted to Hafnium's APIs by hand.
"""
import re, sys, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
REF = ROOT / "reference/anubis/net/anubis/client"
OUT = ROOT / "src/main/java"
HEADER = """/*
 * Ported from Anubis (https://github.com/4ldenz/Anubis-client), Copyright (c) 4ldenz, GPL-3.0-only.
 * Adapted for Hafnium Client; modifications Copyright (c) Hafnium contributors, GPL-3.0-only.
 */
"""
PKG = [
    (r"net\.anubis\.client\.event\.impl\.Render3DEvent", "dev.hafnium.client.event.WorldRenderEvent"),
    (r"\bRender3DEvent\b", "WorldRenderEvent"),
    (r"net\.anubis\.client\.event\.impl\.", "dev.hafnium.client.event."),
    (r"net\.anubis\.client\.module\.impl\.[a-z]+\.", "dev.hafnium.client.modules.anubis."),
    (r"net\.anubis\.client\.module\.setting\.", "dev.hafnium.client.anubis.setting."),
    (r"com\.example\.client\.mixin\.", "dev.hafnium.client.mixin.anubis."),
    (r"net\.anubis\.client\.", "dev.hafnium.client.anubis."),
]

def port(rel, target_pkg=None):
    src = REF / (rel + ".java")
    text = src.read_text()
    for a, b in PKG:
        text = re.sub(a, b, text)
    pkg = re.search(r"^package ([\w.]+);", text, re.M).group(1)
    if target_pkg:
        text = text.replace(f"package {pkg};", f"package {target_pkg};", 1)
        pkg = target_pkg
    text = text.replace("@Environment(EnvType.CLIENT)\n", "")
    text = re.sub(r"import net\.fabricmc\.api\.(EnvType|Environment);\n", "", text)
    dest = OUT / pkg.replace(".", "/") / src.name.replace("Render3DEvent", "WorldRenderEvent")
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(HEADER + text)
    print(dest.relative_to(ROOT))

args = sys.argv[1:]
for a in args:
    rel, _, tgt = a.partition("=")
    port(rel, tgt or None)
