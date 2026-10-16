import { useEffect, useMemo, useState, type FormEvent } from "react";
import Accordion from "@mui/material/Accordion";
import AccordionDetails from "@mui/material/AccordionDetails";
import AccordionSummary from "@mui/material/AccordionSummary";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import Chip from "@mui/material/Chip";
import { DialogErrorAlert } from "../../components/DialogErrorAlert";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormGroup from "@mui/material/FormGroup";
import IconButton from "@mui/material/IconButton";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import Tooltip from "@mui/material/Tooltip";
import Typography from "@mui/material/Typography";
import AddIcon from "@mui/icons-material/Add";
import UnfoldLessIcon from "@mui/icons-material/UnfoldLess";
import UnfoldMoreIcon from "@mui/icons-material/UnfoldMore";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import MoreVertIcon from "@mui/icons-material/MoreVert";
import { useAuth } from "../../auth/AuthContext";
import { Can } from "../../auth/Can";
import { ApiError } from "../../api/client";
import { listPermissions, type PermissionResponse } from "../../api/permissions";
import {
	changeRoleStatus,
	createRole,
	listRoles,
	updateRolePermissions,
	updateRolePortalFlag,
	type RoleResponse,
} from "../../api/roles";

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

	// Auto-expand only modules that already have a permission checked when the dialog
	// opens (63 permissions across 15 modules is too many to show fully expanded) -
	// captured once on mount, not re-derived as the user toggles checkboxes, so a
	// section doesn't jump shut/open under their cursor while they're working in it.
	const [expanded, setExpanded] = useState<Set<string>>(() => {
		const initial = new Set<string>();
		for (const [module, modulePermissions] of grouped) {
			if (modulePermissions.some((permission) => selected.has(permission.code))) {
				initial.add(module);
			}
		}
		return initial;
	});

	function toggleExpanded(module: string) {
		setExpanded((prev) => {
			const next = new Set(prev);
			if (next.has(module)) next.delete(module);
			else next.add(module);
			return next;
		});
	}

	const allExpanded = grouped.size > 0 && expanded.size === grouped.size;

	return (
		<Stack spacing={1}>
			<Box sx={{ display: "flex", justifyContent: "flex-end" }}>
				<Button
					size="small"
					startIcon={allExpanded ? <UnfoldLessIcon /> : <UnfoldMoreIcon />}
					onClick={() => setExpanded(allExpanded ? new Set() : new Set(grouped.keys()))}
				>
					{allExpanded ? "Collapse all" : "Expand all"}
				</Button>
			</Box>
			{[...grouped.entries()].map(([module, modulePermissions]) => {
				const selectedCount = modulePermissions.filter((permission) => selected.has(permission.code)).length;
				return (
					<Accordion
						key={module}
						expanded={expanded.has(module)}
						onChange={() => toggleExpanded(module)}
						disableGutters
						variant="outlined"
						sx={{ "&:before": { display: "none" } }}
					>
						<AccordionSummary expandIcon={<ExpandMoreIcon />}>
							<Box sx={{ display: "flex", alignItems: "center", gap: 1, width: "100%", pr: 1 }}>
								<Typography variant="subtitle2" sx={{ textTransform: "capitalize", flexGrow: 1 }}>
									{module}
								</Typography>
								{selectedCount > 0 && <Chip label={`${selectedCount} selected`} size="small" />}
							</Box>
						</AccordionSummary>
						<AccordionDetails>
							<FormGroup>
								{modulePermissions.map((permission) => (
									<FormControlLabel
										key={permission.code}
										control={
											<Checkbox checked={selected.has(permission.code)} onChange={() => onToggle(permission.code)} size="small" />
										}
										label={`${permission.code} — ${permission.description}`}
									/>
								))}
							</FormGroup>
						</AccordionDetails>
					</Accordion>
				);
			})}
		</Stack>
	);
}

