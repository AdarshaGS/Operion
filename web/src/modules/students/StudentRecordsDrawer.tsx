import { useId, useRef } from "react";
import Box from "@mui/material/Box";
import Drawer from "@mui/material/Drawer";
import IconButton from "@mui/material/IconButton";
import Tab from "@mui/material/Tab";
import Tabs from "@mui/material/Tabs";
import Typography from "@mui/material/Typography";
import CloseIcon from "@mui/icons-material/Close";
import type { StudentEnrollmentResponse } from "../../api/enrollments";
import { StudentCertificatesPanel } from "../certificates/StudentCertificatesPanel";
import { StudentDocumentsPanel } from "./StudentDocumentsPanel";
import { StudentHistoryPanel } from "./StudentHistoryPanel";
import { StudentTransferPanel } from "./StudentTransferPanel";
import { TAB_LABELS, type RecordsTab } from "./studentRecordsTabs";
import { useEnrollmentLabels } from "./useEnrollmentLabels";

interface StudentRecordsDrawerProps {
	open: boolean;
	tab: RecordsTab;
	tabs: RecordsTab[];
	onTabChange: (tab: RecordsTab) => void;
	onClose: () => void;
	studentId: number;
	enrollments: StudentEnrollmentResponse[];
}

/** Right-hand drawer holding the student's secondary records so the detail page stays compact.
 * Only the active tab's panel is mounted, and the whole drawer content is unmounted when closed. */
export function StudentRecordsDrawer({ open, tab, tabs, onTabChange, onClose, studentId, enrollments }: StudentRecordsDrawerProps) {
	const baseId = useId();
	const titleId = `${baseId}-title`;
	const panelId = `${baseId}-panel`;
	const tabId = (key: RecordsTab) => `${baseId}-tab-${key}`;
	// While the drawer slides out `tab` has already fallen back to the first tab (the ?records= param
	// is gone), so keep rendering the last tab that was shown instead of flashing another one.
	const shownTab = useRef<RecordsTab | undefined>(undefined);
	if (open) shownTab.current = tabs.includes(tab) ? tab : tabs[0];
	const activeTab = shownTab.current && tabs.includes(shownTab.current) ? shownTab.current : tabs[0];
	const labels = useEnrollmentLabels(enrollments, open && activeTab === "history");

	return (
		<Drawer
			anchor="right"
			open={open}
			onClose={onClose}
			slotProps={{ paper: { role: "dialog", "aria-modal": true, "aria-labelledby": titleId, sx: { width: "min(720px, 100vw)" } } }}
		>
			{activeTab && (
				<Box sx={{ display: "flex", flexDirection: "column", height: "100%" }}>
					<Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", px: 2, pt: 1.5 }}>
						<Typography id={titleId} variant="h6" component="h2">
							Records
						</Typography>
						<IconButton aria-label="Close records" onClick={onClose}>
							<CloseIcon />
						</IconButton>
					</Box>
					<Tabs
						value={activeTab}
						onChange={(_event, value: RecordsTab) => onTabChange(value)}
						variant="scrollable"
						scrollButtons="auto"
						sx={{ borderBottom: 1, borderColor: "divider", px: 1 }}
					>
						{tabs.map((key) => (
							<Tab key={key} id={tabId(key)} aria-controls={panelId} value={key} label={TAB_LABELS[key]} />
						))}
					</Tabs>
					<Box id={panelId} role="tabpanel" aria-labelledby={tabId(activeTab)} sx={{ p: 2, overflowY: "auto", flex: 1 }}>
						{activeTab === "documents" && <StudentDocumentsPanel studentId={studentId} />}
						{activeTab === "transfers" && <StudentTransferPanel studentId={studentId} />}
						{activeTab === "certificates" && <StudentCertificatesPanel studentId={studentId} />}
						{activeTab === "history" && <StudentHistoryPanel enrollments={enrollments} labels={labels} />}
					</Box>
				</Box>
			)}
		</Drawer>
	);
}
