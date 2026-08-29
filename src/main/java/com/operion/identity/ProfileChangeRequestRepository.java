package com.operion.identity;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileChangeRequestRepository extends JpaRepository<ProfileChangeRequest, Long> {

	List<ProfileChangeRequest> findByPersonId(Long personId);

	List<ProfileChangeRequest> findByStatus(ProfileChangeRequestStatus status);
}
