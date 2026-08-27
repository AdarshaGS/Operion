import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import { ApiError } from "../../api/client";
import { listPersons, type PersonResponse } from "../../api/persons";
import { listStaffProfiles, type StaffProfileResponse } from "../../api/staffProfiles";

export function StaffProfilesPanel() {
	const navigate = useNavigate();
	const [staff, setStaff] = useState<StaffProfileResponse[]>([]);
	const [personsById, setPersonsById] = useState<Map<number, PersonResponse>>(new Map());
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		listStaffProfiles()
			.then(setStaff)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load staff"));
		listPersons()
			.then((persons) => setPersonsById(new Map(persons.map((p) => [p.id, p]))))
			.catch(() => {});
	}, []);

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Staff</Typography>
					<Button size="small" startIcon={<AddIcon />} onClick={() => navigate("/hr/staff/new")}>
						Add staff
					</Button>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{staff.length === 0 && <Alert severity="info">No active staff yet.</Alert>}

				{staff.length > 0 && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Employee code</TableCell>
									<TableCell>Name</TableCell>
									<TableCell>Designation</TableCell>
									<TableCell>Employment type</TableCell>
									<TableCell>Status</TableCell>
								</TableRow>
							</TableHead>
							<TableBody>
								{staff.map((profile) => {
									const person = personsById.get(profile.personId);
									return (
										<TableRow
											key={profile.id}
											hover
											sx={{ cursor: "pointer" }}
											onClick={() => navigate(`/hr/staff/${profile.id}`)}
										>
											<TableCell>{profile.employeeCode}</TableCell>
											<TableCell>{person ? `${person.firstName} ${person.lastName}` : "—"}</TableCell>
											<TableCell>{profile.designationName}</TableCell>
											<TableCell>{profile.employmentType}</TableCell>
											<TableCell>
												<Chip label={profile.status} size="small" />
											</TableCell>
										</TableRow>
									);
								})}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>
		</Paper>
	);
}
