import { api } from "./client";

export type ComponentType = "EARNING" | "DEDUCTION";
export type CalculationBasis = "FLAT" | "PERCENT_OF_BASIC";

export interface SalaryComponentResponse {
	id: number;
	name: string;
	type: ComponentType;
	calculationBasis: CalculationBasis;
	status: "ACTIVE" | "INACTIVE";
}

export interface CreateSalaryComponentRequest {
	name: string;
	type: ComponentType;
	calculationBasis: CalculationBasis;
}

export interface SalaryStructureLineResponse {
	componentId: number;
	componentName: string;
	type: ComponentType;
	calculationBasis: CalculationBasis;
	value: number;
}

export interface SalaryStructureResponse {
	id: number;
	staffProfileId: number;
	effectiveFrom: string;
	basicAmount: number;
	lines: SalaryStructureLineResponse[];
}

export interface CreateSalaryStructureRequest {
	effectiveFrom: string;
	basicAmount: number;
	lines: { componentId: number; value: number }[];
}

export interface PayslipLineResponse {
	id: number;
	label: string;
	type: ComponentType;
	source: "BASIC" | "STRUCTURE" | "ADJUSTMENT";
	amount: number;
}

export interface PayslipResponse {
	id: number;
	payrollRunId: number;
	payYear: number;
	payMonth: number;
	runStatus: "DRAFT" | "FINALIZED" | "SUPERSEDED";
	staffProfileId: number;
	employeeCode: string;
	staffName: string;
	designationName: string | null;
	departmentName: string | null;
	teacher: boolean;
	basicAmount: number;
	grossEarnings: number;
	totalDeductions: number;
	netPay: number;
	lines: PayslipLineResponse[];
}

export interface PayrollTotals {
	count: number;
	gross: number;
	deductions: number;
	net: number;
}

export interface PayrollRunResponse {
	id: number;
	payYear: number;
	payMonth: number;
	status: "DRAFT" | "FINALIZED" | "SUPERSEDED";
	supersedesRunId: number | null;
	finalizedAt: string | null;
	payslipCount: number;
	teacherTotals: PayrollTotals;
	staffTotals: PayrollTotals;
	payslips: PayslipResponse[];
}

export function listSalaryComponents(): Promise<SalaryComponentResponse[]> {
	return api.get<SalaryComponentResponse[]>("/api/v1/payroll/components");
}

export function createSalaryComponent(request: CreateSalaryComponentRequest): Promise<SalaryComponentResponse> {
	return api.post<SalaryComponentResponse>("/api/v1/payroll/components", request);
}

export function changeSalaryComponentStatus(id: number, status: "ACTIVE" | "INACTIVE"): Promise<SalaryComponentResponse> {
	return api.post<SalaryComponentResponse>(`/api/v1/payroll/components/${id}/status`, { status });
}

export function listSalaryStructures(staffProfileId: number): Promise<SalaryStructureResponse[]> {
	return api.get<SalaryStructureResponse[]>(`/api/v1/hr/staff/${staffProfileId}/salary-structures`);
}

export function createSalaryStructure(staffProfileId: number, request: CreateSalaryStructureRequest): Promise<SalaryStructureResponse> {
	return api.post<SalaryStructureResponse>(`/api/v1/hr/staff/${staffProfileId}/salary-structures`, request);
}

export function listPayrollRuns(): Promise<PayrollRunResponse[]> {
	return api.get<PayrollRunResponse[]>("/api/v1/payroll/runs");
}

export function getPayrollRun(id: number): Promise<PayrollRunResponse> {
	return api.get<PayrollRunResponse>(`/api/v1/payroll/runs/${id}`);
}

export function generatePayrollRun(year: number, month: number): Promise<PayrollRunResponse> {
	return api.post<PayrollRunResponse>("/api/v1/payroll/runs", { year, month });
}

export function finalizePayrollRun(id: number): Promise<PayrollRunResponse> {
	return api.post<PayrollRunResponse>(`/api/v1/payroll/runs/${id}/finalize`);
}

export function startPayrollCorrection(id: number): Promise<PayrollRunResponse> {
	return api.post<PayrollRunResponse>(`/api/v1/payroll/runs/${id}/correct`);
}

export function discardPayrollRun(id: number): Promise<void> {
	return api.delete<void>(`/api/v1/payroll/runs/${id}`);
}

export function getPayslip(id: number): Promise<PayslipResponse> {
	return api.get<PayslipResponse>(`/api/v1/payroll/payslips/${id}`);
}

export function addPayslipAdjustment(
	id: number,
	request: { label: string; type: ComponentType; amount: number },
): Promise<PayslipResponse> {
	return api.post<PayslipResponse>(`/api/v1/payroll/payslips/${id}/adjustments`, request);
}

export function removePayslipAdjustment(lineId: number): Promise<PayslipResponse> {
	return api.delete<PayslipResponse>(`/api/v1/payroll/payslip-lines/${lineId}`);
}

/** Staff self-service (portal) - own finalized payslips only. */
export function getMyPayslips(): Promise<PayslipResponse[]> {
	return api.get<PayslipResponse[]>("/api/v1/me/payslips");
}

export function getMyPayslip(id: number): Promise<PayslipResponse> {
	return api.get<PayslipResponse>(`/api/v1/me/payslips/${id}`);
}
