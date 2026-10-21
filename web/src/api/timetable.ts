import { api, ApiError } from "./client";
import { getSession } from "./tokenStore";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export type SlotType = "TEACHING" | "BREAK" | "SPECIAL";

export interface PeriodRequest {
	ordinal?: number;
	label: string;
	startTime: string;
	endTime: string;
	/** Older flag, kept for callers that only know break/not-break; slotType wins when both are sent. */
	breakSlot?: boolean;
	slotType?: SlotType;
	/** Which school day (period template) a new period belongs to; omitted is the default day. */
	templateId?: number | null;
}

export interface PeriodResponse {
	id: number;
	ordinal: number;
	label: string;
	startTime: string;
	endTime: string;
	/** True for every non-teaching slot (BREAK and SPECIAL). */
	breakSlot: boolean;
	slotType: SlotType;
	templateId: number | null;
	templateName: string | null;
}

export interface TimetableEntryResponse {
	id: number;
	sectionId: number;
	sectionName: string;
	className: string;
	subjectId: number;
	subjectName: string;
	teacherPersonId: number;
	teacherName: string;
	dayOfWeek: string;
	periodId: number;
	roomId: number | null;
	roomName: string | null;
}

export interface SetTimetableEntryRequest {
	subjectId: number;
	teacherPersonId: number;
	dayOfWeek: string;
	periodId: number;
	roomId?: number | null;
}

/** The default school day, or a template's day when templateId is given. */
export function listPeriods(templateId?: number | null): Promise<PeriodResponse[]> {
	return api.get<PeriodResponse[]>(`/api/v1/timetable/periods${templateId ? `?templateId=${templateId}` : ""}`);
}

/** Every period of every school day (the default day and each template), for views that mix class groups. */
export async function listAllPeriods(): Promise<PeriodResponse[]> {
	const [defaultDay, templates] = await Promise.all([listPeriods(), listPeriodTemplates()]);
	const others = await Promise.all(templates.map((template) => listPeriods(template.id)));
	return [...defaultDay, ...others.flat()];
}

/** The periods of the school day the section's class follows - what its grid is drawn from. */
export function listSectionPeriods(sectionId: number): Promise<PeriodResponse[]> {
	return api.get<PeriodResponse[]>(`/api/v1/sections/${sectionId}/timetable/periods`);
}

export function createPeriod(request: PeriodRequest): Promise<PeriodResponse> {
	return api.post<PeriodResponse>("/api/v1/timetable/periods", request);
}

export function updatePeriod(id: number, request: PeriodRequest): Promise<PeriodResponse> {
	return api.put<PeriodResponse>(`/api/v1/timetable/periods/${id}`, request);
}

export function deletePeriod(id: number): Promise<void> {
	return api.delete<void>(`/api/v1/timetable/periods/${id}`);
}

export function listSectionTimetable(sectionId: number): Promise<TimetableEntryResponse[]> {
	return api.get<TimetableEntryResponse[]>(`/api/v1/sections/${sectionId}/timetable`);
}

export function listTeacherTimetable(personId: number, academicYearId?: number): Promise<TimetableEntryResponse[]> {
	const query = academicYearId ? `?academicYearId=${academicYearId}` : "";
	return api.get<TimetableEntryResponse[]>(`/api/v1/teachers/${personId}/timetable${query}`);
}

export function setTimetableEntry(sectionId: number, request: SetTimetableEntryRequest): Promise<TimetableEntryResponse> {
	return api.put<TimetableEntryResponse>(`/api/v1/sections/${sectionId}/timetable`, request);
}

export function removeTimetableEntry(id: number): Promise<void> {
	return api.delete<void>(`/api/v1/timetable-entries/${id}`);
}

// Settings

export interface TimetableSettingsResponse {
	gapMinutes: number;
	maxTeacherPeriodsPerDay: number | null;
	maxTeacherPeriodsPerWeek: number | null;
}

export interface UpdateTimetableSettingsRequest {
	gapMinutes: number;
	/** null or 0 clears the limit. */
	maxTeacherPeriodsPerDay: number | null;
	maxTeacherPeriodsPerWeek: number | null;
}

export function getTimetableSettings(): Promise<TimetableSettingsResponse> {
	return api.get<TimetableSettingsResponse>("/api/v1/timetable/settings");
}

export function updateTimetableSettings(request: UpdateTimetableSettingsRequest): Promise<TimetableSettingsResponse> {
	return api.put<TimetableSettingsResponse>("/api/v1/timetable/settings", request);
}

// Period templates (alternative school days per class group)

export interface PeriodTemplateResponse {
	id: number;
	name: string;
	periodCount: number;
	classCount: number;
}

