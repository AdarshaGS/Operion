-- Backs DeliveryStatus.DELIVERED: provider/provider_message_id let
-- NotificationDeliveryWebhookService correlate a provider's delivery-confirmation
-- callback back to the exact row NotificationDispatchService sent.

ALTER TABLE notification_recipients ADD COLUMN provider VARCHAR(20);
ALTER TABLE notification_recipients ADD COLUMN provider_message_id VARCHAR(255);
ALTER TABLE notification_recipients ADD COLUMN delivered_at DATETIME(6);

-- NotificationRecipientRepository.findOrganisationIdByProviderAndProviderMessageId is a
-- native query (deliberately bypassing @TenantId, same shape as
-- PaymentGatewayOrderRepository.findOrganisationIdByGatewayOrderId) - a webhook carries
-- only the provider's own message id, no organisation context.
CREATE INDEX idx_notification_recipients_provider_message ON notification_recipients (provider, provider_message_id);
