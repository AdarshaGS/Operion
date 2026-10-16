import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import CancelIcon from "@mui/icons-material/Cancel";
import WarningAmberIcon from "@mui/icons-material/WarningAmber";
import { ApiError } from "../../api/client";
import { getGoLiveReport, type GoLiveGroup, type GoLiveReport, type ReadinessLevel } from "../../api/goLive";
import { colors } from "../../theme";

const STAGE_LABEL = { CONFIGURATION: "Configuration", DATA: "Data", ACCESS: "Access" } as const;

function LevelIcon({ level }: { level: ReadinessLevel }) {
	if (level === "PASS") {
		return <CheckCircleIcon fontSize="small" sx={{ color: colors.ok }} />;
	}
	return level === "WARN" ? (
		<WarningAmberIcon fontSize="small" sx={{ color: colors.warn }} />
	) : (
		<CancelIcon fontSize="small" sx={{ color: colors.bad }} />
	);
}

function GroupCard({ group }: { group: GoLiveGroup }) {
	const navigate = useNavigate();
	return (
		<Paper sx={{ p: 2.5 }}>
			<Stack spacing={1}>
				<Stack direction="row" sx={{ alignItems: "center", justifyContent: "space-between" }}>
					<Typography variant="subtitle1" sx={{ color: colors.ink }}>
						{group.label}
					</Typography>
					{group.hasChecks ? <LevelIcon level={group.level} /> : <Chip size="small" variant="outlined" label="Not checked yet" />}
				</Stack>
				{group.findings.map((finding) => (
					<Box
						key={finding.message}
						sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 1.5, py: 0.75, borderTop: `1px solid ${colors.rule}` }}
					>
						<Stack direction="row" spacing={1.25} sx={{ alignItems: "center" }}>
							<LevelIcon level={finding.level} />
							<Typography variant="body2" sx={{ color: colors.ink }}>
								{finding.message}
							</Typography>
							<Typography variant="caption" sx={{ color: colors.inkFaint }}>
								{STAGE_LABEL[finding.stage]}
							</Typography>
						</Stack>
						{finding.fixPath && finding.level !== "PASS" && (
							<Button size="small" onClick={() => navigate(finding.fixPath as string)}>
								Fix
							</Button>
						)}
					</Box>
				))}
			</Stack>
		</Paper>
	);
}

/** Final check before go-live: for every module that is switched on, is it configured, does it
 * hold data, can someone use it. Guidance, not a gate - nothing is blocked by a failing row.
 * Modules switched off for the school are not checked, only named at the bottom. */
export function GoLiveCheckPage() {
	const navigate = useNavigate();
	const [report, setReport] = useState<GoLiveReport | null>(null);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		getGoLiveReport()
			.then(setReport)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to run the go-live check"));
	}, []);

	return (
		<Stack spacing={3}>
			<Box>
				<Typography variant="h4" sx={{ color: colors.ink }}>
					Go-live check
				</Typography>
				<Typography variant="body1" sx={{ color: colors.inkSoft, mt: 0.5 }}>
					For each module that is switched on: is it configured, is the data in, and can people use it.
				</Typography>
			</Box>

			{error && <Alert severity="error">{error}</Alert>}

			{report && (
				<Alert severity={report.ready ? "success" : "warning"}>
					{report.ready
						? "Nothing is blocking go-live. Anything marked with a warning is worth a look but not required."
						: "Some required setup is still missing - the items marked with an error below need attention first."}
				</Alert>
			)}

			{report?.groups.map((group) => (
				<GroupCard key={group.code} group={group} />
			))}

			{report && report.hiddenModules.length > 0 && (
				<Typography variant="body2" sx={{ color: colors.inkSoft }}>
					Not checked because they are switched off for your school: {report.hiddenModules.join(", ")}.
				</Typography>
			)}

			<Box>
				<Button onClick={() => navigate("/setup/review")} sx={{ color: colors.inkSoft }}>
					Back to school setup review
				</Button>
			</Box>
		</Stack>
	);
}
