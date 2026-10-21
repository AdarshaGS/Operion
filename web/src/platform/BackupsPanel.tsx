import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import { getBackupStatus, runBackupNow, type BackupStatus } from "./api/systemHealth";
import { PlatformApiError } from "./api/platformClient";

function formatBytes(bytes: number): string {
	if (bytes < 1024) return `${bytes} B`;
	if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
	if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
	return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

const when = (iso: string | null) => (iso ? new Date(iso).toLocaleString() : "never");

/** Local backups (BackupService): schedule, last result, the backups on disk and "Back up now". Restoring is a
 * script run with the app stopped (scripts/restore-backup.sh), so there is no restore button here. */
export function BackupsPanel({ onChange }: { onChange?: () => void }) {
	const [status, setStatus] = useState<BackupStatus | null>(null);
	const [error, setError] = useState<string | null>(null);
	const [running, setRunning] = useState(false);
	const [done, setDone] = useState<string | null>(null);

	function load() {
		getBackupStatus()
			.then(setStatus)
			.catch((err) => setError(err instanceof PlatformApiError ? err.message : "Failed to load backups"));
	}

	useEffect(load, []);

	async function handleRun() {
		setRunning(true);
		setError(null);
		setDone(null);
		try {
			const summary = await runBackupNow();
			setDone(`Backup ${summary.name} written (${formatBytes(summary.totalBytes)}).`);
		} catch (err) {
			setError(err instanceof PlatformApiError ? err.message : "Backup failed");
		} finally {
			setRunning(false);
			load();
			onChange?.();
		}
	}

	return (
		<Paper variant="outlined" sx={{ p: 2.5 }}>
			<Stack spacing={2}>
				<Stack direction="row" sx={{ justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 1 }}>
					<Typography variant="h6" component="h2">
						Backups
					</Typography>
					<Button variant="contained" size="small" disabled={running} onClick={handleRun}>
						{running ? "Backing up…" : "Back up now"}
					</Button>
				</Stack>

				{error && <Alert severity="error">{error}</Alert>}
				{done && <Alert severity="success">{done}</Alert>}

				{status && (
					<>
						{!status.scheduleEnabled && (
							<Alert severity="warning">
								Scheduled backups are off. Set BACKUP_ENABLED=true on the server so a backup is taken every night.
							</Alert>
						)}
						{status.lastError && <Alert severity="error">Last backup attempt failed: {status.lastError}</Alert>}
						<Stack direction="row" spacing={3} sx={{ flexWrap: "wrap" }}>
							<Stack spacing={0.5}>
								<Typography variant="overline" color="text.secondary">Status</Typography>
								<Chip size="small" label={status.healthy ? "Up to date" : "Overdue"} color={status.healthy ? "success" : "error"} />
							</Stack>
							<Stack spacing={0.5}>
								<Typography variant="overline" color="text.secondary">Last successful backup</Typography>
								<Typography variant="body2">{when(status.lastSuccessAt)}</Typography>
							</Stack>
							<Stack spacing={0.5}>
								<Typography variant="overline" color="text.secondary">Schedule</Typography>
								<Typography variant="body2">{status.scheduleEnabled ? `cron ${status.schedule}` : "off"} · keeps {status.keep}</Typography>
							</Stack>
							<Stack spacing={0.5}>
								<Typography variant="overline" color="text.secondary">Folder</Typography>
								<Typography variant="body2" sx={{ wordBreak: "break-all" }}>{status.directory}</Typography>
							</Stack>
						</Stack>

						{status.backups.length === 0 ? (
							<Alert severity="info">No backups yet.</Alert>
						) : (
							<Table size="small" aria-label="Backups on disk">
								<TableHead>
									<TableRow>
										<TableCell>Backup</TableCell>
										<TableCell>Taken</TableCell>
										<TableCell>Contents</TableCell>
										<TableCell align="right">Size</TableCell>
									</TableRow>
								</TableHead>
								<TableBody>
									{status.backups.map((backup) => (
										<TableRow key={backup.name}>
											<TableCell>{backup.name}</TableCell>
											<TableCell>{when(backup.createdAt)}</TableCell>
											<TableCell>{backup.files.map((f) => f.name).join(", ")}</TableCell>
											<TableCell align="right">{formatBytes(backup.totalBytes)}</TableCell>
										</TableRow>
									))}
								</TableBody>
							</Table>
						)}
					</>
				)}
			</Stack>
		</Paper>
	);
}
