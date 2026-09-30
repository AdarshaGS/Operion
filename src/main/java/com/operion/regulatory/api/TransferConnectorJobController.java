package com.operion.regulatory.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.regulatory.RegulatoryExportSchema;
import com.operion.regulatory.RegulatoryExportSchemaRepository;
import com.operion.regulatory.TransferConnectorJob;
import com.operion.regulatory.TransferConnectorJobRepository;
import com.operion.regulatory.TransferConnectorJobStatus;
import com.operion.regulatory.TransferConnectorService;
import com.operion.student.StudentExit;
import com.operion.student.StudentExitRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfer-connector-jobs")
@RequirePermission("REGULATORY_EXPORT_MANAGE")
public class TransferConnectorJobController {

	private final TransferConnectorService connectorService;
	private final TransferConnectorJobRepository jobRepository;
	private final StudentExitRepository studentExitRepository;
	private final RegulatoryExportSchemaRepository schemaRepository;

	public TransferConnectorJobController(TransferConnectorService connectorService, TransferConnectorJobRepository jobRepository,
			StudentExitRepository studentExitRepository, RegulatoryExportSchemaRepository schemaRepository) {
		this.connectorService = connectorService;
		this.jobRepository = jobRepository;
		this.studentExitRepository = studentExitRepository;
		this.schemaRepository = schemaRepository;
	}

	@GetMapping
	public List<TransferConnectorJobResponse> list(@RequestParam(required = false) Long studentExitId,
			@RequestParam(required = false) String status) {
		List<TransferConnectorJob> jobs = studentExitId != null
				? jobRepository.findByStudentExitIdOrderByIdDesc(studentExitId)
				: status != null ? jobRepository.findByStatusOrderByIdDesc(TransferConnectorJobStatus.valueOf(status))
						: jobRepository.findAllByOrderByIdDesc();
		return jobs.stream().map(TransferConnectorJobResponse::from).toList();
	}

	@PostMapping
	public TransferConnectorJobResponse prepare(@RequestBody PrepareTransferConnectorJobRequest request) {
		StudentExit studentExit = studentExitRepository.findById(request.studentExitId())
				.orElseThrow(() -> new IllegalArgumentException("No student exit with id " + request.studentExitId()));
		RegulatoryExportSchema schema = schemaRepository.findById(request.schemaId())
				.orElseThrow(() -> new IllegalArgumentException("No regulatory export schema with id " + request.schemaId()));
		return TransferConnectorJobResponse.from(connectorService.prepare(studentExit, schema, request.connectorName()));
	}

	@PostMapping("/{id}/submit")
	public TransferConnectorJobResponse submit(@PathVariable Long id, @RequestBody SubmitTransferConnectorJobRequest request) {
		return TransferConnectorJobResponse.from(connectorService.markSubmitted(load(id), request.externalReference()));
	}

	@PostMapping("/{id}/fail")
	public TransferConnectorJobResponse fail(@PathVariable Long id, @RequestBody FailTransferConnectorJobRequest request) {
		return TransferConnectorJobResponse.from(connectorService.markFailed(load(id), request.reason()));
	}

	@PostMapping("/{id}/reprepare")
	public TransferConnectorJobResponse reprepare(@PathVariable Long id) {
		return TransferConnectorJobResponse.from(connectorService.reprepare(load(id)));
	}

	@PostMapping("/{id}/cancel")
	public TransferConnectorJobResponse cancel(@PathVariable Long id) {
		return TransferConnectorJobResponse.from(connectorService.cancel(load(id)));
	}

	private TransferConnectorJob load(Long id) {
		return jobRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No connector job with id " + id));
	}
}
