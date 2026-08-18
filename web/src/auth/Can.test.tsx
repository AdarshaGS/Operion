import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { Can } from "./Can";
import * as AuthContext from "./AuthContext";

describe("Can", () => {
	it("renders children when the caller holds one of the required permissions", () => {
		vi.spyOn(AuthContext, "useAuth").mockReturnValue({
			hasAnyPermission: (codes: string[]) => codes.includes("STUDENT_MANAGE"),
		} as unknown as ReturnType<typeof AuthContext.useAuth>);

		render(
			<Can anyOf={["STUDENT_MANAGE"]}>
				<button>Admit student</button>
			</Can>,
		);

		expect(screen.getByText("Admit student")).toBeInTheDocument();
	});

	it("renders nothing when the caller holds none of the required permissions", () => {
		vi.spyOn(AuthContext, "useAuth").mockReturnValue({
			hasAnyPermission: () => false,
		} as unknown as ReturnType<typeof AuthContext.useAuth>);

		render(
			<Can anyOf={["STUDENT_MANAGE"]}>
				<button>Admit student</button>
			</Can>,
		);

		expect(screen.queryByText("Admit student")).not.toBeInTheDocument();
	});
});
