package com.operion.learning.api;

import java.time.Instant;

public record CreateQuizRequest(Long sectionId, Long subjectId, String title, String instructions, Instant opensAt, Instant closesAt) {
}
