"""Small helpers for structural edits of Java sources (used while porting)."""
import re


def remove_member(s, signature_regex):
    """Remove a method/record/class whose declaration matches the regex, including its body (brace matched)."""
    m = re.search(signature_regex, s)
    if not m:
        raise ValueError("not found: " + signature_regex)
    start = s.rfind("\n", 0, m.start()) + 1
    # include preceding annotations/comments lines that belong to it
    i = s.index("{", m.end() - 1) if s[m.end() - 1] != "{" else m.end() - 1
    depth = 0
    j = i
    while True:
        c = s[j]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                break
        j += 1
    end = s.index("\n", j) + 1
    # also drop one following blank line
    if s[end:end + 1] == "\n":
        end += 1
    prev = s.rfind("\n", 0, start - 1) + 1
    if s[prev:start].strip().startswith("@"):
        start = prev
    return s[:start] + s[end:]


def remove_lines(s, *needles):
    out = []
    for line in s.split("\n"):
        if any(n in line for n in needles):
            continue
        out.append(line)
    return "\n".join(out)
