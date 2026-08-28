package com.operion.reporting.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.common.TenantContext;
import com.operion.reporting.ReportExecutionService;
import com.operion.reporting.ReportParameterType;
import com.operion.reporting.SavedReport;
import com.operion.reporting.SavedReportColumnRepository;
import com.operion.reporting.SavedReportParameterRepository;
import com.operion.reporting.SavedReportRepository;
import com.operion.reporting.SavedReportService;
import com.operion.reporting.SharePrincipalType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class SavedReportController {

	private final SavedReportService savedReportService;
	private final ReportExecutionService reportExecutionService;
	private final SavedReportRepository savedReportRepository;
	private final SavedReportParameterRepository parameterRepository;
	private final SavedReportColumnRepository columnRepository;

	public SavedReportController(SavedReportService savedReportService, ReportExecutionService reportExecutionService,
			SavedReportRepository savedReportRepository, SavedReportParameterRepository parameterRepository,
			SavedReportColumnRepository columnRepository) {
		this.savedReportService = savedReportService;
		this.reportExecutionService = reportExecutionService;
		this.savedReportRepository = savedReportRepository;
		this.parameterRepository = parameterRepository;
		this.columnRepository = columnRepository;
	}

	@PostMapping
	@RequirePermission("REPORT_CREATE")
	public SavedReportResponse create(@RequestBody SaveReportRequest request) {
		SavedReport report = savedReportService.createReport(request.name(), request.description(), request.sqlQuery(),
				toParameterInputs(request.parameters()), toColumnInputs(request.columns()));
		return toResponse(report);
	}

	@GetMapping
	public List<SavedReportResponse> list() {
		return savedReportService.listVisibleTo(actorId()).stream().map(this::toResponse).toList();
	}

	@GetMapping("/{id}")
	public SavedReportResponse detail(@PathVariable Long id) {
		SavedReport report = findReport(id);
		savedReportService.assertCanRun(report, actorId());
		return toResponse(report);
	}

	@PostMapping("/{id}/duplicate")
	public SavedReportResponse duplicate(@PathVariable Long id) {
		return toResponse(savedReportService.duplicate(findReport(id), actorId()));
	}

	@PutMapping("/{id}")
	public SavedReportResponse update(@PathVariable Long id, @RequestBody SaveReportRequest request) {
		SavedReport report = savedReportService.updateDefinition(findReport(id), actorId(), request.name(), request.description(),
				request.sqlQuery(), toParameterInputs(request.parameters()), toColumnInputs(request.columns()));
		return toResponse(report);
	}

	@PostMapping("/seed-standard")
	@RequirePermission("REPORT_MANAGE")
	public List<SavedReportResponse> seedStandard() {
		return savedReportService.seedStandardReports().stream().map(this::toResponse).toList();
	}

	@PostMapping("/{id}/publish")
	public SavedReportResponse publish(@PathVariable Long id) {
		return toResponse(savedReportService.publish(findReport(id), actorId()));
	}

	@PostMapping("/{id}/archive")
	public SavedReportResponse archive(@PathVariable Long id) {
		return toResponse(savedReportService.archive(findReport(id), actorId()));
	}

	@PostMapping("/{id}/share")
	public void share(@PathVariable Long id, @RequestBody ShareReportRequest request) {
		savedReportService.share(findReport(id), actorId(), SharePrincipalType.valueOf(request.principalType()), request.principalId(),
				request.canRun(), request.canEdit());
	}

	@PostMapping("/{id}/run")
	public ReportResultResponse run(@PathVariable Long id, @RequestBody RunReportRequest request) {
		SavedReport report = findReport(id);
		savedReportService.assertCanRun(report, actorId());
		return ReportResultResponse.from(reportExecutionService.run(report, request.parameters(), false));
	}

	@PostMapping("/{id}/export")
	@RequirePermission("REPORT_EXPORT")
	public ReportResultResponse export(@PathVariable Long id, @RequestBody RunReportRequest request) {
		SavedReport report = findReport(id);
		savedReportService.assertCanRun(report, actorId());
		return ReportResultResponse.from(reportExecutionService.run(report, request.parameters(), true));
	}

	private SavedReportResponse toResponse(SavedReport report) {
		return SavedReportResponse.from(report, parameterRepository.findBySavedReportIdOrderBySortOrder(report.getId()),
				columnRepository.findBySavedReportIdOrderBySortOrder(report.getId()));
	}

	private List<SavedReportService.ParameterInput> toParameterInputs(List<ReportParameterRequest> parameters) {
		return parameters.stream()
				.map(p -> new SavedReportService.ParameterInput(p.name(), ReportParameterType.valueOf(p.type()), p.label(), p.sortOrder()))
				.toList();
	}

	private List<SavedReportService.ColumnInput> toColumnInputs(List<ReportColumnRequest> columns) {
		return columns.stream().map(c -> new SavedReportService.ColumnInput(c.sourceColumn(), c.label(), c.sortOrder())).toList();
	}

	private SavedReport findReport(Long id) {
		return savedReportRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No report with id " + id));
	}

	private Long actorId() {
		return TenantContext.getActorId();
	}
}
