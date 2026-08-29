package com.operion.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.common.WebhookVerificationException;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Proves the webhook piece of #166: a verified provider callback moves a SENT row to
 * DELIVERED, an unverified one is rejected outright, and an unrecognized/duplicate
 * message id is a silent no-op rather than an error - see the class's own doc for why
 * both of those are expected in practice, not edge cases. */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationDeliveryWebhookServiceTest {

	private static final String RESEND_SECRET = "whsec_MfKQ9r8GKYqrTwjUPD8ILPZIo2LaLaSw";
	private static final String BREVO_SECRET = "brevo-shared-secret";

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private NotificationRecipientRepository notificationRecipientRepository;

	private NotificationDeliveryWebhookService serviceUnderTest() {
		return new NotificationDeliveryWebhookService(notificationRecipientRepository, new ResendWebhookVerifier(), RESEND_SECRET,
				BREVO_SECRET, BREVO_SECRET);
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private NotificationRecipient sentEmailRecipient(String slugPrefix, String provider, String providerMessageId) {
		Organisation organisation = organisationRepository.save(
				new Organisation("Test School", "Test School Trust", slugPrefix + "-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);
		Person person = personRepository.save(new Person("Vikram", "Shah"));
		NotificationRecipient recipient =
				notificationRecipientRepository.save(new NotificationRecipient(null, person, NotificationChannel.EMAIL, "Subject", "Body"));
		recipient.markSent(provider, providerMessageId);
		notificationRecipientRepository.save(recipient);
		TenantContext.clear();
		return recipient;
	}

	private String signResend(String id, String timestamp, String body) throws Exception {
		byte[] keyBytes = Base64.getDecoder().decode(RESEND_SECRET.substring("whsec_".length()));
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
		byte[] computed = mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
		return "v1," + Base64.getEncoder().encodeToString(computed);
	}

	@Test
	void aVerifiedResendDeliveredEventMarksTheRowDelivered() throws Exception {
		NotificationRecipient recipient = sentEmailRecipient("webhook-resend-delivered", "resend", "resend-msg-1");
		String id = "svix-id-1";
		String timestamp = String.valueOf(Instant.now().getEpochSecond());
		String body = "{}";
		String signature = signResend(id, timestamp, body);

		serviceUnderTest().handleResendEvent(body, id, timestamp, signature, "email.delivered", "resend-msg-1");

		TenantContext.set(recipient.getOrganisationId(), null);
		assertThat(notificationRecipientRepository.findById(recipient.getId()).orElseThrow().getDeliveryStatus())
				.isEqualTo(DeliveryStatus.DELIVERED);
	}

	@Test
	void anUnverifiedResendEventIsRejectedAndNeverAppliesTheStatusChange() throws Exception {
		NotificationRecipient recipient = sentEmailRecipient("webhook-resend-bad-sig", "resend", "resend-msg-2");

		assertThatThrownBy(() -> serviceUnderTest().handleResendEvent("{}", "id", "123", "v1,not-a-real-signature", "email.delivered",
				"resend-msg-2")).isInstanceOf(WebhookVerificationException.class);

		TenantContext.set(recipient.getOrganisationId(), null);
		assertThat(notificationRecipientRepository.findById(recipient.getId()).orElseThrow().getDeliveryStatus())
				.isEqualTo(DeliveryStatus.SENT);
	}

	@Test
	void aVerifiedBrevoEmailDeliveredEventMarksTheRowDelivered() {
		NotificationRecipient recipient = sentEmailRecipient("webhook-brevo-delivered", "brevo", "brevo-msg-1");

		serviceUnderTest().handleBrevoEmailEvent(BREVO_SECRET, "delivered", "brevo-msg-1");

		TenantContext.set(recipient.getOrganisationId(), null);
		assertThat(notificationRecipientRepository.findById(recipient.getId()).orElseThrow().getDeliveryStatus())
				.isEqualTo(DeliveryStatus.DELIVERED);
	}

	@Test
	void aWrongBrevoSecretIsRejected() {
		NotificationDeliveryWebhookService service = serviceUnderTest();

		assertThatThrownBy(() -> service.handleBrevoEmailEvent("wrong-secret", "delivered", "brevo-msg-2"))
				.isInstanceOf(WebhookVerificationException.class);
	}

	@Test
	void anUnrecognizedMessageIdIsASilentNoOp() {
		serviceUnderTest().handleBrevoEmailEvent(BREVO_SECRET, "delivered", "no-such-message-id");
		// No exception, nothing to assert on - this is Brevo's account-wide webhook firing
		// for an email that was never a NotificationRecipient (e.g. a staff invite).
	}

	@Test
	void aDuplicateDeliveredEventIsIdempotent() {
		NotificationRecipient recipient = sentEmailRecipient("webhook-brevo-duplicate", "brevo", "brevo-msg-3");
		NotificationDeliveryWebhookService service = serviceUnderTest();
		service.handleBrevoEmailEvent(BREVO_SECRET, "delivered", "brevo-msg-3");

		service.handleBrevoEmailEvent(BREVO_SECRET, "delivered", "brevo-msg-3");

		TenantContext.set(recipient.getOrganisationId(), null);
		assertThat(notificationRecipientRepository.findById(recipient.getId()).orElseThrow().getDeliveryStatus())
				.isEqualTo(DeliveryStatus.DELIVERED);
	}
}
