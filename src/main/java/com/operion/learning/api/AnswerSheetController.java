package com.operion.learning.api;

import java.time.Instant;
import java.util.List;

import com.operion.common.TenantContext;
import com.operion.learning.AnswerSheetService;
import com.operion.learning.LearningAccessService;
import com.operion.learning.api.AnswerSheetResponses.MarkRequest;
import com.operion.learning.api.AnswerSheetResponses.RosterRowResponse;
import com.operion.learning.api.AnswerSheetResponses.ScheduleResponse;
import com.operion.learning.api.AnswerSheetResponses.SheetResponse;
import com.operion.learning.api.AnswerSheetResponses.UploadRequest;
import com.operion.storage.AssetStorageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Not permission-gated - shared by admins and portal teachers, scoped by LearningAccessService. */
@RestController
@RequestMapping("/api/v1/learning/answer-sheets")
public class AnswerSheetController {

	private final AnswerSheetService sheetService;
	private final LearningAccessService accessService;
	private final AssetStorageService storage;

	public AnswerSheetController(AnswerSheetService sheetService, LearningAccessService accessService, AssetStorageService storage) {
		this.sheetService = sheetService;
		this.accessService = accessService;
		this.storage = storage;
	}

	@GetMapping("/schedules")
	public List<ScheduleResponse> schedules() {
		var actor = accessService.resolve(TenantContext.getActorId());
		return sheetService.schedules(actor).stream().map(ScheduleResponse::from).toList();
	}

	@GetMapping("/schedules/{scheduleId}/roster")
	public List<RosterRowResponse> roster(@PathVariable Long scheduleId) {
		var actor = accessService.resolve(TenantContext.getActorId());
		return sheetService.roster(actor, scheduleId).stream().map(RosterRowResponse::from).toList();
	}

	@PostMapping("/schedules/{scheduleId}/upload")
	public SheetResponse upload(@PathVariable Long scheduleId, @RequestBody UploadRequest request) {
		var actor = accessService.resolve(TenantContext.getActorId());
		var sheet = sheetService.upload(actor, scheduleId, request.enrollmentId(), request.pageRefs());
		return SheetResponse.from(sheetService.detail(actor, sheet.getId()), storage);
	}

	@GetMapping("/{id}")
	public SheetResponse get(@PathVariable Long id) {
		var actor = accessService.resolve(TenantContext.getActorId());
		return SheetResponse.from(sheetService.detail(actor, id), storage);
	}

	@PutMapping("/{id}/marking")
	public SheetResponse mark(@PathVariable Long id, @RequestBody MarkRequest request) {
		var actor = accessService.resolve(TenantContext.getActorId());
		var annotations = request.annotations() == null ? List.<AnswerSheetService.AnnotationInput>of()
				: request.annotations().stream()
						.map(a -> new AnswerSheetService.AnnotationInput(a.pageNumber(), a.kind(), a.x(), a.y(), a.label(), a.marks())).toList();
		return SheetResponse.from(sheetService.mark(actor, id, annotations, request.totalMarks(), request.overallRemarks(), Instant.now()), storage);
	}

	@PostMapping("/{id}/release")
	public SheetResponse release(@PathVariable Long id) {
		var actor = accessService.resolve(TenantContext.getActorId());
		sheetService.release(actor, id, Instant.now());
		return SheetResponse.from(sheetService.detail(actor, id), storage);
	}
}
