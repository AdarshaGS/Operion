const MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];

export function monthLabel(year: number, month: number): string {
	return `${MONTHS[month - 1]} ${year}`;
}

export function money(amount: number): string {
	return amount.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export function runStatusColor(status: string): "default" | "warning" | "success" {
	if (status === "FINALIZED") return "success";
	if (status === "DRAFT") return "warning";
	return "default";
}
