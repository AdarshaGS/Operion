package com.operion.communication;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {

	List<NotificationRecipient> findByPersonIdOrderByCreatedAtDesc(Long personId);

	List<NotificationRecipient> findByAnnouncementId(Long announcementId);

	Optional<NotificationRecipient> findByIdAndPersonId(Long id, Long personId);

	/** Due-for-dispatch query for NotificationDispatchWorker - run once per tenant under
	 * that tenant's TenantContext, same convention as
	 * AnnouncementRepository.findByStatusAndScheduledAtLessThanEqual. */
	List<NotificationRecipient> findByDeliveryStatus(DeliveryStatus deliveryStatus);

	/** Native, scalar-only, deliberately bypassing Hibernate's @TenantId filter - a
	 * delivery webhook carries only the provider's own message id, with no organisation
	 * context at all (Brevo/Resend don't know about our tenancy), so there is no way to
	 * set TenantContext before finding out which org this row even belongs to. Same
	 * narrowest-possible-escape-hatch shape as
	 * PaymentGatewayOrderRepository.findOrganisationIdByGatewayOrderId - deliberately
	 * scalar (not the entity itself): a native query loading a full entity's associations
	 * (person) doesn't manage/merge them the same way a normal query does, so
	 * NotificationDeliveryWebhookService sets TenantContext from this id, then re-fetches
	 * the entity via the normal, tenant-filtered findByProviderAndProviderMessageId below. */
	@Query(value = "SELECT organisation_id FROM notification_recipients WHERE provider = :provider AND provider_message_id = :providerMessageId "
			+ "LIMIT 1", nativeQuery = true)
	Optional<Long> findOrganisationIdByProviderAndProviderMessageId(
			@Param("provider") String provider, @Param("providerMessageId") String providerMessageId);

	Optional<NotificationRecipient> findByProviderAndProviderMessageId(String provider, String providerMessageId);
}
