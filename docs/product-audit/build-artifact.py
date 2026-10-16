#!/usr/bin/env python3
"""Build the publishable artifact page from PRODUCT_GAP_AUDIT.md.

Usage: python3 docs/product-audit/build-artifact.py <output.html>

The markdown file stays the source of truth; the page embeds it and renders it client-side.
After building, republish the output to the existing artifact URL (see README.md in this folder).
"""
import pathlib
import sys

here = pathlib.Path(__file__).resolve().parent
md = (here / "PRODUCT_GAP_AUDIT.md").read_text(encoding="utf-8")
tpl = (here / "artifact-template.html").read_text(encoding="utf-8")

# The markdown sits inside a <script> element, so a literal closing tag must be neutralised
# (the page's script restores it before rendering).
md = md.replace("</script", "<\\/script")
out = tpl.replace("__MARKDOWN__", md)

if len(sys.argv) != 2:
    sys.exit(__doc__)
pathlib.Path(sys.argv[1]).write_text(out, encoding="utf-8")
print(f"wrote {sys.argv[1]} ({len(out) / 1024:.0f} KB)")
