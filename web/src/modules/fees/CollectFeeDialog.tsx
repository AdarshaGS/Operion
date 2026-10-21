import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Dialog from "@mui/material/Dialog";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import IconButton from "@mui/material/IconButton";
import Stack from "@mui/material/Stack";
import CloseIcon from "@mui/icons-material/Close";
import { ApiError } from "../../api/client";
import { listStudentEnrollments, type StudentEnrollmentResponse } from "../../api/enrollments";
import { StudentPicker } from "../../components/StudentPicker";
import { StudentFeesPanel } from "./StudentFeesPanel";

export interface FeeTarget {
	studentEnrollmentId: number;
	academicYearId: number;
	schoolClassId: number;
}

interface Props {
	open: boolean;
	onClose: () => void;
	/** Set when opened from a ledger row; otherwise the dialog starts with a student search. */
	target?: FeeTarget | null;
}

/** Fee collection for one student - the same StudentFeesPanel the page used to host inline,
 * now opened over the overview from a ledger row or the "Collect Fee" button (which first
 * asks which student, so a student with no invoice yet can still be assigned fees). */
export function CollectFeeDialog({ open, onClose, target }: Props) {
	const [studentId, setStudentId] = useState<number | "">("");
	const [enrollment, setEnrollment] = useState<StudentEnrollmentResponse | null>(null);
	const [resolveError, setResolveError] = useState<string | null>(null);

	useEffect(() => {
		if (!open) {
			setStudentId("");
			setEnrollment(null);
			setResolveError(null);
		}
	}, [open]);

	useEffect(() => {
		setEnrollment(null);
		setResolveError(null);
		if (studentId === "") return;
		listStudentEnrollments(studentId)
			.then((enrollments) => {
				const current = enrollments.find((e) => e.current);
				if (!current) {
					setResolveError("This student has no current class enrollment yet — enroll them before assigning fees.");
					return;
				}
				setEnrollment(current);
			})
			.catch((err) => setResolveError(err instanceof ApiError ? err.message : "Failed to load student enrollment"));
	}, [studentId]);

	const panelTarget: FeeTarget | null = target ?? (enrollment ? { studentEnrollmentId: enrollment.id, academicYearId: enrollment.academicYearId, schoolClassId: enrollment.schoolClassId } : null);

	return (
		<Dialog open={open} onClose={onClose} fullWidth maxWidth="lg">
			<DialogTitle sx={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
				Collect fee
				<IconButton aria-label="Close" onClick={onClose} size="small">
					<CloseIcon />
				</IconButton>
			</DialogTitle>
			<DialogContent>
				<Stack spacing={2} sx={{ pt: 1 }}>
					{!target && (
						<Box sx={{ maxWidth: 420 }}>
							<StudentPicker value={studentId} onChange={(id) => setStudentId(id)} fullWidth />
						</Box>
					)}
					{resolveError && <Alert severity="info">{resolveError}</Alert>}
					{panelTarget && (
						<StudentFeesPanel
							studentEnrollmentId={panelTarget.studentEnrollmentId}
							academicYearId={panelTarget.academicYearId}
							schoolClassId={panelTarget.schoolClassId}
						/>
					)}
				</Stack>
			</DialogContent>
		</Dialog>
	);
}
