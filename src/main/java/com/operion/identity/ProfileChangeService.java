package com.operion.identity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileChangeService {

	private final ProfileChangeRequestRepository profileChangeRequestRepository;
	private final PersonRepository personRepository;

	public ProfileChangeService(ProfileChangeRequestRepository profileChangeRequestRepository, PersonRepository personRepository) {
		this.profileChangeRequestRepository = profileChangeRequestRepository;
		this.personRepository = personRepository;
	}

	public ProfileChangeRequest submit(Person person, String phone, String email, String photoUrl, Long requestedBy) {
		return profileChangeRequestRepository.save(new ProfileChangeRequest(person, phone, email, photoUrl, requestedBy));
	}

	/** Applies the requested fields onto Person and saves both rows in one transaction. */
	@Transactional
	public ProfileChangeRequest approve(ProfileChangeRequest request, Long reviewedBy) {
		request.approve(reviewedBy);
		personRepository.save(request.getPerson());
		return profileChangeRequestRepository.save(request);
	}

	public ProfileChangeRequest reject(ProfileChangeRequest request, Long reviewedBy) {
		request.reject(reviewedBy);
		return profileChangeRequestRepository.save(request);
	}
}
