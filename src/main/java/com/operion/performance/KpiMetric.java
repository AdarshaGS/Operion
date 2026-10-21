package com.operion.performance;

/**
 * The built-in metrics a tenant can switch on for its teachers. Adding a metric means adding a constant
 * here and teaching a {@link KpiMetricProvider} to compute it.
 */
public enum KpiMetric {

	STAFF_PUNCTUALITY("Punctuality", "Share of attended days the teacher was not marked late.", Unit.PERCENT, Kind.STANDARD, false),
	STAFF_PRESENCE("Presence", "Share of working days the teacher was present, not counting approved leave.", Unit.PERCENT, Kind.STANDARD, false),
	LESSON_PLAN_ON_TIME("Lesson plans on time", "Share of lesson plans submitted on or before the day they are planned for.", Unit.PERCENT, Kind.STANDARD, false),
	LESSON_PLAN_APPROVAL("Lesson plan approval", "Share of reviewed lesson plans that were approved (a rejected plan that is fixed and approved counts as approved).", Unit.PERCENT, Kind.STANDARD, false),
	REGISTER_SAME_DAY("Attendance registers on the day", "Share of a class teacher's attendance registers submitted on the day they are for. Counted against the section's current class teacher; registers still open after their day count as late.", Unit.PERCENT, Kind.STANDARD, false),
	MARKS_ENTRY_ON_TIME("Marks entered on time", "Share of marks entered within the set number of days after the exam. Marks never entered are not counted.", Unit.PERCENT, Kind.STANDARD, true),
	HOMEWORK_ISSUED("Homework issued", "Active homework assigned, per 30 days.", Unit.PER_30_DAYS, Kind.STANDARD, false),
	QUIZZES_PUBLISHED("Quizzes published", "Published quizzes that opened in the period, per 30 days.", Unit.PER_30_DAYS, Kind.STANDARD, false),
	MATERIALS_SHARED("Materials shared", "Active learning materials uploaded, per 30 days.", Unit.PER_30_DAYS, Kind.STANDARD, false),
	ANSWER_SHEETS_RELEASED("Answer sheets released", "Marked answer sheets released to students, per 30 days.", Unit.PER_30_DAYS, Kind.STANDARD, false),
	OUTCOME_PASS_RATE("Pass rate", "Share of students who passed the exams of the sections and subjects the teacher teaches. Scored against the previous period, not a target; shared when several teachers teach a section.", Unit.PERCENT, Kind.OUTCOME, false),
	OUTCOME_AVERAGE_MARKS("Average marks", "Average marks percentage in the exams of the sections and subjects the teacher teaches. Scored against the previous period, not a target; shared when several teachers teach a section.", Unit.PERCENT, Kind.OUTCOME, false),
	FEEDBACK_RATING("Reviewer rating", "Average manager and peer rating (out of 5) given in the period. Self-ratings are not counted.", Unit.PERCENT, Kind.STANDARD, false);

	public enum Unit {
		PERCENT, PER_30_DAYS
	}

	public enum Kind {
		/** Scored as value / target. */
		STANDARD,
		/** Depends on who is in the class, so scored against the teacher's own previous period instead of a target. */
		OUTCOME
	}

	private final String label;
	private final String description;
	private final Unit unit;
	private final Kind kind;
	private final boolean usesDays;

	KpiMetric(String label, String description, Unit unit, Kind kind, boolean usesDays) {
		this.label = label;
		this.description = description;
		this.unit = unit;
		this.kind = kind;
		this.usesDays = usesDays;
	}

	public String label() {
		return label;
	}

	public String description() {
		return description;
	}

	public Unit unit() {
		return unit;
	}

	public Kind kind() {
		return kind;
	}

	/** Whether this metric reads the definition's day threshold. */
	public boolean usesDays() {
		return usesDays;
	}

	public int maxTarget() {
		return unit == Unit.PERCENT ? 100 : 999;
	}

	public int defaultTarget() {
		return unit == Unit.PERCENT ? 100 : 10;
	}

	public Integer defaultDays() {
		return usesDays ? 7 : null;
	}
}
