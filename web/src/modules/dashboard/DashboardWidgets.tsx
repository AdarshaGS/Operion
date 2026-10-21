import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Box from "@mui/material/Box";
import ButtonBase from "@mui/material/ButtonBase";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import BarChartIcon from "@mui/icons-material/BarChart";
import CampaignIcon from "@mui/icons-material/Campaign";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import EventIcon from "@mui/icons-material/Event";
import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip as RechartsTooltip, XAxis, YAxis } from "recharts";
import { listAnnouncements, type AnnouncementResponse } from "../../api/announcements";
import type { DashboardChartsResponse } from "../../api/dashboard";
import { listSchoolEvents, type SchoolEventResponse } from "../../api/schoolEvents";
import { useAuth } from "../../auth/AuthContext";
import { EmptyNote, IconTile, SectionCard } from "../../components/DashboardKit";
import { colors } from "../../theme";

const DAYS_AHEAD = 45;

function isoDate(date: Date): string {
	const offset = date.getTimezoneOffset() * 60_000;
	return new Date(date.getTime() - offset).toISOString().slice(0, 10);
}

function weekday(isoDay: string): string {
	return new Date(`${isoDay}T00:00:00`).toLocaleDateString("en-IN", { weekday: "short" });
}

/** The most recent school days with attendance marked, as bars of % present. */
export function AttendanceOverview({ charts }: { charts: DashboardChartsResponse | null }) {
	const points = (charts?.attendanceTrend ?? []).slice(-7);
	return (
		<SectionCard title="Attendance overview" icon={<BarChartIcon fontSize="small" />} tint="green" actionLabel="Open" actionPath="/attendance">
			{points.length === 0 ? (
				<EmptyNote>{charts ? "No attendance marked in the last 30 days." : "Loading…"}</EmptyNote>
			) : (
				<Box sx={{ height: 230 }}>
					<ResponsiveContainer width="100%" height="100%">
						<BarChart data={points} barCategoryGap="28%">
							<CartesianGrid strokeDasharray="3 3" vertical={false} stroke={colors.rule} />
							<XAxis dataKey="date" tickFormatter={weekday} tickLine={false} axisLine={false} />
							<YAxis domain={[0, 100]} unit="%" tickLine={false} axisLine={false} width={44} />
							<RechartsTooltip
								cursor={{ fill: colors.accentSoft }}
								labelFormatter={(label) => new Date(`${String(label)}T00:00:00`).toLocaleDateString("en-IN", { weekday: "long", day: "numeric", month: "short" })}
								formatter={(value, _name, item) => [`${value}% (${item.payload.present} of ${item.payload.marked})`, "Present"]}
							/>
							<Bar dataKey="ratePercent" name="Present" radius={[8, 8, 0, 0]}>
								{points.map((point, index) => (
									<Cell key={point.date} fill={colors.ok} fillOpacity={index === points.length - 1 ? 1 : 0.7} />
								))}
							</Bar>
						</BarChart>
					</ResponsiveContainer>
				</Box>
			)}
		</SectionCard>
	);
}

