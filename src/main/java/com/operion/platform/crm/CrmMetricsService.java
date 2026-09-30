package com.operion.platform.crm;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class CrmMetricsService {

	private final LeadRepository leadRepository;
	private final CrmTaskRepository crmTaskRepository;

	public CrmMetricsService(LeadRepository leadRepository, CrmTaskRepository crmTaskRepository) {
		this.leadRepository = leadRepository;
		this.crmTaskRepository = crmTaskRepository;
	}

	public Metrics current() {
		Map<LeadStage, Long> byStage = new EnumMap<>(LeadStage.class);
		for (LeadStage stage : LeadStage.values()) {
			byStage.put(stage, 0L);
		}
		for (Object[] row : leadRepository.countByStage()) {
			byStage.put((LeadStage) row[0], (Long) row[1]);
		}
		long won = byStage.get(LeadStage.WON);
		long lost = byStage.get(LeadStage.LOST);
		// Win rate is over closed leads only (WON / (WON + LOST)); null until at least one lead has closed.
		Double winRate = won + lost == 0 ? null : (double) won / (won + lost);
		return new Metrics(byStage, won, lost, winRate, crmTaskRepository.countByCompletedAtIsNull(),
				crmTaskRepository.countByCompletedAtIsNullAndDueAtBefore(Instant.now()));
	}

	public record Metrics(Map<LeadStage, Long> leadsByStage, long wonCount, long lostCount, Double winRate,
			long openTaskCount, long overdueTaskCount) {
	}
}
