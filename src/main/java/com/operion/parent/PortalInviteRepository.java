package com.operion.parent;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PortalInviteRepository extends JpaRepository<PortalInvite, Long> {

	List<PortalInvite> findByStatus(PortalInviteStatus status);
}
