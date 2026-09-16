package com.operion.academic.api;

/** Times as "HH:mm" strings (e.g. "08:30") - null clears a previously-set time. */
public record UpdateAcademicConfigurationRequest(String schoolStartTime, String schoolEndTime) {
}
