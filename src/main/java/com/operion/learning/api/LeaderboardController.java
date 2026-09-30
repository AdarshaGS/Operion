package com.operion.learning.api;

import java.time.Instant;
import java.util.List;

import com.operion.common.TenantContext;
import com.operion.learning.LeaderboardService;
import com.operion.learning.LearningAccessService;
import com.operion.learning.api.LeaderboardResponses.GrantRewardRequest;
import com.operion.learning.api.LeaderboardResponses.RewardResponse;
import com.operion.learning.api.LeaderboardResponses.StudentEntryResponse;
import com.operion.learning.api.LeaderboardResponses.TeacherEntryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Not permission-gated - shared by admins and portal teachers, scoped by LearningAccessService. */
@RestController
@RequestMapping("/api/v1/learning")
public class LeaderboardController {

	private final LeaderboardService leaderboardService;
	private final LearningAccessService accessService;

	public LeaderboardController(LeaderboardService leaderboardService, LearningAccessService accessService) {
		this.leaderboardService = leaderboardService;
		this.accessService = accessService;
	}

	@GetMapping("/leaderboard/students")
	public List<StudentEntryResponse> students(@RequestParam Long sectionId, @RequestParam LeaderboardService.Track track) {
		var actor = accessService.resolve(TenantContext.getActorId());
		return leaderboardService.studentBoard(actor, sectionId, track).stream().map(StudentEntryResponse::from).toList();
	}

	@GetMapping("/leaderboard/teachers")
	public List<TeacherEntryResponse> teachers() {
		var actor = accessService.resolve(TenantContext.getActorId());
		return leaderboardService.teacherBoard(actor).stream().map(TeacherEntryResponse::from).toList();
	}

	@GetMapping("/sections/students")
	public List<LeaderboardResponses.SectionStudentResponse> sectionStudents(@RequestParam Long sectionId) {
		var actor = accessService.resolve(TenantContext.getActorId());
		return leaderboardService.sectionStudents(actor, sectionId).stream().map(LeaderboardResponses.SectionStudentResponse::from).toList();
	}

	@GetMapping("/rewards")
	public List<RewardResponse> rewards(@RequestParam Long sectionId) {
		var actor = accessService.resolve(TenantContext.getActorId());
		return leaderboardService.rewardsInSection(actor, sectionId).stream().map(RewardResponse::from).toList();
	}

	@PostMapping("/rewards")
	public RewardResponse grant(@RequestBody GrantRewardRequest request) {
		var actor = accessService.resolve(TenantContext.getActorId());
		return RewardResponse.from(leaderboardService.grant(actor, request.enrollmentId(), request.title(), request.points(), request.reason(), Instant.now()));
	}
}
