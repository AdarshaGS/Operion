import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { getAdmissionFeePreview, type AdmissionFeePreviewResponse } from "../../api/admission";
import { ModuleEnabled } from "../../auth/ModuleEnabled";

function currency(amount: number): string {
	return `₹${amount.toLocaleString("en-IN")}`;
}

function formatDate(iso: string): string {
	return new Date(`${iso}T00:00:00`).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
}

interface Props {
	/** Ids as held by the enrol forms' select fields - empty string until chosen. */
	academicYearId: string;
	sectionId: string;
}

/** What enrolling into the chosen year and section will charge up front, shown inside the enrol forms. Renders nothing while the Fees module is off, the admission-fee mode is OFF, or the year/section is not yet chosen. A failed preview is swallowed: it is advice, and must never stop the enrolment from being saved. */
export function AdmissionFeePreview(props: Props) {
	return (
		<ModuleEnabled module="FEES">
			<AdmissionFeePreviewBody {...props} />
		</ModuleEnabled>
	);
}

function AdmissionFeePreviewBody({ academicYearId, sectionId }: Props) {
	const [preview, setPreview] = useState<AdmissionFeePreviewResponse | null>(null);

	useEffect(() => {
		setPreview(null);
		if (!academicYearId || !sectionId) return;
		let cancelled = false;
		getAdmissionFeePreview(Number(academicYearId), Number(sectionId))
			.then((response) => {
				if (!cancelled) setPreview(response);
			})
			.catch(() => {});
		return () => {
			cancelled = true;
		};
	}, [academicYearId, sectionId]);

	if (!preview || preview.mode === "OFF") return null;

	if (preview.structures.length === 0) {
		return (
			<Alert severity="warning">
				No fee is marked 'collect at admission' for this class - the student will be enrolled with no admission fee.
			</Alert>
		);
	}

	return (
		<Alert severity="info">
			<Typography variant="subtitle2">Fees due at admission</Typography>
			<Box component="ul" sx={{ m: 0, pl: 2.5 }}>
				{preview.structures.map((line) => (
					<li key={line.feeStructureId}>
						{line.categoryName} - {currency(line.firstInstallment.amount)} (instalment {line.firstInstallment.number}, due{" "}
						{formatDate(line.firstInstallment.dueDate)})
					</li>
				))}
			</Box>
			<Typography variant="body2" sx={{ fontWeight: 600, mt: 0.5 }}>
				Total: {currency(preview.admissionTotal)}
			</Typography>
		</Alert>
	);
}
