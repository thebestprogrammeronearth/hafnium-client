#!/usr/bin/env python3
"""Translate intermediary names (class_123, method_456, field_789) to Mojang names using Loom's mappings.tiny."""
import sys, glob, os
path = glob.glob(os.path.expanduser("~/.gradle/caches/fabric-loom/1.21.11/loom.mappings.*/mappings.tiny"))[0]
want = set(sys.argv[1:])
cls = None
for line in open(path):
    p = line.rstrip("\n").split("\t")
    if p[0] == "c":
        cls = p[3]
        if p[2].split("/")[-1] in want or p[2] in want:
            print(p[2], "->", p[3])
    elif len(p) > 1 and p[1] in ("m", "f") and p[4] in want:
        print(p[4], "->", cls + ("." if p[1]=="f" else "#") + p[5], p[2])
