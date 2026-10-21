package com.operion.certificate.api;

import java.util.List;

import com.operion.certificate.CertificateType;

public record CertificateTypeResponse(Long id, String code, String name, String title, String bodyTemplate, String particularsTemplate,
		String manualFields, List<CertificateType.ManualField> manualFieldList, String numberFormat, long nextSequence, String pageSize,
		boolean oncePerStudent, boolean requiresExit, boolean requiresClearance, boolean approvalRequired, boolean guardianRequestable,
		boolean active) {

	public static CertificateTypeResponse from(CertificateType type) {
		return new CertificateTypeResponse(type.getId(), type.getCode(), type.getName(), type.getTitle(), type.getBodyTemplate(),
				type.getParticularsTemplate(), type.getManualFields(), type.manualFieldList(), type.getNumberFormat(), type.getNextSequence(),
				type.getPageSize(), type.isOncePerStudent(), type.isRequiresExit(), type.isRequiresClearance(), type.isApprovalRequired(),
				type.isGuardianRequestable(), type.isActive());
	}
}
