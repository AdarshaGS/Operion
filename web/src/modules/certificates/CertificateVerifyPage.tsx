import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import CircularProgress from "@mui/material/CircularProgress";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { ApiError } from "../../api/client";
import { verifyCertificate, type CertificateVerification } from "../../api/certificates";
import { Wordmark } from "../../branding/Wordmark";
import { colors } from "../../theme";

/** Public page a certificate's QR code opens. Shows only what the API returns - no login, no other student data. */
export function CertificateVerifyPage() {
	const { orgSlug = "", token = "" } = useParams();
	const [result, setResult] = useState<CertificateVerification | null>(null);
	const [notFound, setNotFound] = useState(false);
	const [error, setError] = useState<string | null>(null);

	useEffect(() => {
		verifyCertificate(orgSlug, token)
			.then(setResult)
			.catch((err) => {
				if (err instanceof ApiError && err.status === 404) setNotFound(true);
				else setError(err instanceof ApiError ? err.message : "Could not check this certificate right now");
			});
	}, [orgSlug, token]);

	const rows: [string, string][] = result
		? [
				["Certificate", result.certificateName],
				["Number", result.certificateNumber],
				["Issued on", new Date(result.issueDate).toLocaleDateString(undefined, { day: "numeric", month: "long", year: "numeric" })],
				["Issued by", result.schoolName],
				["Student", result.studentName],
			]
		: [];

	return (
		<Box sx={{ display: "flex", justifyContent: "center", alignItems: "flex-start", minHeight: "100vh", bgcolor: colors.paper, p: { xs: 1.5, sm: 4 } }}>
			<Paper variant="outlined" sx={{ p: { xs: 2.5, sm: 4 }, width: "100%", maxWidth: 520, borderColor: colors.rule }}>
				<Stack spacing={2}>
					<Wordmark tagline="Certificate verification" />
					{!result && !notFound && !error && <CircularProgress size={28} />}
					{notFound && (
						<Alert severity="error" data-testid="verify-result">
							No certificate matches this link. It may have been mistyped, or the certificate is not genuine.
						</Alert>
					)}
					{error && <Alert severity="warning">{error}</Alert>}
					{result && (
						<>
							{result.status === "ISSUED" ? (
								<Alert severity="success" data-testid="verify-result">
									This certificate is genuine and valid.
								</Alert>
							) : (
								<Alert severity="error" data-testid="verify-result">
									This certificate was issued but has since been cancelled by the school. It is no longer valid.
								</Alert>
							)}
							<Stack spacing={1}>
								{rows.map(([label, value]) => (
									<Box key={label} sx={{ display: "flex", gap: 2 }}>
										<Typography variant="body2" color="text.secondary" sx={{ minWidth: 110 }}>
											{label}
										</Typography>
										<Typography variant="body2" sx={{ fontWeight: 600 }}>
											{value}
										</Typography>
									</Box>
								))}
							</Stack>
							<Typography variant="caption" color="text.secondary">
								Check that these details match the paper certificate exactly.
							</Typography>
						</>
					)}
				</Stack>
			</Paper>
		</Box>
	);
}
