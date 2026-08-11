import { useEffect, useMemo, useState, type FormEvent } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import Chip from "@mui/material/Chip";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormGroup from "@mui/material/FormGroup";
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
import { Can } from "../../auth/Can";
import { ApiError } from "../../api/client";
import { listPermissions, type PermissionResponse } from "../../api/permissions";
import { changeRoleStatus, createRole, listRoles, updateRolePermissions, type RoleResponse } from "../../api/roles";

function groupByModule(permissions: PermissionResponse[]): Map<string, PermissionResponse[]> {
	const groups = new Map<string, PermissionResponse[]>();
	for (const permission of permissions) {
		const existing = groups.get(permission.module) ?? [];
		existing.push(permission);
		groups.set(permission.module, existing);
	}
	return groups;
}

function PermissionCheckboxes({
	permissions,
	selected,
	onToggle,
}: {
	permissions: PermissionResponse[];
	selected: Set<string>;
	onToggle: (code: string) => void;
}) {
	const grouped = useMemo(() => groupByModule(permissions), [permissions]);

	return (
		<Stack spacing={1.5}>
			{[...grouped.entries()].map(([module, modulePermissions]) => (
				<Box key={module}>
					<Typography variant="subtitle2" sx={{ textTransform: "capitalize" }}>
						{module}
					</Typography>
					<FormGroup>
						{modulePermissions.map((permission) => (
							<FormControlLabel
								key={permission.code}
								control={<Checkbox checked={selected.has(permission.code)} onChange={() => onToggle(permission.code)} size="small" />}
								label={`${permission.code} — ${permission.description}`}
							/>
						))}
					</FormGroup>
				</Box>
			))}
		</Stack>
	);
}

export function RolesPanel() {
	const [roles, setRoles] = useState<RoleResponse[]>([]);
	const [permissions, setPermissions] = useState<PermissionResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	const [createOpen, setCreateOpen] = useState(false);
	const [name, setName] = useState("");
	const [description, setDescription] = useState("");
	const [createSelected, setCreateSelected] = useState<Set<string>>(new Set());
	const [submitting, setSubmitting] = useState(false);

	const [editingRole, setEditingRole] = useState<RoleResponse | null>(null);
	const [editSelected, setEditSelected] = useState<Set<string>>(new Set());

	function refresh() {
		listRoles()
			.then(setRoles)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load roles"));
	}

	useEffect(() => {
		refresh();
		listPermissions()
			.then(setPermissions)
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load permissions"));
	}, []);

	function toggle(set: Set<string>, setSet: (s: Set<string>) => void, code: string) {
		const next = new Set(set);
		if (next.has(code)) next.delete(code);
		else next.add(code);
		setSet(next);
	}

	async function handleCreate(event: FormEvent) {
		event.preventDefault();
		setSubmitting(true);
		try {
			await createRole({ name, description, permissionCodes: [...createSelected] });
			setName("");
			setDescription("");
			setCreateSelected(new Set());
			setCreateOpen(false);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to create role");
		} finally {
			setSubmitting(false);
		}
	}

	function openEdit(role: RoleResponse) {
		setEditingRole(role);
		setEditSelected(new Set(role.permissionCodes));
	}

	async function handleSavePermissions() {
		if (!editingRole) return;
		setSubmitting(true);
		try {
			await updateRolePermissions(editingRole.id, [...editSelected]);
			setEditingRole(null);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to update role permissions");
		} finally {
			setSubmitting(false);
		}
	}

	async function toggleStatus(role: RoleResponse) {
		try {
			await changeRoleStatus(role.id, role.status === "ACTIVE" ? "INACTIVE" : "ACTIVE");
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to change role status");
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Roles</Typography>
					<Can anyOf={["ROLE_MANAGE"]}>
						<Button size="small" startIcon={<AddIcon />} onClick={() => setCreateOpen(true)}>
							Add role
						</Button>
					</Can>
				</Box>

				{error && <Alert severity="error">{error}</Alert>}

				<TableContainer>
					<Table size="small">
						<TableHead>
							<TableRow>
								<TableCell>Name</TableCell>
								<TableCell>Description</TableCell>
								<TableCell>Permissions</TableCell>
								<TableCell>Status</TableCell>
								<TableCell align="right">Actions</TableCell>
							</TableRow>
						</TableHead>
						<TableBody>
							{roles.map((role) => (
								<TableRow key={role.id}>
									<TableCell>
										{role.name}
										{role.systemDefault && <Chip label="system" size="small" sx={{ ml: 1 }} />}
									</TableCell>
									<TableCell>{role.description}</TableCell>
									<TableCell>{role.permissionCodes.length}</TableCell>
									<TableCell>
										<Chip label={role.status} size="small" />
									</TableCell>
									<TableCell align="right">
										<Can anyOf={["ROLE_MANAGE"]}>
											<Button size="small" onClick={() => openEdit(role)}>
												Edit permissions
											</Button>
											{!role.systemDefault && (
												<Button size="small" onClick={() => toggleStatus(role)}>
													{role.status === "ACTIVE" ? "Deactivate" : "Reactivate"}
												</Button>
											)}
										</Can>
									</TableCell>
								</TableRow>
							))}
						</TableBody>
					</Table>
				</TableContainer>
			</Stack>

			<Dialog open={createOpen} onClose={() => setCreateOpen(false)} component="form" onSubmit={handleCreate} fullWidth maxWidth="sm">
				<DialogTitle>Add role</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						<TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} required autoFocus fullWidth />
						<TextField
							label="Description"
							value={description}
							onChange={(e) => setDescription(e.target.value)}
							required
							fullWidth
						/>
						<PermissionCheckboxes
							permissions={permissions}
							selected={createSelected}
							onToggle={(code) => toggle(createSelected, setCreateSelected, code)}
						/>
					</Stack>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setCreateOpen(false)}>Cancel</Button>
					<Button type="submit" variant="contained" disabled={submitting}>
						Add
					</Button>
				</DialogActions>
			</Dialog>

			<Dialog open={editingRole !== null} onClose={() => setEditingRole(null)} fullWidth maxWidth="sm">
				<DialogTitle>Edit permissions — {editingRole?.name}</DialogTitle>
				<DialogContent>
					<PermissionCheckboxes
						permissions={permissions}
						selected={editSelected}
						onToggle={(code) => toggle(editSelected, setEditSelected, code)}
					/>
				</DialogContent>
				<DialogActions>
					<Button onClick={() => setEditingRole(null)}>Cancel</Button>
					<Button variant="contained" disabled={submitting} onClick={handleSavePermissions}>
						Save
					</Button>
				</DialogActions>
			</Dialog>
		</Paper>
	);
}
