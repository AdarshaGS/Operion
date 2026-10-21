package com.operion.performance;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.operion.authorization.MembershipStatus;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.examination.MarksEntry;
import com.operion.examination.MarksEntryRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Whether marks were entered within the configured number of days after the exam. Marks never entered leave
 * no record, so they cannot be counted against anyone. The entry is credited to whoever first created it.
 */
@Component
public class MarksEntryKpiProvider implements KpiMetricProvider {

	private final MarksEntryRepository marksRepository;
	private final OrganisationMembershipRepository membershipRepository;

	public MarksEntryKpiProvider(MarksEntryRepository marksRepository, OrganisationMembershipRepository membershipRepository) {
		this.marksRepository = marksRepository;
		this.membershipRepository = membershipRepository;
	}

	@Override
	public Set<KpiMetric> supports() {
		return Set.of(KpiMetric.MARKS_ENTRY_ON_TIME);
	}

	@Override
	@Transactional(readOnly = true)
	public Map<Long, MetricValue> compute(KpiMetric metric, KpiPeriod period, Integer days) {
		int grace = days == null ? 0 : days;
		Map<Long, Long> personByUser = new HashMap<>();
		Map<Long, int[]> tally = new HashMap<>();
		for (MarksEntry entry : marksRepository.findByExamScheduleExamDateBetween(period.from(), period.to())) {
			if (entry.getCreatedBy() == null || entry.getCreatedAt() == null) {
				continue;
			}
			Long personId = personByUser.computeIfAbsent(entry.getCreatedBy(), userId -> membershipRepository.findByUserId(userId).stream()
					.filter(m -> m.getStatus() == MembershipStatus.ACTIVE).findFirst().map(m -> m.getPerson().getId()).orElse(-1L));
			if (personId < 0) {
				continue;
			}
			LocalDate enteredOn = entry.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate();
			int[] counts = tally.computeIfAbsent(personId, k -> new int[2]);
			counts[1]++;
			if (!enteredOn.isAfter(entry.getExamSchedule().getExamDate().plusDays(grace))) {
				counts[0]++;
			}
		}
		Map<Long, MetricValue> values = new HashMap<>();
		tally.forEach((personId, counts) -> values.put(personId, MetricValue.ratio(counts[0], counts[1])));
		return values;
	}
}