export function RolesPanel() {
	const { profile } = useAuth();
	const ownRoleNames = useMemo(() => new Set(profile?.roleNames ?? []), [profile]);
	const [roles, setRoles] = useState<RoleResponse[]>([]);
	const [permissions, setPermissions] = useState<PermissionResponse[]>([]);
	const [error, setError] = useState<string | null>(null);
	// Shown inside whichever dialog is open - the page-level alert sits behind its backdrop.
	const [dialogError, setDialogError] = useState<string | null>(null);

	const [createOpen, setCreateOpen] = useState(false);
	const [name, setName] = useState("");
	const [description, setDescription] = useState("");
	const [createSelected, setCreateSelected] = useState<Set<string>>(new Set());
	const [createPortal, setCreatePortal] = useState(false);
	const [submitting, setSubmitting] = useState(false);

	const [editingRole, setEditingRole] = useState<RoleResponse | null>(null);
	const [editSelected, setEditSelected] = useState<Set<string>>(new Set());
	const [menu, setMenu] = useState<{ anchor: HTMLElement; role: RoleResponse } | null>(null);

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
		setDialogError(null);
		try {
			await createRole({ name, description, permissionCodes: [...createSelected], portal: createPortal });
			setName("");
			setDescription("");
			setCreateSelected(new Set());
			setCreatePortal(false);
			setCreateOpen(false);
			refresh();
		} catch (err) {
			setDialogError(err instanceof ApiError ? err.message : "Failed to create role");
		} finally {
			setSubmitting(false);
		}
	}

	function openEdit(role: RoleResponse) {
		setDialogError(null);
		setEditingRole(role);
		setEditSelected(new Set(role.permissionCodes));
	}

	async function handleSavePermissions() {
		if (!editingRole) return;
		setSubmitting(true);
		setDialogError(null);
		try {
			await updateRolePermissions(editingRole.id, [...editSelected]);
			setEditingRole(null);
			refresh();
		} catch (err) {
			setDialogError(err instanceof ApiError ? err.message : "Failed to update role permissions");
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

	async function togglePortal(role: RoleResponse) {
		try {
			await updateRolePortalFlag(role.id, !role.portal);
			refresh();
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to change role's portal flag");
		}
	}

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
					<Typography variant="h6">Roles</Typography>
					<Can anyOf={["ROLE_MANAGE"]}>
						<Button size="small" startIcon={<AddIcon />} onClick={() => { setDialogError(null); setCreateOpen(true); }}>
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
										{role.portal && <Chip label="portal" size="small" color="info" sx={{ ml: 1 }} />}
									</TableCell>
									<TableCell>{role.description}</TableCell>
									<TableCell>{role.permissionCodes.length}</TableCell>
									<TableCell>
										<Chip label={role.status} size="small" />
									</TableCell>
									<TableCell align="right">
										{ownRoleNames.has(role.name) ? (
											<Tooltip title="You hold this role, so you can't change it - ask another administrator">
												<Chip label="your role" size="small" variant="outlined" />
											</Tooltip>
										) : (
											<Can anyOf={["ROLE_MANAGE"]}>
												<IconButton size="small" aria-label="Role actions" onClick={(event) => setMenu({ anchor: event.currentTarget, role })}>
													<MoreVertIcon fontSize="small" />
												</IconButton>
											</Can>
										)}
									</TableCell>
								</TableRow>
							))}
						</TableBody>
					</Table>
				</TableContainer>
			</Stack>

			<Menu anchorEl={menu?.anchor} open={menu !== null} onClose={() => setMenu(null)}>
				<MenuItem
					onClick={() => {
						if (menu) openEdit(menu.role);
						setMenu(null);
					}}
				>
					Edit permissions
				</MenuItem>
				{menu && !menu.role.systemDefault && (
					<MenuItem
						onClick={() => {
							togglePortal(menu.role);
							setMenu(null);
						}}
					>
						{menu.role.portal ? "Unmark portal" : "Mark as portal role"}
					</MenuItem>
				)}
				{menu && !menu.role.systemDefault && (
					<MenuItem
						onClick={() => {
							toggleStatus(menu.role);
							setMenu(null);
						}}
						sx={{ color: menu.role.status === "ACTIVE" ? "error.main" : undefined }}
					>
						{menu.role.status === "ACTIVE" ? "Deactivate" : "Reactivate"}
					</MenuItem>
				)}
			</Menu>

			<Dialog open={createOpen} onClose={() => setCreateOpen(false)} component="form" onSubmit={handleCreate} fullWidth maxWidth="sm">
				<DialogTitle>Add role</DialogTitle>
				<DialogContent>
					<Stack spacing={2} sx={{ mt: 1 }}>
						{dialogError && <DialogErrorAlert>{dialogError}</DialogErrorAlert>}
						<TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} required autoFocus fullWidth />
						<TextField label="Description (optional)" value={description} onChange={(e) => setDescription(e.target.value)} fullWidth />
						<FormControlLabel
							control={<Checkbox checked={createPortal} onChange={(e) => setCreatePortal(e.target.checked)} />}
							label="Portal role — holders land in the self-service portal shell instead of the admin shell"
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
					{dialogError && <DialogErrorAlert>{dialogError}</DialogErrorAlert>}
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
