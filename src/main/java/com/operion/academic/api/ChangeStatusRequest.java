package com.operion.academic.api;

/** Shared across GradeLevel/Subject/SchoolClass/Section/ClassSubject status-change
 * endpoints - each entity parses `status` against its own enum, this DTO only carries
 * the raw string across the wire. */
public record ChangeStatusRequest(String status) {
}
