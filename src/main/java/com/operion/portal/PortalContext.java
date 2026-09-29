package com.operion.portal;

import java.util.List;

/**
 * One self-service identity available to the caller. GUARDIAN and TEACHER are populated
 * independently - a person can hold both simultaneously (e.g. a teacher whose own child
 * also attends), in which case the frontend offers a switcher rather than the backend
 * guessing precedence.
 */
public record PortalContext(PortalContextType type, Long guardianId, List<ChildSummary> children, Long teacherPersonId) {
}