export interface ClassPeriodTemplateResponse {
	classId: number;
	templateId: number | null;
	templateName: string | null;
}

export function listPeriodTemplates(): Promise<PeriodTemplateResponse[]> {
	return api.get<PeriodTemplateResponse[]>("/api/v1/timetable/templates");
}

export function createPeriodTemplate(name: string): Promise<PeriodTemplateResponse> {
	return api.post<PeriodTemplateResponse>("/api/v1/timetable/templates", { name });
}

export function renamePeriodTemplate(id: number, name: string): Promise<PeriodTemplateResponse> {
	return api.put<PeriodTemplateResponse>(`/api/v1/timetable/templates/${id}`, { name });
}

export function deletePeriodTemplate(id: number): Promise<void> {
	return api.delete<void>(`/api/v1/timetable/templates/${id}`);
}

export function getClassPeriodTemplate(classId: number): Promise<ClassPeriodTemplateResponse> {
	return api.get<ClassPeriodTemplateResponse>(`/api/v1/school-classes/${classId}/period-template`);
}

/** templateId null moves the class back to the default school day. */
export function setClassPeriodTemplate(classId: number, templateId: number | null): Promise<ClassPeriodTemplateResponse> {
	return api.put<ClassPeriodTemplateResponse>(`/api/v1/school-classes/${classId}/period-template`, { templateId });
}

// Rooms

export interface RoomResponse {
	id: number;
	name: string;
	roomType: string | null;
	capacity: number | null;
	active: boolean;
}

export interface RoomRequest {
	name: string;
	roomType?: string | null;
	capacity?: number | null;
	active?: boolean;
}

export function listRooms(): Promise<RoomResponse[]> {
	return api.get<RoomResponse[]>("/api/v1/timetable/rooms");
}

export function createRoom(request: RoomRequest): Promise<RoomResponse> {
	return api.post<RoomResponse>("/api/v1/timetable/rooms", request);
}

export function updateRoom(id: number, request: RoomRequest): Promise<RoomResponse> {
	return api.put<RoomResponse>(`/api/v1/timetable/rooms/${id}`, request);
}

export function deleteRoom(id: number): Promise<void> {
	return api.delete<void>(`/api/v1/timetable/rooms/${id}`);
}

// Coverage, publishing and generation (per section)

export interface SubjectCoverageResponse {
	subjectId: number;
	subjectName: string;
	/** Weekly quota; null when the class has none for this subject. */
	requiredPerWeek: number | null;
	placed: number;
	remaining: number;
}

export interface SectionTimetableSummaryResponse {
	sectionId: number;
	placedSlots: number;
	/** The sum of every quota's shortfall - zero means the timetable is complete against its quotas. */
	unplacedPeriods: number;
	subjects: SubjectCoverageResponse[];
	published: boolean;
	publishedAt: string | null;
}

export function getSectionTimetableSummary(sectionId: number): Promise<SectionTimetableSummaryResponse> {
	return api.get<SectionTimetableSummaryResponse>(`/api/v1/sections/${sectionId}/timetable/summary`);
}

export function publishSectionTimetable(sectionId: number): Promise<SectionTimetableSummaryResponse> {
	return api.post<SectionTimetableSummaryResponse>(`/api/v1/sections/${sectionId}/timetable/publish`);
}

export function unpublishSectionTimetable(sectionId: number): Promise<SectionTimetableSummaryResponse> {
	return api.post<SectionTimetableSummaryResponse>(`/api/v1/sections/${sectionId}/timetable/unpublish`);
}

export interface GenerationShortfall {
	subjectId: number;
	subjectName: string;
	missing: number;
	reason: string;
}

export interface GenerationSectionOutcome {
	sectionId: number;
	sectionName: string;
	className: string;
	placed: number;
	shortfalls: GenerationShortfall[];
}

export interface GenerationResponse {
	placed: number;
	unplaced: number;
	sections: GenerationSectionOutcome[];
}

export function generateSectionTimetable(sectionId: number, clearExisting: boolean): Promise<GenerationResponse> {
	return api.post<GenerationResponse>(`/api/v1/sections/${sectionId}/timetable/generate`, { clearExisting });
}

// Teacher unavailability

export interface UnavailabilityResponse {
	id: number;
	teacherPersonId: number;
	dayOfWeek: string;
	periodId: number | null;
	periodLabel: string | null;
	reason: string | null;
}

export interface UnavailabilityRequest {
	dayOfWeek: string;
	/** null blocks the whole day. */
	periodId: number | null;
	reason?: string | null;
}

