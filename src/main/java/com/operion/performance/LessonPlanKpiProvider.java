package com.operion.performance;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.operion.learning.LessonPlan;
import com.operion.learning.LessonPlanRepository;
import com.operion.learning.LessonPlanStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Planning discipline from the lesson plans a teacher authored, selected by the date each plan is for. */
@Component
public class LessonPlanKpiProvider implements KpiMetricProvider {

	private final LessonPlanRepository planRepository;

	public LessonPlanKpiProvider(LessonPlanRepository planRepository) {
		this.planRepository = planRepository;
	}

	@Override
	public Set<KpiMetric> supports() {
		return Set.of(KpiMetric.LESSON_PLAN_ON_TIME, KpiMetric.LESSON_PLAN_APPROVAL);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, MetricValue> compute(KpiMetric metric, KpiPeriod period, Integer days) {
		Map<Long, int[]> tally = new HashMap<>();
		for (LessonPlan plan : planRepository.findByPlanDateBetween(period.from(), period.to())) {
			int[] counts = tally.computeIfAbsent(plan.getAuthor().getId(), k -> new int[2]);
			if (metric == KpiMetric.LESSON_PLAN_ON_TIME) {
				// submittedAt is reset on resubmission, so this reflects the latest submission
				if (plan.getSubmittedAt() != null) {
					counts[1]++;
					LocalDate submittedOn = plan.getSubmittedAt().atZone(ZoneId.systemDefault()).toLocalDate();
					if (!submittedOn.isAfter(plan.getPlanDate())) {
						counts[0]++;
					}
				}
			} else if (plan.getStatus() == LessonPlanStatus.APPROVED || plan.getStatus() == LessonPlanStatus.REJECTED) {
				counts[1]++;
				if (plan.getStatus() == LessonPlanStatus.APPROVED) {
					counts[0]++;
				}
			}
		}
		Map<Long, MetricValue> values = new HashMap<>();
		tally.forEach((personId, counts) -> {
			if (counts[1] > 0) {
				values.put(personId, MetricValue.ratio(counts[0], counts[1]));
			}
		});
		return values;
	}
}
