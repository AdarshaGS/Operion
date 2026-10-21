package com.operion.certificate.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.operion.certificate.CertificateRequest;
import com.operion.certificate.CertificateType;
import com.operion.identity.Person;

public record CertificateRequestResponse(Long id, Long studentId, String studentName, String admissionNumber, Long certificateTypeId,
		String certificateTypeCode, String certificateTypeName, boolean approvalRequired, boolean requiresClearance,
		List<CertificateType.ManualField> manualFields, String status, String purpose, Map<String, String> manualValues, String requestedVia,
		Long requestedBy, Instant requestedAt, Long approvedBy, Instant approvedAt, String rejectionReason, String clearanceOverrideReason,
		String certificateNumber, Instant issuedAt, int printCount, String cancelReason) {

	public static CertificateRequestResponse from(CertificateRequest request, Map<String, String> manualValues) {
		Person person = request.getStudent().getPerson();
		CertificateType type = request.getCertificateType();
		String name = person.getLastName() == null || person.getLastName().isBlank() ? person.getFirstName()
				: person.getFirstName() + " " + person.getLastName();
		return new CertificateRequestResponse(request.getId(), request.getStudent().getId(), name, request.getStudent().getAdmissionNumber(),
				type.getId(), type.getCode(), type.getName(), type.isApprovalRequired(), type.isRequiresClearance(), type.manualFieldList(),
				request.getStatus().name(), request.getPurpose(), manualValues, request.getRequestedVia().name(), request.getCreatedBy(),
				request.getCreatedAt(), request.getApprovedBy(), request.getApprovedAt(), request.getRejectionReason(),
				request.getClearanceOverrideReason(), request.getCertificateNumber(), request.getIssuedAt(),
				request.getIssuedDocument() == null ? 0 : request.getIssuedDocument().getPrintCount(),
				request.getIssuedDocument() == null ? null : request.getIssuedDocument().getCancelReason());
	}
}
