import { useState } from "react";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import Tab from "@mui/material/Tab";
import Tabs from "@mui/material/Tabs";
import Typography from "@mui/material/Typography";
import { AuditLogsPanel } from "./AuditLogsPanel";
import { ConsentPurposesTab } from "./ConsentPurposesTab";
import { ConsentRecordsTab } from "./ConsentRecordsTab";
import { DataSubjectRequestsTab } from "./DataSubjectRequestsTab";

/** Consent and data-subject-request handling (#234). The Audit trail tab reuses the shared audit
 * viewer pre-filtered to this console's entity types rather than building a second audit surface
 * - every consent decision and request transition already lands in the one ledger. */
export function PrivacyConsolePanel() {
	const [tab, setTab] = useState(0);

	return (
		<Paper sx={{ p: 3 }}>
			<Stack spacing={2}>
				<Typography variant="body2" color="text.secondary">
					Track who has agreed to what, and handle people's requests to see, correct, export or delete their data.
				</Typography>
				<Tabs value={tab} onChange={(_event, value) => setTab(value)}>
					<Tab label="Requests" />
					<Tab label="Consent" />
					<Tab label="Purposes" />
					<Tab label="Audit trail" />
				</Tabs>
				{tab === 0 && <DataSubjectRequestsTab />}
				{tab === 1 && <ConsentRecordsTab />}
				{tab === 2 && <ConsentPurposesTab />}
				{tab === 3 && <AuditLogsPanel defaultEntityType="DATA_SUBJECT_REQUEST" />}
			</Stack>
		</Paper>
	);
}
