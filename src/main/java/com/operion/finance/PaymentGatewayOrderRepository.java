package com.operion.finance;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentGatewayOrderRepository extends JpaRepository<PaymentGatewayOrder, Long> {

	Optional<PaymentGatewayOrder> findByLinkToken(String linkToken);

	Optional<PaymentGatewayOrder> findByGatewayOrderId(String gatewayOrderId);

	/**
	 * Native, deliberately bypassing Hibernate's @TenantId filter - a gateway webhook
	 * carries only the gateway's own order id, with no organisation context at all
	 * (Razorpay doesn't know about our tenancy), so there is no way to set TenantContext
	 * before finding out which org this order even belongs to. This is the one
	 * unavoidable "which tenant is this" lookup, same shape of problem
	 * AuthenticationService.login() solves via an org slug and PortalInviteService.claim()
	 * solves the same way - here there's no slug to resolve from at all, so a native query
	 * is the narrowest possible escape hatch: it returns only the organisation_id, never
	 * entity data, and every other access to this repository stays normally tenant-filtered.
	 */
	@Query(value = "SELECT organisation_id FROM payment_gateway_orders WHERE gateway_order_id = :gatewayOrderId", nativeQuery = true)
	Optional<Long> findOrganisationIdByGatewayOrderId(@Param("gatewayOrderId") String gatewayOrderId);
}
