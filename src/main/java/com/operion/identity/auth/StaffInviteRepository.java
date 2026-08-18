package com.operion.identity.auth;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffInviteRepository extends JpaRepository<StaffInvite, Long> {

	List<StaffInvite> findByStatus(StaffInviteStatus status);
}
