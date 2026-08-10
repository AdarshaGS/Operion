import { useEffect, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControlLabel from "@mui/material/FormControlLabel";
import IconButton from "@mui/material/IconButton";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import DeleteIcon from "@mui/icons-material/Delete";
import { ApiError } from "../../api/client";
import { createGradingScale, listGradingScales, type GradingBandEntry, type GradingScaleResponse } from "../../api/gradingScales";

const EMPTY_BAND: GradingBandEntry = { grade: "", minPercentage: 0, remark: "" };

export function GradingScalesPanel() {
	const [scales, setScales] = useState<GradingScaleResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	const [dialogOpen, setDialogOpen] = useState(false);
	const [name, setName] = useState("");
	const [defaultScale, setDefaultScale] = useState(false);
	const [bands, setBands] = useState<GradingBandEntry[]>([{ ...EMPTY_BAND }]);
	const [submitting, setSubmitting] = useState(false);

	function refresh() {
		listGradingScales()
			.then(setScales)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load grading scales"));
	}

	useEffect(refresh, []);

	function openDialog() {
		setName("");
		setDefaultScale(false);
		setBands([{ ...EMPTY_BAND }]);
		setDialogOpen(true);
	}

	function updateBand(index: number, patch: Partial<GradingBandEntry>) {
		setBands((rows) => rows.map((row, i) => (i === index ? { ...row, ...patch } : row)));
	}

	function addBand() {
		setBands((rows) => [...rows, { ...EMPTY_BAND }]);
	}

	function removeBand(index: number) {
		setBands((rows) => rows.filter((_, i) => i !== index));
	}

	async function handleSubmit(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createGradingScale({ name, defaultScale, bands });
			setDialogOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create grading scale");
		} finally {
			setSubmitting(false);
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Grading scales</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={openDialog}>
						Add grading scale
					</Button>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				<TableContainer>
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Name</TableCell>
								<TableCell>Default</TableCell>
								<TableCell>Bands</TableCell>
							</TableRow>
						</TableHead>
						<TableBody>
							{scales.map((scale) => (
								<TableRow key={scale.id}>
									<TableCell>{scale.name}</TableCell>
									<TableCell>{scale.defaultScale ? "Yes" : "No"}</TableCell>
									<TableCell>{scale.bands.map((band) => `${band.grade} (${band.minPercentage}%+)`).join(", ")}</TableCell>
								</TableRow>
							))}
						</TableBody>
					</Table>
				</TableContainer>
			</Stack>

			<Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} component="form" onSubmit={handleSubmit} fullWidth maxWidth="sm">
				<DialogTitle>Add grading scale</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField label="Name" placeholder="CBSE Standard" value={name} onChange={(e) => setName(e.target.value)} required fullWidth />
						<FormControlLabel
							control={<Checkbox checked={defaultScale} onChange={(e) => setDefaultScale(e.target.checked)} />}
							label="Default scale"
						/>

						<Typography variant="subtitle2">
							Bands — each stores only a minimum percentage floor; resolution picks the highest band a score meets or exceeds
						</Typography>
						{bands.map((band, index) => (
							<Box key={index} sx={{ display: "flex", gap: 1, alignItems: "center" }}>
								<TextField label="Grade" value={band.grade} onChange={(e) => updateBand(index, { grade: e.target.value })} sx={{ width: 90 }} />
								<TextField
									label="Min %"
									type="number"
									value={band.minPercentage}
									onChange={(e) => updateBand(index, { minPercentage: Number(e.target.value) })}
									sx={{ width: 100 }}
								/>
								<TextField
									label="Remark"
									value={band.remark ?? ""}
									onChange={(e) => updateBand(index, { remark: e.target.value })}
									sx={{ flex: 1 }}
								/>
								<IconButton onClick={() => removeBand(index)} disabled={bands.length === 1}>
									<DeleteIcon fontSize="small" />
								</IconButton>
							</Box>
						))}
						<Button size="small" startIcon={<AddIcon />} onClick={addBand} sx={{ alignSelf: "flex-start" }}>
							Add band
						</Button>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setDialogOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Add
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
