package com.operion.support;

import java.util.List;

import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupportTicketService {

	private final SupportTicketRepository supportTicketRepository;
	private final OrganisationRepository organisationRepository;

	public SupportTicketService(SupportTicketRepository supportTicketRepository, OrganisationRepository organisationRepository) {
		this.supportTicketRepository = supportTicketRepository;
		this.organisationRepository = organisationRepository;
	}

	@Transactional
	public SupportTicket create(Long organisationId, String subject, String description, SupportTicketPriority priority,
			String requesterEmail) {
		Organisation organisation = organisationRepository.findById(organisationId)
				.orElseThrow(() -> new IllegalArgumentException("No organisation with id " + organisationId));
		return supportTicketRepository.save(new SupportTicket(organisation, subject, description, priority, requesterEmail));
	}

	public List<SupportTicket> all() {
		return supportTicketRepository.findAllByOrderByCreatedAtDesc();
	}

	public SupportTicket get(Long id) {
		return supportTicketRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("No support ticket with id " + id));
	}

	@Transactional
	public SupportTicket changeStatus(Long id, SupportTicketStatus target) {
		SupportTicket ticket = get(id);
		ticket.changeStatus(target);
		return supportTicketRepository.save(ticket);
	}

	@Transactional
	public SupportTicket assign(Long id, String assigneeEmail) {
		SupportTicket ticket = get(id);
		ticket.assign(assigneeEmail);
		return supportTicketRepository.save(ticket);
	}
}
