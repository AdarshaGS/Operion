import { useEffect, useState } from "react";
import Alert from "@mui/material/Alert";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import {
	CERTIFICATE_STATUS_LABELS,
	listCertificates,
	type CertificateRequestResponse,
	type CertificateRequestStatus,
} from "../../api/certificates";
import { CertificatesTable } from "./CertificatesTable";

const STATUSES = Object.keys(CERTIFICATE_STATUS_LABELS) as CertificateRequestStatus[];

/** The certificate register: every request and issued certificate across the school, filterable by status. */
export function CertificatesPage() {
	const [status, setStatus] = useState<CertificateRequestStatus | "">("");
	const [requests, setRequests] = useState<CertificateRequestResponse[]>([]);
	const [error, setError] = useState<string | null>(null);

	function refresh() {
		listCertificates({ status: status || undefined })
			.then((rows) => {
				setRequests(rows);
				setError(null);
			})
			.catch((err) => setError(err instanceof ApiError ? err.message : "Failed to load certificates"));
	}

	useEffect(refresh, [status]);

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ justifyContent: "space-between", alignItems: { sm: "center" } }}>
					<div>
						<Typography variant="h6">Certificate register</Typography>
						<Typography variant="body2" color="text.secondary">
							Requests waiting for approval or issue, and every certificate issued. Request new ones from the student's page.
						</Typography>
					</div>
					<TextField
						select
						label="Status"
						value={status}
						onChange={(e) => setStatus(e.target.value as CertificateRequestStatus | "")}
						sx={{ minWidth: 200 }}
						size="small"
					>
						<MenuItem value="">All</MenuItem>
						{STATUSES.map((value) => (
							<MenuItem key={value} value={value}>
								{CERTIFICATE_STATUS_LABELS[value]}
							</MenuItem>
						))}
					</TextField>
				</Stack>
				{error && <Alert severity="error">{error}</Alert>}
				{requests.length === 0 && !error && <Alert severity="info">Nothing here.</Alert>}
				{requests.length > 0 && <CertificatesTable requests={requests} showStudent onChanged={refresh} />}
			</Stack>
		</Paper>
	);
}
