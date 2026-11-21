#!/usr/bin/env python3
"""Render docs/product-map/roles/*.md into docs/product-map/roles/index.html.

No dependencies. Run from anywhere:
    python3 docs/product-map/build-roles.py
Then open docs/product-map/roles/index.html.

Supports the subset of Markdown the role template uses: front matter, headings,
blockquote, bullet and numbered lists, checkboxes, tables, inline code, bold,
horizontal rule.
"""
import html
import re
from pathlib import Path

ROLES_DIR = Path(__file__).resolve().parent / "roles"
OUT = ROLES_DIR / "index.html"


def parse_front_matter(text):
    if not text.startswith("---"):
        return {}, text
    end = text.find("\n---", 3)
    if end == -1:
        return {}, text
    meta = {}
    for line in text[3:end].splitlines():
        line = line.split("  #")[0].strip()
        if not line or line.startswith("#") or ":" not in line:
            continue
        key, val = line.split(":", 1)
        meta[key.strip()] = val.strip()
    return meta, text[end + 4:]


def inline(s):
    s = html.escape(s, quote=False)
    s = re.sub(r"`([^`]+)`", r"<code>\1</code>", s)
    s = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", s)
    s = s.replace("[ ]", "&#9744;").replace("[x]", "&#9745;")
    return s


def render_body(md):
    out, i, lines = [], 0, md.splitlines()
    while i < len(lines):
        line = lines[i]
        if line.startswith("|"):
            rows = []
            while i < len(lines) and lines[i].startswith("|"):
                rows.append([c.strip() for c in lines[i].strip().strip("|").split("|")])
                i += 1
            rows = [r for r in rows if not all(re.fullmatch(r"-+", c) for c in r)]
            out.append("<table><thead><tr>" + "".join(f"<th>{inline(c)}</th>" for c in rows[0]) + "</tr></thead><tbody>")
            for r in rows[1:]:
                out.append("<tr>" + "".join(f"<td>{inline(c)}</td>" for c in r) + "</tr>")
            out.append("</tbody></table>")
            continue
        m = re.match(r"^(#{1,3}) (.*)", line)
        if m:
            out.append(f"<h{len(m.group(1))}>{inline(m.group(2))}</h{len(m.group(1))}>")
        elif line.startswith("> "):
            out.append(f"<blockquote>{inline(line[2:])}</blockquote>")
        elif line.strip() == "---":
            out.append("<hr>")
        elif re.match(r"^- ", line):
            out.append("<ul>")
            while i < len(lines) and re.match(r"^- ", lines[i]):
                out.append(f"<li>{inline(lines[i][2:])}</li>")
                i += 1
            out.append("</ul>")
            continue
        elif re.match(r"^\d+\. ", line):
            out.append("<ol>")
            while i < len(lines) and re.match(r"^\d+\. ", lines[i]):
                out.append(f"<li>{inline(re.sub(r'^\d+\. ', '', lines[i]))}</li>")
                i += 1
            out.append("</ol>")
            continue
        elif line.strip():
            out.append(f"<p>{inline(line)}</p>")
        i += 1
    return "\n".join(out)


CSS = """
body{font-family:-apple-system,Segoe UI,Helvetica,Arial,sans-serif;margin:0;background:#f6f7f9;color:#1d2330}
nav{position:fixed;top:0;left:0;width:220px;height:100vh;background:#1d2330;color:#fff;padding:20px;box-sizing:border-box;overflow:auto}
nav a{display:block;color:#cfd6e4;text-decoration:none;padding:6px 0}
nav a:hover{color:#fff}
main{margin-left:220px;padding:32px 48px;max-width:1000px}
article{background:#fff;border:1px solid #e1e4ea;border-radius:8px;padding:28px 36px;margin-bottom:40px}
h1{margin-top:0}
h2{border-bottom:1px solid #e1e4ea;padding-bottom:4px;margin-top:28px}
blockquote{border-left:4px solid #3b6fd4;margin:0;padding:6px 14px;background:#eef3fc}
code{background:#f0f2f6;padding:1px 5px;border-radius:3px;font-size:.92em}
table{border-collapse:collapse;width:100%;margin:10px 0;font-size:.94em}
th,td{border:1px solid #dfe3ea;padding:6px 10px;text-align:left;vertical-align:top}
th{background:#f0f2f6}
.meta{display:grid;grid-template-columns:max-content 1fr;gap:4px 16px;font-size:.88em;background:#fafbfc;border:1px dashed #cfd6e4;padding:12px;border-radius:6px;margin-bottom:20px}
.meta b{color:#4a5568}
.warn{background:#fff4e5;border:1px solid #f2c48c;padding:8px 12px;border-radius:6px;font-size:.9em}
"""


def main():
    pages = []
    for path in sorted(ROLES_DIR.glob("*.md")):
        if path.name.startswith("_"):
            continue
        meta, body = parse_front_matter(path.read_text(encoding="utf-8"))
        pages.append((path.stem, meta, body))

    nav = "".join(f'<a href="#{slug}">{html.escape(meta.get("role", slug))}</a>' for slug, meta, _ in pages)
    articles = []
    for slug, meta, body in pages:
        meta_html = "".join(f"<b>{html.escape(k)}</b><span>{inline(v)}</span>" for k, v in meta.items())
        warn = ""
        if meta.get("verified_by_running_app", "no").lower() != "yes":
            warn = '<p class="warn">Written from source files only. Not yet checked against the running app.</p>'
        articles.append(f'<article id="{slug}">{warn}<div class="meta">{meta_html}</div>{render_body(body)}</article>')

    OUT.write_text(
        "<!doctype html><html><head><meta charset='utf-8'><title>Operion role pages</title>"
        f"<style>{CSS}</style></head><body><nav><h3>Roles</h3>{nav}</nav><main>"
        f"<p>{len(pages)} role page(s). Source: <code>docs/product-map/roles/*.md</code>. Regenerate with <code>python3 docs/product-map/build-roles.py</code>.</p>"
        + "".join(articles) + "</main></body></html>",
        encoding="utf-8",
    )
    print(f"wrote {OUT} ({len(pages)} pages)")


if __name__ == "__main__":
    main()
