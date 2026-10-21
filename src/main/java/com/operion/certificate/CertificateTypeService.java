package com.operion.certificate;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.operion.common.InvalidRequestException;

/**
 * A school's certificate types. New types start from a preset (copied, then independent) or from a short generic
 * wording; the School setup template creates them through {@link #createFromTemplate}.
 */
@Service
public class CertificateTypeService {

	private static final Pattern CODE = Pattern.compile("[A-Z0-9_]{1,30}");
	private static final Set<String> PAGE_SIZES = Set.of("A4", "A5", "Letter");
	private static final String GENERIC_BODY = "This is to certify that {{student.name}} (admission number {{student.admissionNumber}})"
			+ " is a student of {{school.name}}, studying in {{class.current}} during the academic year {{academicYear}}.";

	public record TypeDetails(String name, String title, String bodyTemplate, String particularsTemplate, String manualFields,
			String numberFormat, String pageSize, boolean oncePerStudent, boolean requiresExit, boolean requiresClearance,
			boolean approvalRequired, boolean guardianRequestable, boolean active) {
	}

	private final CertificateTypeRepository typeRepository;
	private final CertificateTypePresetRepository presetRepository;

	public CertificateTypeService(CertificateTypeRepository typeRepository, CertificateTypePresetRepository presetRepository) {
		this.typeRepository = typeRepository;
		this.presetRepository = presetRepository;
	}

	public List<CertificateType> list() {
		return typeRepository.findAllByOrderByNameAsc();
	}

	public List<CertificateTypePreset> presets() {
		return presetRepository.findAll();
	}

	public CertificateType get(Long id) {
		return typeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No certificate type with id " + id));
	}

	/** A preset's wording and rules when its code matches one, else the generic wording with every rule off. */
	@Transactional
	public CertificateType create(String code, String name) {
		String normalizedCode = normalizeCode(code);
		if (typeRepository.findByCode(normalizedCode).isPresent()) {
			throw new IllegalStateException("A certificate type with code " + normalizedCode + " already exists");
		}
		if (name == null || name.isBlank()) {
			throw new InvalidRequestException("Name is required");
		}
		Optional<CertificateTypePreset> preset = presetRepository.findByCode(normalizedCode);
		CertificateType type = preset.map(p -> CertificateType.fromPreset(p, name.trim())).orElseGet(() -> {
			CertificateType generic = new CertificateType(normalizedCode, name.trim());
			generic.update(name.trim(), name.trim().toUpperCase(Locale.ROOT), GENERIC_BODY, null, null, normalizedCode + "-{YYYY}-{SEQ:4}", "A4",
					false, false, false, false, false, true);
			return generic;
		});
		return typeRepository.save(type);
	}

	/** Skip-if-exists create for the School setup template: false when a type with this code or name is already there. */
	@Transactional
	public boolean createFromTemplate(String code, String name) {
		String normalizedCode = normalizeCode(code);
		boolean exists = typeRepository.findAll().stream()
				.anyMatch(type -> type.getCode().equals(normalizedCode) || type.getName().equalsIgnoreCase(name.trim()));
		if (exists) {
			return false;
		}
		create(normalizedCode, name);
		return true;
	}

	@Transactional
	public CertificateType update(Long id, TypeDetails details) {
		CertificateType type = get(id);
		validate(details);
		type.update(details.name().trim(), details.title().trim(), details.bodyTemplate(), details.particularsTemplate(), details.manualFields(),
				details.numberFormat().trim(), details.pageSize(), details.oncePerStudent(), details.requiresExit(), details.requiresClearance(),
				details.approvalRequired(), details.guardianRequestable(), details.active());
		return type;
	}

	private static void validate(TypeDetails details) {
		if (details.name() == null || details.name().isBlank() || details.title() == null || details.title().isBlank()) {
			throw new InvalidRequestException("Name and title are required");
		}
		if (details.bodyTemplate() == null || details.bodyTemplate().isBlank()) {
			throw new InvalidRequestException("The certificate wording cannot be empty");
		}
		if (details.numberFormat() == null || !details.numberFormat().contains("{SEQ")) {
			throw new InvalidRequestException("The number format must contain {SEQ} or {SEQ:n} so every certificate gets a unique number");
		}
		if (!PAGE_SIZES.contains(details.pageSize())) {
			throw new InvalidRequestException("Page size must be A4, A5 or Letter");
		}
	}

	private static String normalizeCode(String code) {
		String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
		if (!CODE.matcher(normalized).matches()) {
			throw new InvalidRequestException("Code must be 1-30 letters, digits or underscores");
		}
		return normalized;
	}
}
