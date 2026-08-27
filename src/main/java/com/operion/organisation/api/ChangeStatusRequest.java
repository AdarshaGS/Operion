package com.operion.organisation.api;

/** Shared across Department/Designation status-change endpoints - each entity parses
 * `status` against its own enum, this DTO only carries the raw string across the wire. */
public record ChangeStatusRequest(String status) {
}
