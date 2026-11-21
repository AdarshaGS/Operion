import { useEffect, useMemo, useState } from "react";
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
import { Can } from "../../auth/Can";
import { loadErrorMessage } from "./loadError";
import { listMemberships, type MembershipResponse } from "../../api/memberships";
import { listPersons, type PersonResponse } from "../../api/persons";
import { listStaffProfiles, type StaffProfileResponse } from "../../api/staffProfiles";

export function StaffProfilesPanel() {
	const navigate = useNavigate();
	const [staff, setStaff] = useState<StaffProfileResponse[]>([]);
	const [personsById, setPersonsById] = useState<Map<number, PersonResponse>>(new Map());
	const [memberships, setMemberships] = useState<MembershipResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		listStaffProfiles()
			.then(setStaff)
			.catch((err) => setError(loadErrorMessage(err, "Failed to load staff")));
		listPersons()
			.then((persons) => setPersonsById(new Map(persons.map((p) => [p.id, p]))))
			.catch(() => {});
		// Members without an HR profile still belong in the staff list; a role that can't view members just sees profiles only.
		listMemberships()
			.then(setMemberships)
			.catch(() => {});
	}, []);

	// One row per member (not per role) who has no StaffProfile yet; they open on the member page.
	const membersWithoutProfile = useMemo(() => {
		const profiled = new Set(staff.map((profile) => profile.personId));
		const byUser = new Map<number, MembershipResponse[]>();
		for (const membership of memberships) {
			if (profiled.has(membership.personId) || membership.memberStatus === "INACTIVE") continue;
			byUser.set(membership.userId, [...(byUser.get(membership.userId) ?? []), membership]);
		}
		return Array.from(byUser.values()).map((rows) => ({
			userId: rows[0].userId,
			name: rows[0].personName,
			memberId: rows.find((row) => row.memberId)?.memberId ?? null,
			roles: Array.from(new Set(rows.map((row) => row.roleName))).join(", "),
			memberStatus: rows[0].memberStatus,
		}));
	}, [memberships, staff]);

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Staff</Typography>
					<Can anyOf={["HR_STAFF_MANAGE"]}>
						<Button size="small" startIcon={<AddIcon />} onClick={() => navigate("/hr/staff/new")}>
							Add staff
						</Button>
					</Can>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				{staff.length === 0 && membersWithoutProfile.length === 0 && !error && <Alert severity="info">No active staff yet.</Alert>}

				{(staff.length > 0 || membersWithoutProfile.length > 0) && (
					<TableContainer>
						<Table size="small">
							<TableHead>
								<TableRow>
									<TableCell>Staff ID</TableCell>
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
								{membersWithoutProfile.map((member) => (
									<TableRow key={`member-${member.userId}`} hover sx={{ cursor: "pointer" }} onClick={() => navigate(`/members/${member.userId}`)}>
										<TableCell>{member.memberId ?? "—"}</TableCell>
										<TableCell>{member.name}</TableCell>
										<TableCell>{member.roles}</TableCell>
										<TableCell>—</TableCell>
										<TableCell>
											<Chip label={member.memberStatus === "INVITED" ? "INVITED" : "NO HR PROFILE"} size="small" variant="outlined" />
										</TableCell>
									</TableRow>
								))}
							</TableBody>
						</Table>
					</TableContainer>
				)}
			</Stack>
		</Paper>
	);
}