/** Latest published announcements. */
export function AnnouncementsCard() {
	const navigate = useNavigate();
	const { hasPermission, isPathEnabled, permissionsLoaded } = useAuth();
	const allowed = permissionsLoaded && isPathEnabled("/communication") && hasPermission("COMMUNICATION_VIEW");
	const [items, setItems] = useState<AnnouncementResponse[] | null>(null);

	useEffect(() => {
		if (!allowed) return;
		listAnnouncements("PUBLISHED")
			.then((all) => setItems([...all].sort((a, b) => (b.publishedAt ?? "").localeCompare(a.publishedAt ?? "")).slice(0, 3)))
			.catch(() => setItems([]));
	}, [allowed]);

	if (!allowed) return null;
	return (
		<SectionCard title="Announcements" icon={<CampaignIcon fontSize="small" />} tint="violet" actionLabel="View all" actionPath="/communication">
			{items === null ? (
				<EmptyNote>Loading…</EmptyNote>
			) : items.length === 0 ? (
				<EmptyNote>No announcements published yet.</EmptyNote>
			) : (
				<Stack spacing={1.25}>
					{items.map((item) => (
						<ButtonBase
							key={item.id}
							onClick={() => navigate("/communication")}
							sx={{ display: "flex", gap: 1.5, p: 1.5, borderRadius: "12px", border: `1px solid ${colors.rule}`, textAlign: "left", justifyContent: "flex-start", alignItems: "center", "&:hover": { backgroundColor: colors.paperSunken } }}
						>
							<IconTile icon={<CampaignIcon fontSize="small" />} tint="blue" size={40} />
							<Box sx={{ minWidth: 0, flexGrow: 1 }}>
								<Typography variant="body2" noWrap sx={{ fontWeight: 700, color: colors.ink }}>
									{item.title}
								</Typography>
								<Typography variant="caption" sx={{ color: colors.inkFaint, display: "block" }}>
									{item.publishedAt ? new Date(item.publishedAt).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" }) : "Published"}
								</Typography>
								<Typography variant="caption" sx={{ color: colors.inkSoft, display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical", overflow: "hidden" }}>
									{item.body}
								</Typography>
							</Box>
							<ChevronRightIcon sx={{ color: colors.inkFaint, flexShrink: 0 }} />
						</ButtonBase>
					))}
				</Stack>
			)}
		</SectionCard>
	);
}

const MONTH_TINTS = ["violet", "orange", "blue", "pink", "green"] as const;

/** School calendar entries (holidays, PTMs, functions) for the next few weeks. */
export function UpcomingEventsCard() {
	const { hasPermission, isPathEnabled, permissionsLoaded } = useAuth();
	const allowed = permissionsLoaded && isPathEnabled("/academics") && hasPermission("SCHOOL_EVENT_VIEW");
	const [events, setEvents] = useState<SchoolEventResponse[] | null>(null);

	useEffect(() => {
		if (!allowed) return;
		const from = new Date();
		const to = new Date(from.getTime() + DAYS_AHEAD * 86_400_000);
		listSchoolEvents(isoDate(from), isoDate(to))
			.then((all) => setEvents([...all].sort((a, b) => a.date.localeCompare(b.date)).slice(0, 4)))
			.catch(() => setEvents([]));
	}, [allowed]);

	if (!allowed) return null;
	return (
		<SectionCard title="Upcoming events" icon={<EventIcon fontSize="small" />} tint="orange" actionLabel="View all" actionPath="/academics">
			{events === null ? (
				<EmptyNote>Loading…</EmptyNote>
			) : events.length === 0 ? (
				<EmptyNote>No events in the next {DAYS_AHEAD} days.</EmptyNote>
			) : (
				<Stack spacing={1.5}>
					{events.map((event, index) => {
						const date = new Date(`${event.date}T00:00:00`);
						const tint = MONTH_TINTS[index % MONTH_TINTS.length];
						return (
							<Stack key={event.id} direction="row" spacing={1.5} sx={{ alignItems: "center" }}>
								<Box
									sx={{
										width: 48,
										height: 52,
										flexShrink: 0,
										borderRadius: "12px",
										display: "flex",
										flexDirection: "column",
										alignItems: "center",
										justifyContent: "center",
										backgroundColor: {
											violet: colors.tintViolet,
											orange: colors.tintOrange,
											blue: colors.tintBlue,
											pink: colors.tintPink,
											green: colors.tintGreen,
										}[tint],
									}}
								>
									<Typography sx={{ fontSize: "1.1rem", fontWeight: 800, lineHeight: 1, color: colors.ink }}>{date.getDate()}</Typography>
									<Typography sx={{ fontSize: "0.65rem", fontWeight: 700, letterSpacing: "0.06em", color: colors.inkSoft }}>
										{date.toLocaleDateString("en-IN", { month: "short" }).toUpperCase()}
									</Typography>
								</Box>
								<Box sx={{ minWidth: 0 }}>
									<Typography variant="body2" noWrap sx={{ fontWeight: 700, color: colors.ink }}>
										{event.title}
									</Typography>
									<Typography variant="caption" noWrap sx={{ color: colors.inkSoft, display: "block" }}>
										{event.description ?? date.toLocaleDateString("en-IN", { weekday: "long" })}
									</Typography>
								</Box>
							</Stack>
						);
					})}
				</Stack>
			)}
		</SectionCard>
	);
}
