import { api } from "./client";

export interface PurchaseOrderLineItemRequest {
	itemId: number;
	quantity: number;
	unitCost: number;
}

export interface CreatePurchaseOrderRequest {
	supplierId: number;
	campusId: number;
	expectedDate: string;
	lines: PurchaseOrderLineItemRequest[];
}

export interface PurchaseOrderResponse {
	id: number;
	supplierId: number;
	campusId: number;
	expectedDate: string;
	status: string;
}

export interface PurchaseOrderLineResponse {
	id: number;
	itemId: number;
	quantity: number;
	unitCost: number;
	quantityReceived: number;
	quantityReturned: number;
}

export interface PurchaseOrderDetailResponse extends PurchaseOrderResponse {
	lines: PurchaseOrderLineResponse[];
}

export interface ReceiveGoodsLineRequest {
	lineId: number;
	quantity: number;
	unitCost?: number | null;
}

export interface ReceiveGoodsRequest {
	entryDate: string;
	lines: ReceiveGoodsLineRequest[];
}

export interface RecordPurchaseReturnRequest {
	quantity: number;
	reason: string;
	returnDate: string;
	remarks?: string | null;
}

export interface PurchaseReturnResponse {
	id: number;
	purchaseOrderLineId: number;
	quantity: number;
	reason: string;
	returnDate: string;
	remarks: string | null;
}

export function createPurchaseOrder(request: CreatePurchaseOrderRequest): Promise<PurchaseOrderResponse> {
	return api.post<PurchaseOrderResponse>("/api/v1/purchase/orders", request);
}

export function listPurchaseOrders(): Promise<PurchaseOrderResponse[]> {
	return api.get<PurchaseOrderResponse[]>("/api/v1/purchase/orders");
}

export function getPurchaseOrder(id: number): Promise<PurchaseOrderDetailResponse> {
	return api.get<PurchaseOrderDetailResponse>(`/api/v1/purchase/orders/${id}`);
}

export function submitPurchaseOrder(id: number): Promise<PurchaseOrderResponse> {
	return api.post<PurchaseOrderResponse>(`/api/v1/purchase/orders/${id}/submit`);
}

export function approvePurchaseOrder(id: number): Promise<PurchaseOrderResponse> {
	return api.post<PurchaseOrderResponse>(`/api/v1/purchase/orders/${id}/approve`);
}

export function cancelPurchaseOrder(id: number): Promise<PurchaseOrderResponse> {
	return api.post<PurchaseOrderResponse>(`/api/v1/purchase/orders/${id}/cancel`);
}

export function receiveGoods(id: number, request: ReceiveGoodsRequest): Promise<PurchaseOrderDetailResponse> {
	return api.post<PurchaseOrderDetailResponse>(`/api/v1/purchase/orders/${id}/receive`, request);
}

export function recordPurchaseReturn(
	orderId: number,
	lineId: number,
	request: RecordPurchaseReturnRequest,
): Promise<PurchaseReturnResponse> {
	return api.post<PurchaseReturnResponse>(`/api/v1/purchase/orders/${orderId}/lines/${lineId}/returns`, request);
}
