package com.operion.learning.api;

import java.util.List;

import com.operion.learning.QuizService;

/** subjectId is only read for question-bank items; a quiz question takes its quiz's subject. */
public record QuestionRequest(Long subjectId, String text, int marks, List<QuizService.OptionInput> options) {
}
