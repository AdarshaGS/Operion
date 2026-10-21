package com.operion.performance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Average manager and peer rating given in the period, as a percentage of the top rating. Self-ratings never count. */
@Component
public class FeedbackKpiProvider implements KpiMetricProvider {

	private final TeacherFeedbackRepository feedbackRepository;

	public FeedbackKpiProvider(TeacherFeedbackRepository feedbackRepository) {
		this.feedbackRepository = feedbackRepository;
	}

	@Override
	public Set<KpiMetric> supports() {
		return Set.of(KpiMetric.FEEDBACK_RATING);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, MetricValue> compute(KpiMetric metric, KpiPeriod period, Integer days) {
		Map<Long, int[]> tally = new HashMap<>();
		for (TeacherFeedback f : feedbackRepository.findBySourceInAndCreatedAtBetween(List.of(FeedbackSource.MANAGER, FeedbackSource.PEER),
				period.startInstant(), period.endInstant().minusNanos(1))) {
			int[] counts = tally.computeIfAbsent(f.getTeacher().getId(), k -> new int[2]);
			counts[0] += f.getRating();
			counts[1]++;
		}
		Map<Long, MetricValue> values = new HashMap<>();
		tally.forEach((personId, c) -> values.put(personId,
				new MetricValue(100.0 * c[0] / (c[1] * TeacherFeedback.MAX_RATING), c[1])));
		return values;
	}
}
