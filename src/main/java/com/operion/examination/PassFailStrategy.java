package com.operion.examination;

/** How ReportCard.passed is decided at publish time. Configurable per organisation via
 * ExaminationSettings - schools vary on what "overall pass" means. Per #135. */
public enum PassFailStrategy {
	/** Fail overall if any single subject is below its ExamSchedule.passMarks. */
	PASS_EVERY_SUBJECT,
	/** Fail overall only if the aggregate percentage is below ExaminationSettings.minimumAggregatePercentage. */
	MINIMUM_AGGREGATE_PERCENTAGE,
	/** Fail overall if either condition above fails - the strictest option. */
	BOTH
}
