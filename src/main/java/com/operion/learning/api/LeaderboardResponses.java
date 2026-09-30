package com.operion.learning.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.operion.learning.LeaderboardService;
import com.operion.learning.Labels;
import com.operion.learning.StudentReward;

/** Response/request shapes for leaderboards and rewards (#231). */
public final class LeaderboardResponses {

	private LeaderboardResponses() {
	}

	public record StudentEntryResponse(int rank, Long studentId, Long enrollmentId, String studentName, Integer rollNumber, double score) {

		public static StudentEntryResponse from(LeaderboardService.BoardEntry e) {
			return new StudentEntryResponse(e.rank(), e.enrollment().getStudent().getId(), e.enrollment().getId(),
					Labels.personName(e.enrollment().getStudent().getPerson()), e.enrollment().getRollNumber(), e.score());
		}
	}

	public record TeacherEntryResponse(int rank, String teacherName, int points, int homeworkAssigned, int quizzesPublished,
			int materialsShared, int lessonPlansApproved, int sheetsReleased) {

		public static TeacherEntryResponse from(LeaderboardService.TeacherEntry e) {
			return new TeacherEntryResponse(e.rank(), Labels.personName(e.person()), e.points(), e.homeworkAssigned(), e.quizzesPublished(),
					e.materialsShared(), e.lessonPlansApproved(), e.sheetsReleased());
		}
	}

	public record RewardResponse(Long id, Long studentId, String studentName, String title, int points, String reason,
			String grantedByName, Instant grantedAt) {

		public static RewardResponse from(StudentReward r) {
			return new RewardResponse(r.getId(), r.getStudent().getId(), Labels.personName(r.getStudent().getPerson()), r.getTitle(),
					r.getPoints(), r.getReason(), Labels.personName(r.getGrantedBy()), r.getGrantedAt());
		}
	}

	public record SectionStudentResponse(Long enrollmentId, Long studentId, String studentName, Integer rollNumber) {

		public static SectionStudentResponse from(com.operion.student.StudentEnrollment e) {
			return new SectionStudentResponse(e.getId(), e.getStudent().getId(), Labels.personName(e.getStudent().getPerson()), e.getRollNumber());
		}
	}

	public record GrantRewardRequest(Long enrollmentId, String title, int points, String reason) {
	}

	public record StandingResponse(Integer rank, int of, Double score) {
	}

	/** A child's own standing per track plus their rewards - never other students' names. */
	public record ChildStandingResponse(Map<String, StandingResponse> standings, int rewardPoints, List<RewardResponse> rewards) {

		public static ChildStandingResponse from(Map<LeaderboardService.Track, LeaderboardService.Standing> standings, List<StudentReward> rewards) {
			Map<String, StandingResponse> mapped = new java.util.LinkedHashMap<>();
			standings.forEach((track, s) -> mapped.put(track.name(), new StandingResponse(s.rank(), s.of(), s.score())));
			return new ChildStandingResponse(mapped, rewards.stream().mapToInt(StudentReward::getPoints).sum(),
					rewards.stream().map(RewardResponse::from).toList());
		}
	}
}
