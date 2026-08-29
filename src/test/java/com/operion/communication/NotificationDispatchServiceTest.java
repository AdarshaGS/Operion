package com.operion.communication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.email.EmailDeliveryService;
import com.operion.email.EmailMessage;
import com.operion.email.EmailOutboxRepository;
import com.operion.email.EmailSendException;
import com.operion.email.EmailSender;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.sms.SmsDeliveryService;
import com.operion.sms.SmsMessage;
import com.operion.sms.SmsSendException;
import com.operion.sms.SmsSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves NotificationDispatchService actually calls a real EMAIL/SMS sender for a PENDING
 * row and records the true outcome, rather than the pre-#161 behaviour of every row
 * landing SENT regardless of whether anything was ever sent.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationDispatchServiceTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private NotificationRecipientRepository notificationRecipientRepository;

	@Autowired
	private EmailOutboxRepository emailOutboxRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private Person newPersonWithContact(String slugPrefix) {
		Organisation organisation = organisationRepository.save(
				new Organisation("Test School", "Test School Trust", slugPrefix + "-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);
		Person person = personRepository.save(new Person("Vikram", "Shah"));
		person.setEmail("vikram@example.com");
		person.setPhone("+15551234567");
		return personRepository.save(person);
	}

	private static class StubEmailSender implements EmailSender {
		private final boolean succeeds;

		StubEmailSender(boolean succeeds) {
			this.succeeds = succeeds;
		}

		@Override
		public String send(EmailMessage message) {
			if (!succeeds) {
				throw new EmailSendException("provider down");
			}
			return "email-msg-id";
		}

		@Override
		public String providerName() {
			return "stub-email";
		}
	}

	private static class StubSmsSender implements SmsSender {
		private final boolean succeeds;

		StubSmsSender(boolean succeeds) {
			this.succeeds = succeeds;
		}

		@Override
		public String send(SmsMessage message) {
			if (!succeeds) {
				throw new SmsSendException("provider down");
			}
			return "sms-msg-id";
		}

		@Override
		public String providerName() {
			return "stub-sms";
		}
	}

	@Test
	void marksAnEmailRowSentWhenTheProviderAcceptsIt() {
		Person person = newPersonWithContact("dispatch-email-sent");
		NotificationRecipient recipient = notificationRecipientRepository.save(
				new NotificationRecipient(null, person, NotificationChannel.EMAIL, "Subject", "Body"));
		NotificationDispatchService dispatchService = new NotificationDispatchService(notificationRecipientRepository,
				new EmailDeliveryService(List.of(new StubEmailSender(true)), emailOutboxRepository), new SmsDeliveryService(List.of()));

		dispatchService.dispatch(recipient);

		NotificationRecipient reloaded = notificationRecipientRepository.findById(recipient.getId()).orElseThrow();
		assertThat(reloaded.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
		assertThat(reloaded.getSentAt()).isNotNull();
	}

	@Test
	void marksAnEmailRowFailedWhenNoProviderIsConfigured() {
		Person person = newPersonWithContact("dispatch-email-failed");
		NotificationRecipient recipient = notificationRecipientRepository.save(
				new NotificationRecipient(null, person, NotificationChannel.EMAIL, "Subject", "Body"));
		NotificationDispatchService dispatchService = new NotificationDispatchService(notificationRecipientRepository,
				new EmailDeliveryService(List.of(new StubEmailSender(false)), emailOutboxRepository), new SmsDeliveryService(List.of()));

		dispatchService.dispatch(recipient);

		NotificationRecipient reloaded = notificationRecipientRepository.findById(recipient.getId()).orElseThrow();
		assertThat(reloaded.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
		assertThat(reloaded.getFailureReason()).isNotBlank();
	}

	@Test
	void marksAnSmsRowSentWhenTheProviderAcceptsIt() {
		Person person = newPersonWithContact("dispatch-sms-sent");
		NotificationRecipient recipient = notificationRecipientRepository.save(
				new NotificationRecipient(null, person, NotificationChannel.SMS, null, "Body"));
		NotificationDispatchService dispatchService = new NotificationDispatchService(notificationRecipientRepository,
				new EmailDeliveryService(List.of(), emailOutboxRepository), new SmsDeliveryService(List.of(new StubSmsSender(true))));

		dispatchService.dispatch(recipient);

		NotificationRecipient reloaded = notificationRecipientRepository.findById(recipient.getId()).orElseThrow();
		assertThat(reloaded.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
	}
}
