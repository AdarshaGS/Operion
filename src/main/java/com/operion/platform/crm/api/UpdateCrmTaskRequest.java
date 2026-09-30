package com.operion.platform.crm.api;

import java.time.Instant;

/** Every field optional - null leaves the current value unchanged. */
public record UpdateCrmTaskRequest(String title, Instant dueAt, Long assigneeId) {
}
