import type { ReactNode } from "react";
import ApartmentIcon from "@mui/icons-material/Apartment";
import CalendarMonthIcon from "@mui/icons-material/CalendarMonth";
import GroupIcon from "@mui/icons-material/Group";
import LocationCityIcon from "@mui/icons-material/LocationCity";
import SecurityIcon from "@mui/icons-material/Security";
import TuneIcon from "@mui/icons-material/Tune";
import WorkIcon from "@mui/icons-material/Work";
import { AcademicYearsPanel } from "./AcademicYearsPanel";
import { BusinessSettingsPanel } from "./BusinessSettingsPanel";
import { CampusesPanel } from "./CampusesPanel";
import { DepartmentsPanel } from "./DepartmentsPanel";
import { DesignationsPanel } from "./DesignationsPanel";
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
];
