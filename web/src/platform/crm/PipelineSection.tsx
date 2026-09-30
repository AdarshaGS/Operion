import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import Chip from "@mui/material/Chip";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip as RechartsTooltip, XAxis, YAxis } from "recharts";
import {
	completeTask,
	getCrmMetrics,
	LEAD_STAGES,
	listLeads,
	listOpenTasks,
	type CrmMetricsResponse,
	type CrmTaskResponse,
	type LeadResponse,
} from "../api/crm";
import { listOrganisations, type OrganisationResponse } from "../api/organisations";
import { PlatformApiError } from "../api/platformClient";
import { colors } from "../../theme";
import { formatDateTime, isDueToday, isOverdue, STAGE_LABEL } from "./crmFormat";

const FOLLOW_UPS_SHOWN = 8;

function Tile({ label, value, hint, onClick }: { label: string; value: string; hint?: string; onClick?: () => void }) {
	return (
		<Paper sx={{ p: 2.5, flex: "1 1 180px", cursor: onClick ? "pointer" : "default" }} onClick={onClick} variant="outlined">
			<Stack spacing={0.5}>
				<Typography variant="overline" color="text.secondary">
					{label}
				</Typography>
				<Typography variant="h4" component="div">
					{value}
				</Typography>
				{hint && (
					<Typography variant="caption" color="text.secondary">
						{hint}
					</Typography>
				)}
			</Stack>
		</Paper>
	);
}

/** Sales pipeline health (stage funnel, win rate) and the follow-ups that need attention,
 * for the platform dashboard. Loads its own data and fails on its own, so a CRM problem
 * never blanks the rest of the dashboard. */
export function PipelineSection() {
	const navigate = useNavigate();
	const [metrics, setMetrics] = useState<CrmMetricsResponse | null>(null);
	const [tasks, setTasks] = useState<CrmTaskResponse[] | null>(null);
	const [leads, setLeads] = useState<LeadResponse[]>([]);
	const [organisations, setOrganisations] = useState<OrganisationResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	function refresh() {
		Promise.all([getCrmMetrics(), listOpenTasks()])
			.then(([m, t]) => {
				setMetrics(m);
				setTasks(t);
			})
			.catch((err) => setError(err instanceof PlatformApiError ? err.message : "Failed to load sales pipeline"));
	}

	useEffect(() => {
		refresh();
		listLeads().then(setLeads).catch(() => {});
		listOrganisations().then(setOrganisations).catch(() => {});
	}, []);

	async function handleComplete(task: CrmTaskResponse) {
		try {
			await completeTask(task.id);
			refresh();
		} catch (err) {
			setError(err instanceof PlatformApiError ? err.message : "Failed to complete task");
		}
	}

	function subjectOf(task: CrmTaskResponse): { name: string; path: string } {
		if (task.leadId != null) {
			return { name: leads.find((l) => l.id === task.leadId)?.schoolName ?? `Lead #${task.leadId}`, path: `/platform/crm/leads/${task.leadId}` };
		}
		return {
			name: organisations.find((o) => o.id === task.organisationId)?.name ?? `Organisation #${task.organisationId}`,
			path: `/platform/organisations/${task.organisationId}`,
		};
	}

	if (error) return <Alert severity="error">{error}</Alert>;
	if (!metrics || !tasks) return null;

	const funnel = LEAD_STAGES.map((stage) => ({ stage: STAGE_LABEL[stage], count: metrics.leadsByStage[stage] ?? 0 }));
	const totalLeads = funnel.reduce((sum, row) => sum + row.count, 0);
	const openLeads = totalLeads - metrics.wonCount - metrics.lostCount;
	// Overdue first, then due today, then the soonest upcoming - the API already sorts by due date.
	const followUps = tasks.slice(0, FOLLOW_UPS_SHOWN);
	const needAttention = tasks.filter((t) => isOverdue(t) || isDueToday(t)).length;

	return (
		<Stack spacing={2}>
			<Typography variant="h6">Sales pipeline</Typography>

			<Stack direction="row" spacing={2} sx={{ flexWrap: "wrap" }}>
				<Tile label="Open leads" value={String(openLeads)} hint={`${totalLeads} total`} onClick={() => navigate("/platform/crm/leads")} />
				<Tile
					label="Win rate"
					value={metrics.winRate === null ? "—" : `${Math.round(metrics.winRate * 100)}%`}
					hint={`${metrics.wonCount} won · ${metrics.lostCount} lost`}
				/>
				<Tile label="Open follow-ups" value={String(metrics.openTaskCount)} hint={`${metrics.overdueTaskCount} overdue`} />
			</Stack>

			<Stack direction={{ xs: "column", md: "row" }} spacing={2} sx={{ alignItems: "stretch" }}>
				<Paper variant="outlined" sx={{ p: 2.5, flex: 1, minWidth: 0 }}>
					<Typography variant="h6">Stage funnel</Typography>
					<Typography variant="caption" color="text.secondary">
						Leads currently in each stage
					</Typography>
					<Box sx={{ height: 260, mt: 1 }}>
						<ResponsiveContainer width="100%" height="100%">
							<BarChart data={funnel} layout="vertical" margin={{ left: 16 }}>
								<CartesianGrid strokeDasharray="3 3" horizontal={false} />
								<XAxis type="number" allowDecimals={false} tickLine={false} axisLine={false} />
								<YAxis type="category" dataKey="stage" tickLine={false} axisLine={false} width={110} />
								<RechartsTooltip />
								<Bar dataKey="count" name="Leads" fill={colors.accent} radius={[0, 4, 4, 0]} />
							</BarChart>
						</ResponsiveContainer>
					</Box>
				</Paper>

				<Paper variant="outlined" sx={{ p: 2.5, flex: 1, minWidth: 0 }}>
					<Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center", mb: 1 }}>
						<Typography variant="h6">Follow-ups</Typography>
						<Chip label={needAttention} size="small" color={needAttention ? "error" : "default"} />
					</Stack>
					{followUps.length === 0 && (
						<Typography variant="body2" color="text.secondary">
							No open follow-ups.
						</Typography>
					)}
					<Stack spacing={0.5}>
						{followUps.map((task) => {
							const subject = subjectOf(task);
							return (
								<Stack key={task.id} direction="row" sx={{ alignItems: "center", gap: 0.5 }}>
									<Checkbox size="small" checked={false} onChange={() => handleComplete(task)} slotProps={{ input: { "aria-label": `Complete ${task.title}` } }} />
									<Box sx={{ flex: 1, minWidth: 0 }}>
										<Typography variant="body2">{task.title}</Typography>
										<Typography variant="caption" color={isOverdue(task) ? "error" : "text.secondary"}>
											{isOverdue(task) ? "Overdue · " : isDueToday(task) ? "Today · " : ""}
											{formatDateTime(task.dueAt)} · {subject.name}
											{task.assigneeName ? ` · ${task.assigneeName}` : ""}
										</Typography>
									</Box>
									<Button size="small" onClick={() => navigate(subject.path)}>
										View
									</Button>
								</Stack>
							);
						})}
					</Stack>
				</Paper>
			</Stack>
		</Stack>
	);
}
