package com.operion.finance.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.operion.authorization.OrganisationMembershipRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lists who is actually eligible to approve a fee waiver/refund - active members holding
 * FEE_STRUCTURE_MANAGE (or the org Owner). Ungated, same "no RequirePermission" default as
 * other picker endpoints (see that annotation's javadoc) - it's just names, not sensitive.
 * Backs the approver picker in StudentFeesPanel (#280), replacing a raw numeric "user id"
 * text field. Deduplicates by user id since one user can hold more than one qualifying
 * membership (e.g. Owner plus an explicit role).
 */
@RestController
@RequestMapping("/api/v1/fees/approvers")
public class FeeApproverController {

	private final OrganisationMembershipRepository membershipRepository;

	public FeeApproverController(OrganisationMembershipRepository membershipRepository) {
		this.membershipRepository = membershipRepository;
	}

	@GetMapping
	public List<FeeApproverResponse> list() {
		Map<Long, FeeApproverResponse> byUserId = new LinkedHashMap<>();
		membershipRepository.findActiveMembersWithPermission("FEE_STRUCTURE_MANAGE").stream()
				.map(FeeApproverResponse::from)
				.forEach(approver -> byUserId.putIfAbsent(approver.userId(), approver));
		return List.copyOf(byUserId.values());
	}
}
