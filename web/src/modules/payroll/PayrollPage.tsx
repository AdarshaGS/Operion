import { useState, type SyntheticEvent } from "react";
import Box from "@mui/material/Box";
import Stack from "@mui/material/Stack";
import Tab from "@mui/material/Tab";
import Tabs from "@mui/material/Tabs";
import { PayrollRunsPanel } from "./PayrollRunsPanel";
import { SalaryComponentsPanel } from "./SalaryComponentsPanel";

type PayrollTab = "runs" | "components";

export function PayrollPage() {
	const [tab, setTab] = useState<PayrollTab>("runs");

	return (
		<Stack spacing={2}>
			<Box sx={{ borderBottom: 1, borderColor: "divider" }}>
				<Tabs value={tab} onChange={(_e: SyntheticEvent, value: PayrollTab) => setTab(value)}>
					<Tab label="Payroll runs" value="runs" />
					<Tab label="Salary components" value="components" />
				</Tabs>
			</Box>
			{tab === "runs" && <PayrollRunsPanel />}
			{tab === "components" && <SalaryComponentsPanel />}
		</Stack>
	);
}
