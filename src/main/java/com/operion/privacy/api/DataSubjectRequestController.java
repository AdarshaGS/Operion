package com.operion.privacy.api;

import java.util.List;
import java.util.Map;

import com.operion.authorization.RequirePermission;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.privacy.DataSubjectRequest;
import com.operion.privacy.DataSubjectRequestRepository;
import com.operion.privacy.DataSubjectRequestService;
import com.operion.privacy.DataSubjectRequestStatus;
import com.operion.privacy.DataSubjectRequestType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data-subject-requests")
@RequirePermission("PRIVACY_MANAGE")
public class DataSubjectRequestController {

	private final DataSubjectRequestService requestService;
	private final DataSubjectRequestRepository requestRepository;
	private final PersonRepository personRepository;

	public DataSubjectRequestController(DataSubjectRequestService requestService, DataSubjectRequestRepository requestRepository,
			PersonRepository personRepository) {
		this.requestService = requestService;
		this.requestRepository = requestRepository;
		this.personRepository = personRepository;
	}

	@GetMapping
	public List<DataSubjectRequestResponse> list(@RequestParam(required = false) String status,
			@RequestParam(required = false) Long personId) {
		List<DataSubjectRequest> requests = personId != null
				? requestRepository.findByPersonIdOrderByIdDesc(personId)
				: status != null ? requestRepository.findByStatusOrderByDueDate(DataSubjectRequestStatus.valueOf(status))
						: requestRepository.findAllByOrderByIdDesc();
		return requests.stream().map(DataSubjectRequestResponse::from).toList();
	}

	@PostMapping
	public DataSubjectRequestResponse open(@RequestBody OpenDataSubjectRequestRequest request) {
		Person person = personRepository.findById(request.personId())
				.orElseThrow(() -> new IllegalArgumentException("No person with id " + request.personId()));
		return DataSubjectRequestResponse.from(requestService.open(person, DataSubjectRequestType.valueOf(request.requestType()),
				request.description(), request.receivedDate(), request.dueDate()));
	}

	@PostMapping("/{id}/verify-identity")
	public DataSubjectRequestResponse verifyIdentity(@PathVariable Long id, @RequestBody VerifyIdentityRequest request) {
		return DataSubjectRequestResponse.from(requestService.verifyIdentity(load(id), request.method()));
	}

	@PostMapping("/{id}/complete")
	public DataSubjectRequestResponse complete(@PathVariable Long id, @RequestBody ResolveDataSubjectRequestRequest request) {
		return DataSubjectRequestResponse.from(requestService.complete(load(id), request.resolutionNote()));
	}

	@PostMapping("/{id}/reject")
	public DataSubjectRequestResponse reject(@PathVariable Long id, @RequestBody ResolveDataSubjectRequestRequest request) {
		return DataSubjectRequestResponse.from(requestService.reject(load(id), request.resolutionNote()));
	}

	@GetMapping("/{id}/data-export")
	public Map<String, Object> dataExport(@PathVariable Long id) {
		return requestService.exportSubjectData(load(id));
	}

	private DataSubjectRequest load(Long id) {
		return requestRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No data subject request with id " + id));
	}
}
