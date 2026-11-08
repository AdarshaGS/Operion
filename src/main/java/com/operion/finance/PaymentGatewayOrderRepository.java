package com.operion.finance;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentGatewayOrderRepository extends JpaRepository<PaymentGatewayOrder, Long> {

	Optional<PaymentGatewayOrder> findByLinkToken(String linkToken);

	Optional<PaymentGatewayOrder> findByGatewayOrderId(String gatewayOrderId);

	/** Row-locks the order (SELECT ... FOR UPDATE) - the webhook's serialization point, so two concurrent
	 * redeliveries of the same payment.captured cannot both pass the "already PAID?" check. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from PaymentGatewayOrder o where o.id = :id")
	Optional<PaymentGatewayOrder> findByIdForUpdate(@Param("id") Long id);
}
