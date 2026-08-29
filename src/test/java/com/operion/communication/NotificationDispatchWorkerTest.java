package com.operion.communication;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.email.EmailDeliveryService;
import com.operion.email.EmailMessage;
import com.operion.email.EmailOutboxRepository;
import com.operion.email.EmailSender;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import com.operion.sms.SmsDeliveryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the poller reaches PENDING rows in every organisation, not just the first - same
 * cross-tenant-looping concern as ScheduledAnnouncementPublisherTest.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class NotificationDispatchWorkerTest {

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

	private static class StubEmailSender implements EmailSender {
		@Override
		public String send(EmailMessage message) {
			return "worker-msg-id-" + message.to();
		}

		@Override
		public String providerName() {
			return "stub-email";
		}
	}

	@Test
	void dispatchesPendingRowsInEveryOrganisation() {
		Organisation orgA = organisationRepository.save(new Organisation("A School", "A School Trust", "dispatch-worker-a"));
		TenantContext.set(orgA.getId(), null);
		Person personA = personRepository.save(new Person("Asha", "Rao"));
		personA.setEmail("asha@example.com");
		personRepository.save(personA);
		NotificationRecipient pendingInOrgA = notificationRecipientRepository.save(
				new NotificationRecipient(null, personA, NotificationChannel.EMAIL, "Subject", "Body"));

		Organisation orgB = organisationRepository.save(new Organisation("B School", "B School Trust", "dispatch-worker-b"));
		TenantContext.set(orgB.getId(), null);
		Person personB = personRepository.save(new Person("Rohit", "Nair"));
		personB.setEmail("rohit@example.com");
		personRepository.save(personB);
		NotificationRecipient pendingInOrgB = notificationRecipientRepository.save(
				new NotificationRecipient(null, personB, NotificationChannel.EMAIL, "Subject", "Body"));

		TenantContext.clear();
		NotificationDispatchService dispatchService = new NotificationDispatchService(notificationRecipientRepository,
				new EmailDeliveryService(List.of(new StubEmailSender()), emailOutboxRepository), new SmsDeliveryService(List.of()));
		NotificationDispatchWorker worker =
				new NotificationDispatchWorker(organisationRepository, notificationRecipientRepository, dispatchService);

		worker.dispatchPendingNotifications();

		TenantContext.set(orgA.getId(), null);
		assertThat(notificationRecipientRepository.findById(pendingInOrgA.getId()).orElseThrow().getDeliveryStatus())
				.isEqualTo(DeliveryStatus.SENT);

		TenantContext.set(orgB.getId(), null);
		assertThat(notificationRecipientRepository.findById(pendingInOrgB.getId()).orElseThrow().getDeliveryStatus())
				.isEqualTo(DeliveryStatus.SENT);
	}
}
