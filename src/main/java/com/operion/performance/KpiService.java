package com.operion.performance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.operion.academic.TeacherAssignment;
import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentStatus;
import com.operion.common.InvalidRequestException;
import com.operion.identity.Person;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Teacher scorecard, computed on read from existing records so it can never be stale. Only metrics the
 * organisation has switched on are measured. A teacher's overall score is the weighted average of the
 * scores they have data for - a metric with no records for them is left out, not counted as zero.
 */
@Service
public class KpiService {

	/** One metric's configuration as the organisation currently has it (defaults when never configured). */
	public record Definition(KpiMetric metric, boolean enabled, int weight, BigDecimal target, Integer paramDays) {
	}

	/** {@code previous} is only filled for outcome metrics, which are scored against it. */
	public record Cell(KpiMetric metric, Double value, int sample, Double score, Double previous) {
	}

	public record TeacherRow(Person person, Double overallScore, List<Cell> cells) {
	}

	public record Scorecard(LocalDate from, LocalDate to, List<Definition> metrics, List<TeacherRow> teachers) {
	}

	private final KpiDefinitionRepository definitionRepository;
	private final TeacherAssignmentRepository assignmentRepository;
	private final List<KpiMetricProvider> providers;

	public KpiService(KpiDefinitionRepository definitionRepository, TeacherAssignmentRepository assignmentRepository,
			List<KpiMetricProvider> providers) {
		this.definitionRepository = definitionRepository;
		this.assignmentRepository = assignmentRepository;
		this.providers = providers;
	}

	@Transactional(readOnly = true)
	public List<Definition> definitions() {
		Map<KpiMetric, KpiDefinition> stored = new EnumMap<>(KpiMetric.class);
		definitionRepository.findAll().forEach(d -> stored.put(d.getMetric(), d));
		List<Definition> all = new ArrayList<>();
		for (KpiMetric metric : KpiMetric.values()) {
			KpiDefinition d = stored.get(metric);
			all.add(d == null
					? new Definition(metric, false, KpiDefinition.DEFAULT_WEIGHT, BigDecimal.valueOf(metric.defaultTarget()), metric.defaultDays())
					: new Definition(metric, d.isEnabled(), d.getWeight(), d.getTarget(), d.getParamDays()));
		}
		return all;
	}

	@Transactional
	public Definition configure(KpiMetric metric, boolean enabled, int weight, BigDecimal target, Integer paramDays) {
		KpiDefinition definition = definitionRepository.findByMetric(metric).orElseGet(() -> new KpiDefinition(metric));
		definition.configure(enabled, weight, target, paramDays);
		KpiDefinition saved = definitionRepository.save(definition);
		return new Definition(saved.getMetric(), saved.isEnabled(), saved.getWeight(), saved.getTarget(), saved.getParamDays());
	}

	@Transactional(readOnly = true)
	public Scorecard scorecard(LocalDate from, LocalDate to) {
		if (to.isBefore(from)) {
			throw new InvalidRequestException("The period end cannot be before its start");
		}
		KpiPeriod period = new KpiPeriod(from, to);
		List<Definition> enabled = definitions().stream().filter(Definition::enabled).toList();
		Map<Long, Person> teachers = assignmentRepository.findByStatus(TeacherAssignmentStatus.ACTIVE).stream()
				.map(TeacherAssignment::getTeacherPerson).collect(Collectors.toMap(Person::getId, p -> p, (a, b) -> a));

		Map<KpiMetric, Map<Long, MetricValue>> measured = new EnumMap<>(KpiMetric.class);
		Map<KpiMetric, Map<Long, MetricValue>> baseline = new EnumMap<>(KpiMetric.class);
		for (Definition definition : enabled) {
			KpiMetric metric = definition.metric();
			KpiMetricProvider provider = providers.stream().filter(p -> p.supports().contains(metric)).findFirst()
					.orElseThrow(() -> new IllegalStateException("No provider for metric " + metric));
			measured.put(metric, provider.compute(metric, period, definition.paramDays()));
			if (metric.kind() == KpiMetric.Kind.OUTCOME) {
				baseline.put(metric, provider.compute(metric, period.previous(), definition.paramDays()));
			}
		}

		List<TeacherRow> rows = teachers.values().stream()
				.sorted(Comparator.comparing(Person::getLastName, Comparator.nullsLast(String::compareToIgnoreCase))
						.thenComparing(Person::getFirstName, Comparator.nullsLast(String::compareToIgnoreCase)))
				.map(person -> row(person, enabled, measured, baseline)).toList();
		return new Scorecard(from, to, enabled, rows);
	}

	/** The scorecard narrowed to one teacher; no row when that person has no active class assignment. */
	@Transactional(readOnly = true)
	public Scorecard scorecardFor(Long personId, LocalDate from, LocalDate to) {
		Scorecard all = scorecard(from, to);
		return new Scorecard(all.from(), all.to(), all.metrics(),
				all.teachers().stream().filter(t -> t.person().getId().equals(personId)).toList());
	}

	private static TeacherRow row(Person person, List<Definition> enabled, Map<KpiMetric, Map<Long, MetricValue>> measured,
			Map<KpiMetric, Map<Long, MetricValue>> baseline) {
		List<Cell> cells = new ArrayList<>();
		double weightedScore = 0;
		int weightSum = 0;
		for (Definition definition : enabled) {
			KpiMetric metric = definition.metric();
			MetricValue value = measured.get(metric).get(person.getId());
			if (value == null) {
				cells.add(new Cell(metric, null, 0, null, null));
				continue;
			}
			Double score;
			Double previous = null;
			if (metric.kind() == KpiMetric.Kind.OUTCOME) {
				MetricValue before = baseline.get(metric).get(person.getId());
				previous = before == null ? null : before.value();
				score = previous == null || previous <= 0 ? null : Math.min(100.0, value.value() / previous * 100.0);
			} else {
				score = Math.min(100.0, value.value() / definition.target().doubleValue() * 100.0);
			}
			cells.add(new Cell(metric, value.value(), value.sample(), score, previous));
			if (score != null) {
				weightedScore += score * definition.weight();
				weightSum += definition.weight();
			}
		}
		return new TeacherRow(person, weightSum == 0 ? null : weightedScore / weightSum, cells);
	}
}
