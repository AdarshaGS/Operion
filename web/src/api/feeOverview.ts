import { api } from "./client";

export type LedgerStatus = "PAID" | "PARTIAL" | "UNPAID" | "OVERDUE";

export interface FeeLedgerRow {
	studentEnrollmentId: number;
	academicYearId: number;
	studentId: number;
	studentName: string;
	admissionNumber: string;
	schoolClassId: number;
	classLabel: string;
	totalFees: number;
	paid: number;
	outstanding: number;
	nextDueDate: string | null;
	status: LedgerStatus;
}

export interface FeeOverviewResponse {
	totalFees: number;
	collected: number;
	outstanding: number;
	overdue: number;
	ledger: FeeLedgerRow[];
	trend: { month: string; collected: number; outstanding: number }[];
	byClass: { schoolClassId: number; classLabel: string; total: number; collected: number; percent: number }[];
	byType: { name: string; amount: number }[];
}

export function getFeeOverview(academicYearId?: number): Promise<FeeOverviewResponse> {
	return api.get<FeeOverviewResponse>(`/api/v1/fees/overview${academicYearId ? `?academicYearId=${academicYearId}` : ""}`);
}
