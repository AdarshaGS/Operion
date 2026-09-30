package com.operion.regulatory.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.regulatory.RegulatoryEntityType;
import com.operion.regulatory.RegulatoryExportSchema;
import com.operion.regulatory.RegulatoryExportSchemaRepository;
import com.operion.regulatory.RegulatoryExportService;
import com.operion.regulatory.RegulatoryExportService.FieldSpec;
import com.operion.regulatory.RegulatoryExportSources;
import com.operion.regulatory.RegulatoryExportStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/regulatory-export-schemas")
@RequirePermission("REGULATORY_EXPORT_MANAGE")
public class RegulatoryExportController {

	private final RegulatoryExportService exportService;
	private final RegulatoryExportSchemaRepository schemaRepository;

	public RegulatoryExportController(RegulatoryExportService exportService, RegulatoryExportSchemaRepository schemaRepository) {
		this.exportService = exportService;
		this.schemaRepository = schemaRepository;
	}

	@GetMapping
	public List<RegulatoryExportSchemaResponse> list() {
		return schemaRepository.findAll().stream().map(this::toResponse).toList();
	}

	@GetMapping("/source-fields")
	public List<RegulatoryExportSourceFieldResponse> sourceFields(@RequestParam String entityType) {
		return RegulatoryExportSources.catalog(RegulatoryEntityType.valueOf(entityType)).stream()
				.map(RegulatoryExportSourceFieldResponse::from).toList();
	}

	@GetMapping("/{id}")
	public RegulatoryExportSchemaResponse get(@PathVariable Long id) {
		return toResponse(load(id));
	}

	@PostMapping
	public RegulatoryExportSchemaResponse create(@RequestBody CreateRegulatoryExportSchemaRequest request) {
		return toResponse(exportService.createSchema(request.code(), request.name(), request.description(),
				RegulatoryEntityType.valueOf(request.entityType())));
	}

	@PatchMapping("/{id}")
	public RegulatoryExportSchemaResponse update(@PathVariable Long id, @RequestBody UpdateRegulatoryExportSchemaRequest request) {
		return toResponse(exportService.updateSchema(load(id), request.name(), request.description()));
	}

	@PutMapping("/{id}/fields")
	public RegulatoryExportSchemaResponse replaceFields(@PathVariable Long id, @RequestBody List<RegulatoryExportFieldRequest> request) {
		RegulatoryExportSchema schema = load(id);
		exportService.replaceFields(schema, request.stream()
				.map(f -> new FieldSpec(f.columnHeader(), f.sourceField(), f.defaultValue())).toList());
		return toResponse(schema);
	}

	@PostMapping("/{id}/status")
	public RegulatoryExportSchemaResponse changeStatus(@PathVariable Long id, @RequestBody RegulatoryExportStatusRequest request) {
		return toResponse(exportService.changeStatus(load(id), RegulatoryExportStatus.valueOf(request.status())));
	}

	@GetMapping(value = "/{id}/export", produces = "text/csv")
	public ResponseEntity<String> export(@PathVariable Long id) {
		RegulatoryExportSchema schema = load(id);
		String csv = exportService.exportCsv(schema);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + schema.getCode() + ".csv\"")
				.contentType(MediaType.parseMediaType("text/csv"))
				.body(csv);
	}

	private RegulatoryExportSchema load(Long id) {
		return schemaRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("No regulatory export schema with id " + id));
	}

	private RegulatoryExportSchemaResponse toResponse(RegulatoryExportSchema schema) {
		return RegulatoryExportSchemaResponse.from(schema, exportService.fieldsOf(schema));
	}
}
