package com.operion.student.api;

import jakarta.validation.constraints.NotNull;


public record ReassignSectionRequest(@NotNull Long sectionId) {
}
