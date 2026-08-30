package com.operion.hr.api;

import java.time.LocalDate;

/** campusId/departmentId are nullable - a null target is a valid transfer (e.g. into an
 * org-wide role). designationId is required, same as CreateStaffProfileRequest. */
public record TransferStaffRequest(Long campusId, Long departmentId, Long designationId, LocalDate effectiveDate) {
}
