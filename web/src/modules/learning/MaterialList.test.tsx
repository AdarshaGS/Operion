import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { MaterialResponse } from "../../api/learning";
import { MaterialAction } from "./MaterialList";

function linkMaterial(url: string): MaterialResponse {
	return { id: 1, title: "t", kind: "LINK", url, fileUrl: null } as unknown as MaterialResponse;
}

describe("MaterialAction", () => {
	it("renders an http(s) LINK material as a real link", () => {
		render(<MaterialAction material={linkMaterial("https://example.com/notes")} />);
		const link = screen.getByRole("link", { name: /open link/i });
		expect(link).toHaveAttribute("href", "https://example.com/notes");
		expect(link).toHaveAttribute("rel", expect.stringContaining("noreferrer"));
	});

	it.each(["javascript:alert(document.cookie)", "data:text/html,<script>alert(1)</script>", "vbscript:msgbox(1)"])(
		"renders %j as plain text, never as a link",
		(url) => {
			render(<MaterialAction material={linkMaterial(url)} />);
			expect(screen.queryByRole("link")).not.toBeInTheDocument();
			expect(document.querySelector("a")).toBeNull();
			expect(screen.getByText(url)).toBeInTheDocument();
		},
	);
});
