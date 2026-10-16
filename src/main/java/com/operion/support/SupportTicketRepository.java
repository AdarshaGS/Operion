package com.operion.support;

import com.operion.common.persistence.PlatformPlane;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

@PlatformPlane
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

	List<SupportTicket> findAllByOrderByCreatedAtDesc();
}
