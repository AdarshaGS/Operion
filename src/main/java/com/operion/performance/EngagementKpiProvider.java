package com.operion.performance;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.operion.identity.Person;
import com.operion.learning.AnswerSheetRepository;
import com.operion.learning.AnswerSheetStatus;
import com.operion.learning.HomeworkRepository;
import com.operion.learning.HomeworkStatus;
import com.operion.learning.LearningMaterialRepository;
import com.operion.learning.MaterialStatus;
import com.operion.learning.QuizRepository;
import com.operion.learning.QuizStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** How much learning content a teacher put out, as a count per 30 days so periods of different lengths compare. */
@Component
public class EngagementKpiProvider implements KpiMetricProvider {

	private final HomeworkRepository homeworkRepository;
	private final QuizRepository quizRepository;
	private final LearningMaterialRepository materialRepository;
	private final AnswerSheetRepository answerSheetRepository;

	public EngagementKpiProvider(HomeworkRepository homeworkRepository, QuizRepository quizRepository,
			LearningMaterialRepository materialRepository, AnswerSheetRepository answerSheetRepository) {
		this.homeworkRepository = homeworkRepository;
		this.quizRepository = quizRepository;
		this.materialRepository = materialRepository;
		this.answerSheetRepository = answerSheetRepository;
	}

	@Override
	public Set<KpiMetric> supports() {
		return Set.of(KpiMetric.HOMEWORK_ISSUED, KpiMetric.QUIZZES_PUBLISHED, KpiMetric.MATERIALS_SHARED, KpiMetric.ANSWER_SHEETS_RELEASED);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, MetricValue> compute(KpiMetric metric, KpiPeriod period, Integer days) {
		var start = period.startInstant();
		var end = period.endInstant().minusNanos(1);
		Map<Long, Integer> counts = new HashMap<>();
		switch (metric) {
			case HOMEWORK_ISSUED -> homeworkRepository.findByStatusAndCreatedAtBetween(HomeworkStatus.ACTIVE, start, end)
					.forEach(h -> count(counts, h.getAssignedBy()));
			case QUIZZES_PUBLISHED -> quizRepository.findByStatusAndOpensAtBetween(QuizStatus.PUBLISHED, start, end)
					.forEach(q -> count(counts, q.getAuthor()));
			case MATERIALS_SHARED -> materialRepository.findByStatusAndCreatedAtBetween(MaterialStatus.ACTIVE, start, end)
					.forEach(m -> count(counts, m.getUploadedBy()));
			case ANSWER_SHEETS_RELEASED -> answerSheetRepository.findByStatusAndReleasedAtBetween(AnswerSheetStatus.RELEASED, start, end)
					.forEach(s -> count(counts, s.getMarkedBy()));
			default -> throw new IllegalArgumentException("Not an engagement metric: " + metric);
		}
		Map<Long, MetricValue> values = new HashMap<>();
		counts.forEach((personId, count) -> values.put(personId, MetricValue.perThirtyDays(count, period)));
		return values;
	}

	private static void count(Map<Long, Integer> counts, Person person) {
		if (person != null) {
			counts.merge(person.getId(), 1, Integer::sum);
		}
	}
}
