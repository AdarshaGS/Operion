/** Shared CSV helpers - escaping/download logic used by both the Reports export
 * (ReportDetailPage) and the Student import/export panel, so there's one owner of the
 * quoting rules instead of two copies drifting apart. */
export function toCsv(columns: string[], rows: Record<string, unknown>[]): string {
	const escape = (value: unknown) => `"${String(value ?? "").replace(/"/g, '""')}"`;
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
