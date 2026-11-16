#!/usr/bin/env python3
"""Extract Compose colour schemes from the jadx output of the Google Health APK.

R8 obfuscates class/field names, but data-class toString() bodies survive, giving us the
original names (e.g. "ExtendedColorScheme(blue=..., teal=...)"). This script:
  1. finds every class whose toString() returns "<Name>ColorScheme(" or "ColorFamily(",
  2. maps constructor argument positions to the original property labels,
  3. resolves every static `new Scheme(...)` instance to concrete ARGB colours.

Usage: extract_gh_palettes.py <jadx>/sources/defpackage > palettes.json
"""
import json
import os
import re
import sys

SRC = sys.argv[1]
_cache = {}


def src(cls):
    if cls not in _cache:
        p = os.path.join(SRC, cls + ".java")
        _cache[cls] = open(p, errors="ignore").read() if os.path.exists(p) else ""
    return _cache[cls]


def ctor_field_order(cls, s):
    m = re.search(r"public %s\(([^)]*)\)\s*\{(.*?)\n    \}" % cls, s, re.S)
    if not m:
        return None
    params = [p.split()[-1] for p in m.group(1).split(",") if p.strip()]
    assigns = dict((v, f) for f, v in re.findall(r"this\.(\w+) = (\w+);", m.group(2)))
    return [assigns.get(p) for p in params]


def tostring_labels(s):
    m = re.search(r'String toString\(\) \{(.*?)\n    \}', s, re.S)
    if not m:
        return None, {}
    body = m.group(1)
    local = dict(re.findall(r"\w+ (\w+) = this\.(\w+);", body))
    name = re.search(r'return "(\w+)\(', body)
    labels = {}
    for label, expr in re.findall(r'[ "(](\w+)=" \+ ([^+]+?)(?: \+|\)$|;)', body):
        f = re.search(r"this\.(\w+)", expr)
        if f:
            labels[f.group(1)] = label
            continue
        v = re.search(r"\((\w+)\)", expr) or re.search(r"^\s*(\w+)\s*$", expr)
        if v and v.group(1) in local:
            labels[local[v.group(1)]] = label
    return (name.group(1) if name else None), labels


SCHEMES = {}
for f in os.listdir(SRC):
    if not f.endswith(".java"):
        continue
    cls = f[:-5]
    s = src(cls)
    if 'ColorScheme(' not in s and 'ColorFamily(' not in s:
        continue
    name, labels = tostring_labels(s)
    if not name or not (name.endswith("ColorScheme") or name == "ColorFamily"):
        continue
    order = ctor_field_order(cls, s)
    if order:
        SCHEMES[cls] = (name, [labels.get(fld, fld) for fld in order])


def static_expr(cls, field):
    s = src(cls)
    m = re.search(r"static final \w+ %s = (.+?);\n" % re.escape(field), s)
    if m:
        return m.group(1)
    # static-block assignment, possibly through a local (`long j2 = X; b = j2;`)
    m = re.search(r"\n\s+(?:long |int )?%s = (.+?);\n" % re.escape(field), s)
    return m.group(1) if m else None


def split_args(a):
    out, depth, cur = [], 0, ""
    for ch in a:
        if ch in "([":
            depth += 1
        elif ch in ")]":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(cur.strip())
            cur = ""
        else:
            cur += ch
    if cur.strip():
        out.append(cur.strip())
    return out


def color(v):
    v &= 0xFFFFFFFFFFFFFFFF
    if v & 0xFFFFFFFF:
        return None
    return "#%08X" % (v >> 32)


def evaluate(expr, ctx, depth=0):
    expr = expr.strip()
    if depth > 30:
        return None
    m = re.fullmatch(r"(-?\d+)L?", expr)
    if m:
        return color(int(m.group(1)))
    # Color(argb: Int) helpers, e.g. fbc.m(-15640626)
    m = re.fullmatch(r"(?:defpackage\.)?\w+\.\w+\((-?\d+)\)", expr)
    if m:
        return "#%08X" % (int(m.group(1)) & 0xFFFFFFFF)
    m = re.fullmatch(r"new (\w+)\((.*)\)", expr, re.S)
    if m and m.group(1) in SCHEMES:
        name, labels = SCHEMES[m.group(1)]
        args = split_args(m.group(2))
        if len(args) != len(labels):
            return None
        return {"_type": name, **{l: evaluate(a, ctx, depth + 1) for l, a in zip(labels, args)}}
    m = re.fullmatch(r"(?:defpackage\.)?(\w+)\.(\w+)", expr)
    cls, fld = (m.group(1), m.group(2)) if m else (ctx, expr) if re.fullmatch(r"\w+", expr) else (None, None)
    if cls:
        e = static_expr(cls, fld)
        if e:
            return evaluate(e, cls, depth + 1)
    return None


result = {}
for f in sorted(os.listdir(SRC)):
    if not f.endswith(".java"):
        continue
    cls = f[:-5]
    s = src(cls)
    for fld, expr in re.findall(r"public static final \w+ (\w+) = (new (?:%s)\(.*?\));\n" % "|".join(SCHEMES), s):
        v = evaluate(expr, cls)
        if isinstance(v, dict):
            result["%s.%s" % (cls, fld)] = v

json.dump({"schemes": {k: v for k, v in SCHEMES.items()}, "instances": result}, sys.stdout, indent=1)
