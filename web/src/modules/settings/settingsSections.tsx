import type { ReactNode } from "react";
import ApartmentIcon from "@mui/icons-material/Apartment";
import BadgeIcon from "@mui/icons-material/Badge";
import BrushIcon from "@mui/icons-material/Brush";
import CalendarMonthIcon from "@mui/icons-material/CalendarMonth";
import GroupIcon from "@mui/icons-material/Group";
import HistoryIcon from "@mui/icons-material/History";
import ImportExportIcon from "@mui/icons-material/ImportExport";
import LocationCityIcon from "@mui/icons-material/LocationCity";
import SecurityIcon from "@mui/icons-material/Security";
import TuneIcon from "@mui/icons-material/Tune";
import WorkIcon from "@mui/icons-material/Work";
import { AcademicYearsPanel } from "./AcademicYearsPanel";
import { AuditLogsPanel } from "./AuditLogsPanel";
import { BusinessSettingsPanel } from "./BusinessSettingsPanel";
import { CampusesPanel } from "./CampusesPanel";
import { DepartmentsPanel } from "./DepartmentsPanel";
import { DesignationsPanel } from "./DesignationsPanel";
import { ImportsExportsPanel } from "./ImportsExportsPanel";
import { OrganisationBrandingPanel } from "./OrganisationBrandingPanel";
import { OrganisationProfilePanel } from "./OrganisationProfilePanel";
import { RolesPanel } from "./RolesPanel";
import { UsersPanel } from "./UsersPanel";

export interface SettingsSection {
	key: string;
	label: string;
	description: string;
	icon: ReactNode;
	panel: ReactNode;
}

/** Single source of truth for both the Settings landing page (SettingsPage) and each
 * section's own page (SettingsSectionPage) - one list drives the tile grid and the
 * /settings/:section route so the two can't drift out of sync. */
export const SETTINGS_SECTIONS: SettingsSection[] = [
	{
		key: "profile",
		label: "Organisation profile",
		description: "Name, logo, primary contact, address, and tax identifiers",
		icon: <BadgeIcon />,
		panel: <OrganisationProfilePanel />,
	},
	{
		key: "branding",
		label: "Branding & documents",
		description: "Logo, stamp, signature, footer text, and numbering formats for printed documents",
		icon: <BrushIcon />,
		panel: <OrganisationBrandingPanel />,
	},
	{
		key: "business",
		label: "Business settings",
		description: "Timezone, currency, date format, and working days",
		icon: <TuneIcon />,
		panel: <BusinessSettingsPanel />,
	},
	{
		key: "campuses",
		label: "Campuses",
		description: "Add or manage branch and location campuses",
		icon: <LocationCityIcon />,
		panel: <CampusesPanel />,
	},
	{
		key: "departments",
		label: "Departments",
		description: "Manage the organisation's departments",
		icon: <ApartmentIcon />,
		panel: <DepartmentsPanel />,
	},
	{
		key: "designations",
		label: "Designations",
		description: "Manage staff designations",
		icon: <WorkIcon />,
		panel: <DesignationsPanel />,
	},
	{
		key: "academic-years",
		label: "Academic years",
		description: "Manage academic year periods",
		icon: <CalendarMonthIcon />,
		panel: <AcademicYearsPanel />,
	},
	{
		key: "roles",
		label: "Roles",
		description: "Define roles and their permissions",
		icon: <SecurityIcon />,
		panel: <RolesPanel />,
	},
	{
		key: "users",
		label: "Users",
		description: "Manage user accounts and role assignments",
		icon: <GroupIcon />,
		panel: <UsersPanel />,
	},
	{
		key: "audit-logs",
		label: "Audit logs",
		description: "Who changed roles, fee records, marks, student data, and settings",
		icon: <HistoryIcon />,
		panel: <AuditLogsPanel />,
	},
	{
		key: "imports-exports",
		label: "Imports & exports",
		description: "Bulk import students from CSV, or export existing records",
		icon: <ImportExportIcon />,
		panel: <ImportsExportsPanel />,
	},
];
