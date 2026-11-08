package com.operion.dashboard.api;

/** Feeds the "next setup step" progress card on the Dashboard (#97) - a guidance layer,
 * not a gate. Each flag is a coarse, cheap-to-compute signal that the corresponding
 * setup step has been touched at all, not a precise completion audit.
 *
 * <p>The first six flags predate the school-lifecycle card and still back other screens
 * (sidebar gates); the rest were added for it, one per step
 * the older flags don't already cover. The card lists, in order: institutionProfileConfigured
 * (Institution setup), academicYearSet (Academic year creation - done only while a year is current), membersAdded (Team members &amp; roles),
 * classesAndSectionsCreated, subjectsMapped (every class of the latest year - the current one, else the most recently closed - has a subject), feesConfigured
 * (Fee structure configuration, set before students so they know their fees in advance), admissionsCompleted,
 * studentRecordsExist, attendanceStarted, examCreated, marksApproved, libraryStocked and, last and not
 * counted, academicYearClosed. The numbers shown on the card are positional - the frontend renumbers the
 * steps it actually shows - so they are not part of this contract. */
public record SetupChecklist(boolean rolesConfigured, boolean membersAdded, boolean academicSetupConfigured,
		boolean studentsAdded, boolean feesConfigured, boolean attendanceStarted, boolean institutionProfileConfigured,
		boolean academicYearSet, boolean classesAndSectionsCreated, boolean subjectsMapped, boolean admissionsCompleted,
		boolean studentRecordsExist, boolean examCreated, boolean marksApproved, boolean libraryStocked,
		boolean academicYearClosed) {
}
