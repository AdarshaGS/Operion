package com.operion.certificate.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.operion.authorization.RequirePermission;
import com.operion.certificate.CertificateMergeFields;
import com.operion.certificate.CertificateTypeService;
import com.operion.certificate.CertificateTypeService.TypeDetails;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/certificate-types")
public class CertificateTypeController {

	public record CreateCertificateTypeRequest(@NotBlank @Size(max = 30) String code, @NotBlank @Size(max = 100) String name) {
	}

	public record UpdateCertificateTypeRequest(@NotBlank @Size(max = 100) String name, @NotBlank @Size(max = 150) String title,
			@NotBlank @Size(max = 20000) String bodyTemplate, @Size(max = 20000) String particularsTemplate, @Size(max = 5000) String manualFields,
			@NotBlank @Size(max = 60) String numberFormat, @NotBlank String pageSize, @NotNull Boolean oncePerStudent,
			@NotNull Boolean requiresExit, @NotNull Boolean requiresClearance, @NotNull Boolean approvalRequired,
			@NotNull Boolean guardianRequestable, @NotNull Boolean active) {
	}

	public record PresetResponse(String code, String name) {
	}

	private final CertificateTypeService typeService;

	public CertificateTypeController(CertificateTypeService typeService) {
		this.typeService = typeService;
	}

	@GetMapping
	@RequirePermission(anyOf = { "CERTIFICATE_VIEW", "CERTIFICATE_REQUEST", "CERTIFICATE_TYPE_MANAGE" })
	public List<CertificateTypeResponse> list() {
		return typeService.list().stream().map(CertificateTypeResponse::from).toList();
	}

	@GetMapping("/presets")
	@RequirePermission("CERTIFICATE_TYPE_MANAGE")
	public List<PresetResponse> presets() {
		return typeService.presets().stream().map(preset -> new PresetResponse(preset.getCode(), preset.getName())).toList();
	}

	@GetMapping("/merge-fields")
	@RequirePermission("CERTIFICATE_TYPE_MANAGE")
	public Map<String, String> mergeFields() {
		return CertificateMergeFields.CATALOG;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@RequirePermission("CERTIFICATE_TYPE_MANAGE")
	public CertificateTypeResponse create(@Valid @RequestBody CreateCertificateTypeRequest request) {
		return CertificateTypeResponse.from(typeService.create(request.code(), request.name()));
	}

	@PutMapping("/{id}")
	@RequirePermission("CERTIFICATE_TYPE_MANAGE")
	public CertificateTypeResponse update(@PathVariable Long id, @Valid @RequestBody UpdateCertificateTypeRequest request) {
		return CertificateTypeResponse.from(typeService.update(id, new TypeDetails(request.name(), request.title(), request.bodyTemplate(),
				request.particularsTemplate(), request.manualFields(), request.numberFormat(), request.pageSize(), request.oncePerStudent(),
				request.requiresExit(), request.requiresClearance(), request.approvalRequired(), request.guardianRequestable(), request.active())));
	}
}
