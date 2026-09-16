-- WHATSAPP notification channel (#144): a WhatsApp Business API (Meta Cloud API) entry
-- in the existing generic external-service/BYOK mechanism (see com.operion.integration),
-- same shape as V72's 'brevo' row - no per-org rows needed here, those are created on
-- first save (see OrganisationExternalServicePropertyService).
INSERT INTO external_services (service_key, display_name) VALUES ('whatsapp', 'WhatsApp Business API');
