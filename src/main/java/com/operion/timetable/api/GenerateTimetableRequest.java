package com.operion.timetable.api;

/** clearExisting=true wipes the section's current slots first; by default only empty slots are filled. */
public record GenerateTimetableRequest(Boolean clearExisting) {
}
