"""Keeps CHANGELOG.md in step with the published releases.

  changelog.py file CHANGELOG VERSION NOTES PUBLISHED
      After a release is published: the notes it published go under "## VERSION — date", and anything under "## Unreleased" that
      has now been published (in NOTES or in an earlier release, PUBLISHED) is taken out of Unreleased.

A changelog entry is matched to a published one the same way the build does when it leaves out notes an earlier release already
published: by its words, ignoring case and punctuation.
"""
import datetime
import re
import sys

KINDS = ("New", "Improved", "Fixed")
BULLET = re.compile(r"\s*[-*]\s+(.*)")


def key(text):
    return re.sub(r"[^a-z0-9]+", " ", text.lower()).strip()


def grouped(lines):
    """Bullets sorted into New / Improved / Fixed, in their order, each once. Bullets under no heading count as Improved."""
    out = {k: [] for k in KINDS}
    kind, seen = "Improved", set()
    for line in lines:
        h = re.match(r"###\s+(New|Improved|Fixed)\s*$", line.strip())
        if h:
            kind = h.group(1)
            continue
        if line.strip().startswith("### "):  # "### Downloads" and the like: not part of the notes
            kind = None
            continue
        m = BULLET.match(line)
        if m and kind and key(m.group(1)) not in seen:
            seen.add(key(m.group(1)))
            out[kind].append("- " + m.group(1).rstrip())
    return out


def render(groups):
    lines = []
    for k in KINDS:
        if groups[k]:
            lines += ["### " + k] + groups[k] + [""]
    return lines


def split(text):
    """(lines before the first "## " section, the Unreleased section's lines or None, the rest)."""
    lines = text.split("\n")
    starts = [i for i, l in enumerate(lines) if l.startswith("## ")]
    if not starts:
        return lines, None, []
    first = starts[0]
    if lines[first].strip() == "## Unreleased":
        end = starts[1] if len(starts) > 1 else len(lines)
        return lines[:first], lines[first + 1:end], lines[end:]
    return lines[:first], None, lines[first:]


def file_release(path, version, notes_path, published_path):
    notes = open(notes_path, encoding="utf-8").read().split("\n")
    published = set()
    for p in (notes_path, published_path):
        for line in open(p, encoding="utf-8", errors="ignore"):
            m = BULLET.match(line)
            if m:
                published.add(key(m.group(1)))
    head, unreleased, rest = split(open(path, encoding="utf-8").read())
    keep = grouped(unreleased or [])
    for k in KINDS:
        keep[k] = [l for l in keep[k] if key(l[2:]) not in published]
    shipped = render(grouped(notes)) or ["- Small fixes and improvements", ""]
    date = datetime.datetime.now(datetime.timezone.utc).strftime("%d %b %Y").lstrip("0")
    while head and not head[-1].strip():
        head.pop()
    out = head + ["", "## Unreleased", ""] + render(keep) + ["## %s — %s" % (version, date), ""] + shipped + rest
    open(path, "w", encoding="utf-8").write("\n".join(out).rstrip("\n") + "\n")


if __name__ == "__main__":
    if len(sys.argv) == 6 and sys.argv[1] == "file":
        file_release(*sys.argv[2:])
    else:
        sys.exit(__doc__)
