package com.operion.common.imports;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** No @RequirePermission: a cross-cutting read-only lookup, same convention as
 * Campus/AcademicYear listing - reachable by any authenticated org member. */
@RestController
@RequestMapping("/api/v1/imports")
public class ImportHistoryController {

	private final ImportRunRepository importRunRepository;

	public ImportHistoryController(ImportRunRepository importRunRepository) {
		this.importRunRepository = importRunRepository;
	}

	@GetMapping("/history")
	public List<ImportRunResponse> history() {
		return importRunRepository.findAllByOrderByCreatedAtDesc().stream().map(ImportRunResponse::from).toList();
	}
}
