import { useEffect, useState } from "react";
import Box from "@mui/material/Box";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import {
	Bar,
	BarChart,
	CartesianGrid,
	Cell,
	Legend,
	Line,
	LineChart,
	Pie,
	PieChart,
	ResponsiveContainer,
	Tooltip as RechartsTooltip,
	XAxis,
	YAxis,
} from "recharts";
import { getDashboardCharts, type DashboardChartsResponse, type DashboardSummaryResponse } from "../../api/dashboard";
import { useAuth } from "../../auth/AuthContext";
import { colors } from "../../theme";

function currency(amount: number): string {
	return `₹${amount.toLocaleString("en-IN")}`;
}

function shortDate(isoDate: string): string {
	return new Date(`${isoDate}T00:00:00`).toLocaleDateString("en-IN", { day: "numeric", month: "short" });
}

function shortMonth(yearMonth: string): string {
	return new Date(`${yearMonth}-01T00:00:00`).toLocaleDateString("en-IN", { month: "short" });
}

function ChartCard({ title, caption, flex = 1, children }: { title: string; caption: string; flex?: number; children: React.ReactNode }) {
	return (
		<Paper variant="outlined" sx={{ p: 2.5, flex: `${flex} 1 0`, minWidth: 0 }}>
			<Typography variant="h6">{title}</Typography>
			<Typography variant="caption" sx={{ color: colors.inkSoft, display: "block", mb: 1 }}>
				{caption}
			</Typography>
			<Box sx={{ height: 240 }}>{children}</Box>
		</Paper>
	);
}

function EmptyChart({ message }: { message: string }) {
	return (
		<Box sx={{ height: "100%", display: "flex", alignItems: "center", justifyContent: "center" }}>
			<Typography variant="body2" sx={{ color: colors.inkSoft }}>
				{message}
			</Typography>
		</Box>
	);
}

interface Slice {
	name: string;
	value: number;
	color: string;
}

/** Donut with its numbers in a legend beside it, so identity never rests on color alone. */
function Donut({ slices, emptyMessage }: { slices: Slice[]; emptyMessage: string }) {
	const total = slices.reduce((sum, slice) => sum + slice.value, 0);
	if (total === 0) return <EmptyChart message={emptyMessage} />;
	return (
		<ResponsiveContainer width="100%" height="100%">
			<PieChart>
				<Pie data={slices.filter((slice) => slice.value > 0)} dataKey="value" nameKey="name" innerRadius="55%" outerRadius="80%" paddingAngle={2} stroke="none">
					{slices.filter((slice) => slice.value > 0).map((slice) => (
						<Cell key={slice.name} fill={slice.color} />
					))}
				</Pie>
				<RechartsTooltip />
				<Legend verticalAlign="bottom" iconType="circle" />
			</PieChart>
		</ResponsiveContainer>
	);
}

/** Dashboard charts (#30 follow-up) - attendance trend, fee collections, enrolment by class, plus
 * today's attendance and fee-collection donuts. Fetched separately from the summary so the headline
 * tiles never wait on the heavier time-series queries; a chart is hidden when its module is off. */
