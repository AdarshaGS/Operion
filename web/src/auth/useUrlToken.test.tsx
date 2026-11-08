import { fireEvent, render, renderHook, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ResetPasswordPage } from "./ResetPasswordPage";
import { removeQueryParam, useUrlToken } from "./useUrlToken";
import * as authApi from "../api/auth";
import { ApiError } from "../api/client";

vi.mock("../api/auth");

afterEach(() => {
	window.history.replaceState(null, "", "/");
	vi.restoreAllMocks();
});

describe("useUrlToken", () => {
	it("returns the token from the URL and removes it from the address bar, keeping other params and the hash", () => {
		window.history.replaceState({ idx: 3 }, "", "/reset-password?org=acme&token=s3cret#top");

		const { result } = renderHook(() => useUrlToken());

		expect(result.current).toBe("s3cret");
		expect(window.location.search).toBe("?org=acme");
		expect(window.location.hash).toBe("#top");
		expect(window.location.href).not.toContain("s3cret");
		// Router bookkeeping that lives in history.state must survive the rewrite.
		expect(window.history.state).toEqual({ idx: 3 });
	});

	it("keeps the token in state across re-renders after the URL no longer has it", () => {
		window.history.replaceState(null, "", "/claim-invite?token=abc123");
		const { result, rerender } = renderHook(() => useUrlToken());
		rerender();
		rerender();
		expect(window.location.search).toBe("");
		expect(result.current).toBe("abc123");
	});

	it("returns an empty string and leaves the URL alone when there is no token", () => {
		window.history.replaceState(null, "", "/reset-password?org=acme");
		const { result } = renderHook(() => useUrlToken());
		expect(result.current).toBe("");
		expect(window.location.search).toBe("?org=acme");
	});

	it("does not add history entries", () => {
		window.history.replaceState(null, "", "/verify-email?token=zzz");
		const before = window.history.length;
		renderHook(() => useUrlToken());
		expect(window.history.length).toBe(before);
	});
});

describe("removeQueryParam", () => {
	it("is a no-op when the param is absent", () => {
		window.history.replaceState(null, "", "/x?a=1");
		const spy = vi.spyOn(window.history, "replaceState");
		removeQueryParam("token");
		expect(spy).not.toHaveBeenCalled();
	});
});

describe("ResetPasswordPage", () => {
	it("pre-fills the token from the link, strips it from the URL, and keeps it through a failed submit", async () => {
		window.history.replaceState(null, "", "/reset-password?org=acme&token=one-time-token");
		vi.mocked(authApi.confirmPasswordReset).mockRejectedValue(new ApiError(400, "Invalid or expired token"));

		render(
			<MemoryRouter initialEntries={["/reset-password?org=acme&token=one-time-token"]}>
				<ResetPasswordPage />
			</MemoryRouter>,
		);

		// Address bar no longer carries the token...
		expect(window.location.search).toBe("?org=acme");
		// ...but the form still has it, so the user can submit without it being in the URL.
		expect(screen.getByLabelText(/reset token/i)).toHaveValue("one-time-token");

		fireEvent.change(screen.getByLabelText(/^new password/i), { target: { value: "Str0ng!Passw0rd" } });
		fireEvent.change(screen.getByLabelText(/confirm new password/i), { target: { value: "Str0ng!Passw0rd" } });
		fireEvent.click(screen.getByRole("button", { name: /reset password/i }));

		expect(await screen.findByText("Invalid or expired token")).toBeInTheDocument();
		expect(authApi.confirmPasswordReset).toHaveBeenCalledWith(
			expect.objectContaining({ organisationSlug: "acme", token: "one-time-token" }),
		);
		expect(window.location.href).not.toContain("one-time-token");
		expect(screen.getByLabelText(/reset token/i)).toHaveValue("one-time-token");
	});
});
