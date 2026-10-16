/** Shared CSV helpers - escaping/download logic used by both the Reports export
 * (ReportDetailPage) and the Student import/export panel, so there's one owner of the
 * quoting rules instead of two copies drifting apart. */
/** Spreadsheets run a cell starting with = + - @ (or tab/CR) as a formula, so an exported
 * name like =HYPERLINK(...) would execute when opened in Excel (CSV injection). Prefixing
 * a single quote makes it plain text; real numbers (a bare "-5") are left alone. */
function neutraliseFormula(value: unknown): string {
	const text = String(value ?? "");
	if (typeof value === "number" || !/^[=+\-@\t\r]/.test(text) || /^[+-]?\d+(\.\d+)?$/.test(text)) {
		return text;
	}
	return `'${text}`;
}

export function toCsv(columns: string[], rows: Record<string, unknown>[]): string {
	const escape = (value: unknown) => `"${neutraliseFormula(value).replace(/"/g, '""')}"`;
	const lines = [columns.map(escape).join(","), ...rows.map((row) => columns.map((column) => escape(row[column])).join(","))];
	return lines.join("\n");
}

export function downloadCsvFile(filename: string, csvText: string) {
	const blob = new Blob([csvText], { type: "text/csv" });
	const url = URL.createObjectURL(blob);
	const link = document.createElement("a");
	link.href = url;
	link.download = filename;
	link.click();
	URL.revokeObjectURL(url);
}