export function listTeacherUnavailability(personId: number): Promise<UnavailabilityResponse[]> {
	return api.get<UnavailabilityResponse[]>(`/api/v1/teachers/${personId}/unavailability`);
}

export function addTeacherUnavailability(personId: number, request: UnavailabilityRequest): Promise<UnavailabilityResponse> {
	return api.post<UnavailabilityResponse>(`/api/v1/teachers/${personId}/unavailability`, request);
}

export function removeTeacherUnavailability(id: number): Promise<void> {
	return api.delete<void>(`/api/v1/timetable/unavailability/${id}`);
}

// Substitutions

export interface SubstitutionResponse {
	id: number;
	date: string;
	entryId: number;
	sectionId: number;
	sectionName: string;
	className: string;
	subjectName: string;
	periodId: number;
	periodLabel: string;
	startTime: string;
	endTime: string;
	absentTeacherPersonId: number;
	absentTeacherName: string;
	substituteTeacherPersonId: number;
	substituteTeacherName: string;
	reason: string | null;
}

export interface SubstitutionCandidate {
	personId: number;
	name: string;
	rank: string;
}

export interface SubstitutionNeedResponse {
	date: string;
	dayOfWeek: string;
	entryId: number;
	sectionId: number;
	sectionName: string;
	className: string;
	subjectName: string;
	periodId: number;
	periodLabel: string;
	startTime: string;
	endTime: string;
	substitution: SubstitutionResponse | null;
	candidates: SubstitutionCandidate[];
}

export function listSubstitutionsOn(date: string): Promise<SubstitutionResponse[]> {
	return api.get<SubstitutionResponse[]>(`/api/v1/timetable/substitutions?date=${date}`);
}

export function listSubstitutionNeeds(teacherPersonId: number, from: string, to: string): Promise<SubstitutionNeedResponse[]> {
	return api.get<SubstitutionNeedResponse[]>(
		`/api/v1/timetable/substitutions/needs?teacherPersonId=${teacherPersonId}&from=${from}&to=${to}`,
	);
}

export function assignSubstitution(request: {
	entryId: number;
	date: string;
	substituteTeacherPersonId: number;
	reason?: string | null;
}): Promise<SubstitutionResponse> {
	return api.post<SubstitutionResponse>("/api/v1/timetable/substitutions", request);
}

export function removeSubstitution(id: number): Promise<void> {
	return api.delete<void>(`/api/v1/timetable/substitutions/${id}`);
}

// Portals

export interface PortalCover {
	role: "COVERING" | "COVERED" | "SUBSTITUTED";
	substitution: SubstitutionResponse;
}

export interface PortalTimetableResponse {
	weekStart: string;
	weekEnd: string;
	/** False when a guardian's child section is still a draft: the periods come back, the entries do not. */
	published: boolean;
	periods: PeriodResponse[];
	entries: TimetableEntryResponse[];
	covers: PortalCover[];
}

/** The signed-in teacher's own published week; date is any day in the week wanted (default today). */
export function getMyTimetable(date?: string): Promise<PortalTimetableResponse> {
	return api.get<PortalTimetableResponse>(`/api/v1/me/timetable${date ? `?date=${date}` : ""}`);
}

export function getChildTimetable(studentId: number, date?: string): Promise<PortalTimetableResponse> {
	return api.get<PortalTimetableResponse>(`/api/v1/me/children/${studentId}/timetable${date ? `?date=${date}` : ""}`);
}

// CSV export

async function downloadCsv(path: string, fallbackName: string): Promise<void> {
	const session = getSession();
	const headers = new Headers({ "ngrok-skip-browser-warning": "true" });
	if (session) headers.set("Authorization", `Bearer ${session.token}`);
	const response = await fetch(`${API_BASE_URL}${path}`, { headers });
	if (!response.ok) {
		const body = await response.json().catch(() => null);
		throw new ApiError(response.status, body?.error ?? `Export failed with status ${response.status}`);
	}
	const disposition = response.headers.get("Content-Disposition") ?? "";
	const name = /filename="([^"]+)"/.exec(disposition)?.[1] ?? fallbackName;
	const url = URL.createObjectURL(await response.blob());
	const link = document.createElement("a");
	link.href = url;
	link.download = name;
	link.click();
	URL.revokeObjectURL(url);
}

export function downloadSectionTimetableCsv(sectionId: number): Promise<void> {
	return downloadCsv(`/api/v1/sections/${sectionId}/timetable/export`, "timetable.csv");
}

export function downloadTeacherTimetableCsv(personId: number): Promise<void> {
	return downloadCsv(`/api/v1/teachers/${personId}/timetable/export`, "timetable.csv");
}