export function DashboardCharts({ summary }: { summary: DashboardSummaryResponse }) {
	const { isPathEnabled } = useAuth();
	const [charts, setCharts] = useState<DashboardChartsResponse | null>(null);

	useEffect(() => {
		// Charts are an enhancement - if the request fails the rest of the dashboard still stands.
		getDashboardCharts().then(setCharts).catch(() => setCharts(null));
	}, []);

	const attendance = summary.attendanceToday;
	const fees = summary.fees;
	const showAttendance = isPathEnabled("/attendance") && attendance !== null;
	const showFees = isPathEnabled("/fees") && fees !== null;
	const showStudents = isPathEnabled("/students");
	if (!showAttendance && !showFees && !showStudents) return null;

	return (
		<Stack spacing={2}>
			{(showAttendance || showFees) && (
				<Stack direction={{ xs: "column", md: "row" }} spacing={2}>
					{showAttendance && (
						<ChartCard title="Attendance trend" caption="Students present, % of those marked — last 30 days" flex={1}>
							{charts && charts.attendanceTrend.length > 0 ? (
								<ResponsiveContainer width="100%" height="100%">
									<LineChart data={charts.attendanceTrend}>
										<CartesianGrid strokeDasharray="3 3" vertical={false} stroke={colors.rule} />
										<XAxis dataKey="date" tickFormatter={shortDate} tickLine={false} axisLine={false} minTickGap={24} />
										<YAxis domain={[0, 100]} unit="%" tickLine={false} axisLine={false} width={48} />
										<RechartsTooltip
											labelFormatter={(label) => shortDate(String(label))}
											formatter={(value, _name, item) => [`${value}% (${item.payload.present} of ${item.payload.marked})`, "Present"]}
										/>
										<Line type="monotone" dataKey="ratePercent" name="Present" stroke={colors.accent} strokeWidth={2} dot={{ r: 3 }} activeDot={{ r: 5 }} />
									</LineChart>
								</ResponsiveContainer>
							) : (
								<EmptyChart message={charts ? "No attendance marked in the last 30 days." : "Loading…"} />
							)}
						</ChartCard>
					)}
					{showFees && (
						<ChartCard title="Fee collections" caption="Cleared payments vs. invoices falling due, by month" flex={1}>
							{charts && charts.feeCollections.some((point) => point.collected > 0 || point.invoiced > 0) ? (
								<ResponsiveContainer width="100%" height="100%">
									<BarChart data={charts.feeCollections} barGap={2}>
										<CartesianGrid strokeDasharray="3 3" vertical={false} stroke={colors.rule} />
										<XAxis dataKey="month" tickFormatter={shortMonth} tickLine={false} axisLine={false} />
										<YAxis tickFormatter={(value) => (value >= 100000 ? `₹${value / 100000}L` : value >= 1000 ? `₹${value / 1000}k` : `₹${value}`)} tickLine={false} axisLine={false} width={52} />
										<RechartsTooltip labelFormatter={(label) => shortMonth(String(label))} formatter={(value) => currency(Number(value))} />
										<Legend iconType="circle" />
										<Bar dataKey="invoiced" name="Invoiced" fill={colors.ruleStrong} radius={[4, 4, 0, 0]} />
										<Bar dataKey="collected" name="Collected" fill={colors.accent} radius={[4, 4, 0, 0]} />
									</BarChart>
								</ResponsiveContainer>
							) : (
								<EmptyChart message={charts ? "No invoices or payments in the last 6 months." : "Loading…"} />
							)}
						</ChartCard>
					)}
				</Stack>
			)}

			<Stack direction={{ xs: "column", md: "row" }} spacing={2}>
				{showStudents && (
					<ChartCard title="Students by class" caption="Currently enrolled" flex={2}>
						{charts && charts.studentsByClass.length > 0 ? (
							<ResponsiveContainer width="100%" height="100%">
								<BarChart data={charts.studentsByClass} margin={{ bottom: 8 }}>
									<CartesianGrid strokeDasharray="3 3" vertical={false} stroke={colors.rule} />
									<XAxis dataKey="className" tickLine={false} axisLine={false} interval={0} angle={charts.studentsByClass.length > 8 ? -35 : 0} textAnchor={charts.studentsByClass.length > 8 ? "end" : "middle"} height={charts.studentsByClass.length > 8 ? 60 : 30} />
									<YAxis allowDecimals={false} tickLine={false} axisLine={false} width={32} />
									<RechartsTooltip />
									<Bar dataKey="students" name="Students" fill={colors.accent} radius={[4, 4, 0, 0]} />
								</BarChart>
							</ResponsiveContainer>
						) : (
							<EmptyChart message={charts ? "No students enrolled in a class yet." : "Loading…"} />
						)}
					</ChartCard>
				)}
				{showAttendance && attendance && (
					<ChartCard title="Today's attendance" caption={`${attendance.marked} marked today`}>
						<Donut
							emptyMessage="Attendance not marked yet today."
							slices={[
								{ name: "Present", value: attendance.present, color: colors.ok },
								{ name: "Late", value: attendance.late, color: colors.warn },
								{ name: "Half day", value: attendance.halfDay, color: colors.ruleStrong },
								{ name: "Absent", value: attendance.absent, color: colors.bad },
							]}
						/>
					</ChartCard>
				)}
				{showFees && fees && (
					<ChartCard title="Fee collection" caption={`${fees.collectionRatePercent}% of ${currency(fees.totalInvoiced)} collected`}>
						<Donut
							emptyMessage="No invoices raised yet."
							slices={[
								{ name: "Collected", value: fees.totalCollected, color: colors.ok },
								{ name: "Outstanding", value: fees.outstanding, color: colors.warn },
							]}
						/>
					</ChartCard>
				)}
			</Stack>
		</Stack>
	);
}
