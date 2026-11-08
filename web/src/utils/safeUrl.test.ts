import { describe, expect, it } from "vitest";
import { safeHref } from "./safeUrl";

describe("safeHref", () => {
	it.each([
		["http://example.com/a", "http://example.com/a"],
		["https://example.com/a?b=1#c", "https://example.com/a?b=1#c"],
		["  https://example.com/padded  ", "https://example.com/padded"],
		["HTTPS://EXAMPLE.COM/", "https://example.com/"],
	])("allows %j", (input, expected) => {
		expect(safeHref(input)).toBe(expected);
	});

	it.each([
		"javascript:alert(1)",
		"JaVaScRiPt:alert(1)",
		"  javascript:alert(1)",
		"java\tscript:alert(1)",
		"java\nscript:alert(1)",
		"\u0001javascript:alert(1)",
		"data:text/html,<script>alert(1)</script>",
		"data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==",
		"vbscript:msgbox(1)",
		"file:///etc/passwd",
		"blob:https://example.com/uuid",
		"ftp://example.com/x",
		"mailto:a@b.c",
	])("refuses %j", (input) => {
		expect(safeHref(input)).toBeNull();
	});

	it("allows mailto: only when asked, and still refuses scripts then", () => {
		expect(safeHref("mailto:a@b.c", { allowMailto: true })).toBe("mailto:a@b.c");
		expect(safeHref("javascript:alert(1)", { allowMailto: true })).toBeNull();
		expect(safeHref("data:text/html,x", { allowMailto: true })).toBeNull();
	});

	it.each([null, undefined, "", "   "])("returns null for empty input %j", (input) => {
		expect(safeHref(input)).toBeNull();
	});

	it("resolves a relative path against the current origin as an http(s) link", () => {
		expect(safeHref("/uploads/x.pdf")).toBe(`${window.location.origin}/uploads/x.pdf`);
	});
});
