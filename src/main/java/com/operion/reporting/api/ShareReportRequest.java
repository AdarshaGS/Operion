package com.operion.reporting.api;

public record ShareReportRequest(String principalType, Long principalId, boolean canRun, boolean canEdit) {
}
