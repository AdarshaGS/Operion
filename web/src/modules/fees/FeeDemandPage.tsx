import { useEffect, useMemo, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import Chip from "@mui/material/Chip";
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
import Typography from "@mui/material/Typography";
import NotificationsActiveIcon from "@mui/icons-material/NotificationsActive";
import { Can } from "../../auth/Can";
import { useCan } from "../../auth/useCan";
import { ApiError } from "../../api/client";
import { listOverdueInvoices, sendReminders, type OverdueInvoiceResponse } from "../../api/invoices";
import { type PersonResponse, listPersons } from "../../api/persons";
import { listSchoolClasses, type SchoolClassResponse } from "../../api/schoolClasses";
import { listStudents, type StudentResponse } from "../../api/students";
import { useCampusFiltered, useSelectedCampusId } from "../../layout/selectedCampus";
import { BulkInvoicePanel } from "./BulkInvoicePanel";
import { useSchoolClassLabel } from "../../utils/useSchoolClassLabel";

function currency(amount: number): string {
	return `₹${amount.toLocaleString("en-IN")}`;
}

/** Demand management (#237) - overdue invoices, filterable by class, with a bulk "Send reminders"
 * action through the existing notification pipeline. Limited to the campus picked in the top bar
 * (every campus under "All campuses"): the class filter lists that campus's classes and only its
 * invoices are listed, selectable and reminded. */
export function FeeDemandPage() {
	const [classes, setClasses] = useState<SchoolClassResponse[]>([]);
	const schoolClassLabel = useSchoolClassLabel();
	// The row checkboxes only feed "Send reminders", so they go with that permission.
	const canRemind = useCan("FEE_REMINDER_SEND");
	const selectedCampusId = useSelectedCampusId();
	const campusClasses = useCampusFiltered(classes);
	const [students, setStudents] = useState<StudentResponse[]>([]);
	const [persons, setPersons] = useState<PersonResponse[]>([]);
	const [schoolClassId, setSchoolClassId] = useState("");
	const [invoices, setInvoices] = useState<OverdueInvoiceResponse[]>([]);
	const [selected, setSelected] = useState<Set<number>>(new Set());
	const [error, setError] = useState<string | null>(null);
	const [notice, setNotice] = useState<string | null>(null);
	const [sending, setSending] = useState(false);

	useEffect(() => {
		listSchoolClasses().then(setClasses).catch(() => {});
		listStudents().then(setStudents).catch(() => {});
		listPersons().then(setPersons).catch(() => {});
	}, []);

	function refresh() {
		setNotice(null);
		listOverdueInvoices(schoolClassId ? { schoolClassId: Number(schoolClassId) } : {})
			.then((rows) => {
				setInvoices(rows);
				setSelected(new Set());
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load overdue invoices"));
	}

	useEffect(refresh, [schoolClassId]);

	// A class filter picked under another campus is dropped when the campus changes.
	useEffect(() => {
		if (schoolClassId && !campusClasses.some((c) => String(c.id) === String(schoolClassId))) setSchoolClassId("");
	}, [campusClasses, schoolClassId]);

	// With "All classes" the server returns every campus's overdue invoices, so keep only the picked campus's.
	const visibleInvoices = useMemo(() => {
		if (selectedCampusId === null) return invoices;
		const classIds = new Set(campusClasses.map((c) => c.id));
		return invoices.filter((invoice) => classIds.has(invoice.schoolClassId));
	}, [invoices, campusClasses, selectedCampusId]);

	// Reminders go only to invoices on screen: a selection made under another campus is cleared on a switch.
	useEffect(() => {
		setSelected(new Set());
	}, [selectedCampusId]);

	function classLabel(id: number): string {
		const schoolClass = classes.find((c) => c.id === id);
		return schoolClass ? schoolClassLabel(schoolClass) : `Class #${id}`;
	}

	function studentLabel(studentId: number): string {
		const student = students.find((s) => s.id === studentId);
		if (!student) return `Student #${studentId}`;
		const person = persons.find((p) => p.id === student.personId);
		return person ? `${person.firstName} ${person.lastName} (${student.admissionNumber})` : student.admissionNumber;
	}

	function toggle(invoiceId: number, checked: boolean) {
		setSelected((prev) => {
			const next = new Set(prev);
			if (checked) next.add(invoiceId);
			else next.delete(invoiceId);
			return next;
		});
	}

	function toggleAll(checked: boolean) {
		setSelected(checked ? new Set(visibleInvoices.map((i) => i.id)) : new Set());
	}

	async function handleSendReminders() {
		const visibleIds = new Set(visibleInvoices.map((i) => i.id));
		const invoiceIds = Array.from(selected).filter((id) => visibleIds.has(id));
		if (invoiceIds.length === 0) return;
		setSending(true);
		setError(null);
		try {
			const results = await sendReminders(invoiceIds);
			const notified = results.reduce((sum, r) => sum + r.recipientsNotified, 0);
			setNotice(`Sent ${notified} reminder${notified === 1 ? "" : "s"} across ${results.length} invoice${results.length === 1 ? "" : "s"}.`);
			setSelected(new Set());
		} catch (err) {
			setError(err instanceof ApiError ? err.message : "Failed to send reminders");
		} finally {
			setSending(false);
		}
	}

	return (
		<Stack spacing={3}>
			<Can anyOf={["FEE_INVOICE_MANAGE"]}>
				<BulkInvoicePanel classes={classes} onGenerated={refresh} />
			</Can>
			<Paper sx={{ p: 3 }}>
				<Stack spacing={2}>
					<Typography variant="h6">Overdue invoices</Typography>

					<TextField select label="Class" value={schoolClassId} onChange={(e) => setSchoolClassId(e.target.value)} sx={{ maxWidth: 300 }}>
						<MenuItem value="">All classes</MenuItem>
						{campusClasses.map((schoolClass) => (
							<MenuItem key={schoolClass.id} value={schoolClass.id}>
								{schoolClassLabel(schoolClass)}
							</MenuItem>
						))}
					</TextField>
					{selectedCampusId !== null && classes.length > 0 && campusClasses.length === 0 && (
						<Alert severity="info">No classes in the selected campus.</Alert>
					)}

					{error && <Alert severity="error">{error}</Alert>}
					{notice && <Alert severity="success">{notice}</Alert>}

					{visibleInvoices.length === 0 ? (
						<Alert severity="info">
							No overdue invoices{schoolClassId ? " for this class" : selectedCampusId !== null ? " in the selected campus" : ""}.
						</Alert>
					) : (
						<>
							<Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
								<Typography variant="body2" color="text.secondary">
									{visibleInvoices.length} overdue invoice{visibleInvoices.length === 1 ? "" : "s"}
								</Typography>
								<Can anyOf={["FEE_REMINDER_SEND"]}>
									<Button
										size="small"
										variant="contained"
										startIcon={<NotificationsActiveIcon />}
										disabled={selected.size === 0 || sending}
										onClick={handleSendReminders}
									>
										Send reminders ({selected.size})
									</Button>
								</Can>
							</Box>

							<TableContainer>
								<Table size="small">
									<TableHead>
										<TableRow>
											{canRemind && (
												<TableCell padding="checkbox">
													<Checkbox
														checked={selected.size === visibleInvoices.length}
														indeterminate={selected.size > 0 && selected.size < visibleInvoices.length}
														onChange={(e) => toggleAll(e.target.checked)}
													/>
												</TableCell>
											)}
											<TableCell>Invoice #</TableCell>
											<TableCell>Student</TableCell>
											<TableCell>Class</TableCell>
											<TableCell>Due date</TableCell>
											<TableCell>Outstanding</TableCell>
											<TableCell>Overdue by</TableCell>
										</TableRow>
									</TableHead>
									<TableBody>
										{visibleInvoices.map((invoice) => (
											<TableRow key={invoice.id} hover selected={selected.has(invoice.id)}>
												{canRemind && (
													<TableCell padding="checkbox">
														<Checkbox checked={selected.has(invoice.id)} onChange={(e) => toggle(invoice.id, e.target.checked)} />
													</TableCell>
												)}
												<TableCell>{invoice.invoiceNumber}</TableCell>
												<TableCell>{studentLabel(invoice.studentId)}</TableCell>
												<TableCell>{classLabel(invoice.schoolClassId)}</TableCell>
												<TableCell>{invoice.dueDate}</TableCell>
												<TableCell>{currency(invoice.outstanding)}</TableCell>
												<TableCell>
													<Chip label={`${invoice.daysOverdue} day${invoice.daysOverdue === 1 ? "" : "s"}`} size="small" color="warning" />
												</TableCell>
											</TableRow>
										))}
									</TableBody>
								</Table>
							</TableContainer>
						</>
					)}
				</Stack>
			</Paper>
		</Stack>
	);
}
