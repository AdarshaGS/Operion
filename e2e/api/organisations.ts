// Provisioning + core setup-chain calls (org -> structure -> academic -> students -> fees),
// mirroring the dependency order in SetupChecklist. Used by global-setup to build the
// fixture org once per run; individual module specs create their own deeper data
// through the UI (page objects), not through this file.

import { api } from "./client";

export interface LoginResponse {
	token: string;
	expiresAt: string;
	userId: number;
	organisationId: number;
}

export interface OrganisationResponse {
	id: number;
	name: string;
	legalName: string;
	slug: string;
	status: string;
}

export interface CreateOrganisationInput {
	name: string;
	legalName: string;
	slug: string;
	adminEmail: string;
	adminPassword: string;
	adminFirstName: string;
	adminLastName: string;
}

export function provisionOrganisation(input: CreateOrganisationInput) {
	return api.post<OrganisationResponse>("/api/v1/organisations", input);
}

export function login(organisationSlug: string, email: string, password: string) {
	return api.post<LoginResponse>("/api/v1/auth/login", { organisationSlug, email, password });
}

export interface CampusResponse {
	id: number;
	name: string;
	code: string;
}

export function listCampuses(token: string) {
	return api.get<CampusResponse[]>("/api/v1/campuses", token);
}

export interface AcademicYearResponse {
	id: number;
	name: string;
}

export function createAcademicYear(token: string, name: string, startDate: string, endDate: string) {
	return api.post<AcademicYearResponse>("/api/v1/academic-years", { name, startDate, endDate }, token);
}

export interface GradeLevelResponse {
	id: number;
	name: string;
}

export function createGradeLevel(token: string, name: string, sequenceOrder: number, stage: string) {
	return api.post<GradeLevelResponse>("/api/v1/grade-levels", { name, sequenceOrder, stage }, token);
}

export interface SchoolClassResponse {
	id: number;
	displayName: string;
}

export function createSchoolClass(
	token: string,
	academicYearId: number,
	campusId: number,
	gradeLevelId: number,
	displayName: string,
) {
	return api.post<SchoolClassResponse>("/api/v1/school-classes", { academicYearId, campusId, gradeLevelId, displayName }, token);
}

export interface SectionResponse {
	id: number;
	name: string;
}

export function createSection(token: string, classId: number, name: string, capacity: number, room: string) {
	return api.post<SectionResponse>(`/api/v1/school-classes/${classId}/sections`, { name, capacity, room }, token);
}

export interface PersonResponse {
	id: number;
	firstName: string;
	lastName: string;
}

export interface CreatePersonInput {
	firstName: string;
	lastName: string;
	dateOfBirth?: string;
	gender?: string;
	phone?: string;
	email?: string;
}

export function createPerson(token: string, input: CreatePersonInput) {
	return api.post<PersonResponse>("/api/v1/persons", input, token);
}

export interface StudentResponse {
	id: number;
	admissionNumber: string;
}

export interface AdmitStudentInput {
	personId: number;
	admissionNumber: string;
	admissionDate: string;
	admissionSource?: string;
	previousSchool?: string;
	tcNumber?: string;
	entranceScore?: number;
	bloodGroup?: string;
	category?: string;
	nationality?: string;
	remarks?: string;
}

export function admitStudent(token: string, input: AdmitStudentInput) {
	return api.post<StudentResponse>("/api/v1/students", input, token);
}

export interface EnrollmentResponse {
	id: number;
	sectionId: number;
	rollNumber: number | null;
}

export function enrollStudent(
	token: string,
	studentId: number,
	input: { academicYearId: number; sectionId: number; rollNumber: number; enrolledDate: string },
) {
	return api.post<EnrollmentResponse>(`/api/v1/students/${studentId}/enrollments`, input, token);
}

export interface SubjectResponse {
	id: number;
	name: string;
	code: string;
}

export function createSubject(token: string, name: string, code: string, elective = false) {
	return api.post<SubjectResponse>("/api/v1/subjects", { name, code, elective }, token);
}
