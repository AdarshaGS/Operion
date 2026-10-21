package com.operion.finance.api;

import java.time.LocalDate;

public record BulkGenerateInvoicesRequest(Long academicYearId, Long schoolClassId, LocalDate dueOnOrBefore, Boolean dryRun) {
}
