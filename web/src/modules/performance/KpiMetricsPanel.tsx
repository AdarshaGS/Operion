import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import FormControlLabel from "@mui/material/FormControlLabel";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Switch from "@mui/material/Switch";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import { configureKpiMetric, getKpiSettings, listKpiMetrics, updateKpiSettings, type KpiMetricConfig } from "../../api/performance";

/** Which built-in metrics this organisation tracks, how much each weighs, and what earns full marks. Nothing is on until it is switched on here. */
export function KpiMetricsPanel() {
	const [metrics, setMetrics] = useState<KpiMetricConfig[]>([]);
	const [selfView, setSelfView] = useState(false);
	const [error, setError] = useState<string | null>(null);
	const [saved, setSaved] = useState<string | null>(null);

	useEffect(() => {
		listKpiMetrics()
			.then(setMetrics)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load the metrics"));
		getKpiSettings()
			.then((s) => setSelfView(s.teacherSelfView))
			.catch(() => setSelfView(false));
	}, []);

	function edit(metric: string, patch: Partial<KpiMetricConfig>) {
		setSaved(null);
		setMetrics((current) => current.map((m) => (m.metric === metric ? { ...m, ...patch } : m)));
	}

	function save(metric: KpiMetricConfig) {
		setError(null);
		setSaved(null);
		configureKpiMetric(metric.metric, { enabled: metric.enabled, weight: metric.weight, target: metric.target, days: metric.usesDays ? metric.days : null })
			.then((updated) => {
				setMetrics((current) => current.map((m) => (m.metric === updated.metric ? updated : m)));
				setSaved(`${updated.label} saved`);
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to save the metric"));
	}

	function toggleSelfView(checked: boolean) {
		setError(null);
		updateKpiSettings(checked)
			.then((s) => setSelfView(s.teacherSelfView))
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to save the setting"));
	}

	return (
		<Stack spacing={2}>
			{error && <Alert severity="error">{error}</Alert>}
			{saved && <Alert severity="success">{saved}</Alert>}
			<FormControlLabel
				control={<Switch checked={selfView} onChange={(e) => toggleSelfView(e.target.checked)} />}
				label="Teachers can see their own scorecard and appraisal"
			/>
			<Paper variant="outlined" sx={{ overflowX: "auto" }}>
				<Table size="small">
					<TableHead>
						<TableRow>
							<TableCell>Metric</TableCell>
							<TableCell align="center">Tracked</TableCell>
							<TableCell align="right">Weight (1-100)</TableCell>
							<TableCell align="right">Target</TableCell>
							<TableCell align="right">Days allowed</TableCell>
							<TableCell />
						</TableRow>
					</TableHead>
					<TableBody>
						{metrics.map((m) => (
							<TableRow key={m.metric}>
								<TableCell>
									<Typography variant="body2">{m.label}</Typography>
									<Typography variant="caption" color="text.secondary">{m.description}</Typography>
								</TableCell>
								<TableCell align="center">
									<Switch checked={m.enabled} onChange={(e) => edit(m.metric, { enabled: e.target.checked })} />
								</TableCell>
								<TableCell align="right">
									<TextField size="small" type="number" value={m.weight} sx={{ width: 90 }}
										onChange={(e) => edit(m.metric, { weight: Number(e.target.value) })} />
								</TableCell>
								<TableCell align="right">
									{m.kind === "OUTCOME" ? (
										<Typography variant="caption" color="text.secondary">vs previous period</Typography>
									) : (
										<TextField size="small" type="number" value={m.target} sx={{ width: 100 }}
											helperText={m.unit === "PERCENT" ? "%" : "per 30 days"}
											onChange={(e) => edit(m.metric, { target: Number(e.target.value) })} />
									)}
								</TableCell>
								<TableCell align="right">
									{m.usesDays && (
										<TextField size="small" type="number" value={m.days ?? ""} sx={{ width: 90 }}
											onChange={(e) => edit(m.metric, { days: e.target.value === "" ? null : Number(e.target.value) })} />
									)}
								</TableCell>
								<TableCell align="right">
									<Button size="small" onClick={() => save(m)}>Save</Button>
								</TableCell>
							</TableRow>
						))}
					</TableBody>
				</Table>
			</Paper>
		</Stack>
	);
}
