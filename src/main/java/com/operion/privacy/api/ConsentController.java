package com.operion.privacy.api;

import java.util.List;

import com.operion.authorization.RequirePermission;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.privacy.ConsentPurpose;
import com.operion.privacy.ConsentPurposeRepository;
import com.operion.privacy.ConsentPurposeStatus;
import com.operion.privacy.ConsentService;
import com.operion.privacy.ConsentSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consent purposes and per-person records under one controller - same "no dedicated
 * controller per tiny entity" grouping as CampusController. */
@RestController
@RequestMapping("/api/v1")
@RequirePermission("PRIVACY_MANAGE")
public class ConsentController {

	private final ConsentService consentService;
	private final ConsentPurposeRepository purposeRepository;
	private final PersonRepository personRepository;

	public ConsentController(ConsentService consentService, ConsentPurposeRepository purposeRepository,
			PersonRepository personRepository) {
		this.consentService = consentService;
		this.purposeRepository = purposeRepository;
		this.personRepository = personRepository;
	}

	@GetMapping("/consent-purposes")
	public List<ConsentPurposeResponse> listPurposes() {
		return purposeRepository.findAll().stream().map(ConsentPurposeResponse::from).toList();
	}

	@PostMapping("/consent-purposes")
	public ConsentPurposeResponse createPurpose(@RequestBody ConsentPurposeRequest request) {
		return ConsentPurposeResponse.from(consentService.createPurpose(request.code(), request.name(), request.description()));
	}

	@PatchMapping("/consent-purposes/{id}")
	public ConsentPurposeResponse updatePurpose(@PathVariable Long id, @RequestBody ConsentPurposeRequest request) {
		return ConsentPurposeResponse.from(consentService.updatePurpose(loadPurpose(id), request.name(), request.description()));
	}

	@PostMapping("/consent-purposes/{id}/status")
	public ConsentPurposeResponse changePurposeStatus(@PathVariable Long id, @RequestBody StatusRequest request) {
		return ConsentPurposeResponse.from(
				consentService.changePurposeStatus(loadPurpose(id), ConsentPurposeStatus.valueOf(request.status())));
	}

	@PostMapping("/consents")
	public ConsentRecordResponse record(@RequestBody RecordConsentRequest request) {
		Person person = personRepository.findById(request.personId())
				.orElseThrow(() -> new IllegalArgumentException("No person with id " + request.personId()));
		return ConsentRecordResponse.from(consentService.record(person, loadPurpose(request.purposeId()), request.granted(),
				ConsentSource.valueOf(request.source()), request.note()));
	}

	@GetMapping("/consents/current")
	public List<CurrentConsentResponse> current(@RequestParam Long personId) {
		return consentService.current(personId).stream().map(CurrentConsentResponse::from).toList();
	}

	@GetMapping("/consents/history")
	public List<ConsentRecordResponse> history(@RequestParam Long personId) {
		return consentService.history(personId).stream().map(ConsentRecordResponse::from).toList();
	}

	private ConsentPurpose loadPurpose(Long id) {
		return purposeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No consent purpose with id " + id));
	}
}
