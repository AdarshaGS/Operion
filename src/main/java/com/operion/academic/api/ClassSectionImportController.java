package com.operion.academic.api;

import java.util.List;

import com.operion.academic.ClassSectionImportService;
import com.operion.academic.SectionRepository;
import com.operion.authorization.RequirePermission;
import com.operion.common.imports.ImportRowResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bulk import/export spans many classes at once, unlike SectionController's per-class
 * CRUD (nested under /school-classes/{classId}/sections) - so this gets its own
 * top-level path rather than bolting onto that nested one. Reuses Section's own
 * CLASS_VIEW/CLASS_MANAGE permission codes, same reasoning as SectionController's
 * javadoc (no dedicated Section permission codes exist).
 */
@RestController
@RequestMapping("/api/v1/class-sections")
@RequirePermission("CLASS_VIEW")
public class ClassSectionImportController {

	private final ClassSectionImportService classSectionImportService;
	private final SectionRepository sectionRepository;

	public ClassSectionImportController(ClassSectionImportService classSectionImportService, SectionRepository sectionRepository) {
		this.classSectionImportService = classSectionImportService;
		this.sectionRepository = sectionRepository;
	}

	@PostMapping("/import")
	@RequirePermission("CLASS_MANAGE")
	public List<ImportRowResult> importFile(@RequestParam("file") MultipartFile file,
			@RequestParam(name = "validateOnly", defaultValue = "false") boolean validateOnly) {
		return classSectionImportService.importFile(file, validateOnly);
	}

	@GetMapping("/export")
	public List<ClassSectionExportResponse> export() {
		return sectionRepository.findAll().stream().map(ClassSectionExportResponse::from).toList();
	}
}
