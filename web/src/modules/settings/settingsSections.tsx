import type { ReactNode } from "react";
import ApartmentIcon from "@mui/icons-material/Apartment";
import BadgeIcon from "@mui/icons-material/Badge";
import BrushIcon from "@mui/icons-material/Brush";
import CableIcon from "@mui/icons-material/Cable";
import CalendarMonthIcon from "@mui/icons-material/CalendarMonth";
import CreditCardIcon from "@mui/icons-material/CreditCard";
import DescriptionIcon from "@mui/icons-material/Description";
import GroupIcon from "@mui/icons-material/Group";
import HistoryIcon from "@mui/icons-material/History";
import ImportExportIcon from "@mui/icons-material/ImportExport";
import LocationCityIcon from "@mui/icons-material/LocationCity";
import RuleIcon from "@mui/icons-material/Rule";
import SecurityIcon from "@mui/icons-material/Security";
import TuneIcon from "@mui/icons-material/Tune";
import WorkIcon from "@mui/icons-material/Work";
import { AcademicYearsPanel } from "./AcademicYearsPanel";
import { AuditLogsPanel } from "./AuditLogsPanel";
import { BusinessSettingsPanel } from "./BusinessSettingsPanel";
import { CampusesPanel } from "./CampusesPanel";
import { DepartmentsPanel } from "./DepartmentsPanel";
import { DesignationsPanel } from "./DesignationsPanel";
import { ExternalServicesPanel } from "./ExternalServicesPanel";
import { IdCardStudioPanel } from "./IdCardStudioPanel";
import { ImportsExportsPanel } from "./ImportsExportsPanel";
import { LetterFormatsPanel } from "./LetterFormatsPanel";
import { OrganisationBrandingPanel } from "./OrganisationBrandingPanel";
import { OrganisationProfilePanel } from "./OrganisationProfilePanel";
import { ProfileChangeRequestsPanel } from "./ProfileChangeRequestsPanel";
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
		key: "letter-formats",
		label: "Letter formats",
		description: "Branded header/footer template for question papers and report cards",
		icon: <DescriptionIcon />,
		panel: <LetterFormatsPanel />,
	},
	{
		key: "id-card-studio",
		label: "ID Card Studio",
		description: "Design ID card layouts bound to live student data",
		icon: <CreditCardIcon />,
		panel: <IdCardStudioPanel />,
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
	{
		key: "integrations",
		label: "Integrations",
		description: "Connect your own accounts for 3rd-party services like email and SMS delivery",
		icon: <CableIcon />,
		panel: <ExternalServicesPanel />,
	},
	{
		key: "profile-change-requests",
		label: "Profile change requests",
		description: "Review self-service phone/email/photo change requests from staff and guardians",
		icon: <RuleIcon />,
		panel: <ProfileChangeRequestsPanel />,
	},
];
