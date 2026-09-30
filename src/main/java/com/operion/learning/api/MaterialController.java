package com.operion.learning.api;

import java.util.List;

import com.operion.common.TenantContext;
import com.operion.learning.LearningAccessService;
import com.operion.learning.LearningMaterialService;
import com.operion.storage.AssetStorageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Not permission-gated - shared by admins and portal teachers, scoped by LearningAccessService. */
@RestController
@RequestMapping("/api/v1/learning/materials")
public class MaterialController {

	private final LearningMaterialService materialService;
	private final LearningAccessService accessService;
	private final AssetStorageService storage;

	public MaterialController(LearningMaterialService materialService, LearningAccessService accessService, AssetStorageService storage) {
		this.materialService = materialService;
		this.accessService = accessService;
		this.storage = storage;
	}

	@GetMapping
	public List<MaterialResponse> list(@RequestParam(required = false) Long classId) {
		var actor = accessService.resolve(TenantContext.getActorId());
		return materialService.list(actor, classId).stream().map(m -> MaterialResponse.from(m, storage)).toList();
	}

	@PostMapping
	public MaterialResponse create(@RequestBody CreateMaterialRequest request) {
		var actor = accessService.resolve(TenantContext.getActorId());
		return MaterialResponse.from(materialService.add(actor, request.classId(), request.subjectId(), request.title(),
				request.description(), request.kind(), request.attachmentRef(), request.url()), storage);
	}

	@PostMapping("/{id}/archive")
	public MaterialResponse archive(@PathVariable Long id) {
		var actor = accessService.resolve(TenantContext.getActorId());
		return MaterialResponse.from(materialService.archive(actor, id), storage);
	}
}
