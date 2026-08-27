package com.operion.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.TenantContext;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the fallback chain GitHub #105 asked for: if one provider fails, the next one
 * configured is tried before giving up - hand-written stub EmailSenders, same
 * external-dependency-testing pattern as RazorpayGateway's tests, rather than hitting a
 * real Brevo/Resend API or mocking HTTP.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmailDeliveryServiceTest {

	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private EmailOutboxRepository emailOutboxRepository;

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private void newTenant(String slugPrefix) {
		Organisation organisation = organisationRepository.save(
				new Organisation("Test School", "Test School Trust", slugPrefix + "-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);
	}

	private static class RecordingSender implements EmailSender {
		private final String name;
		private final boolean succeeds;
		private final List<EmailMessage> received = new ArrayList<>();

		RecordingSender(String name, boolean succeeds) {
			this.name = name;
			this.succeeds = succeeds;
		}

		@Override
		public void send(EmailMessage message) {
			received.add(message);
			if (!succeeds) {
				throw new EmailSendException(name + " is down");
			}
		}

		@Override
		public String providerName() {
			return name;
		}
	}

	@Test
	void fallsBackToTheNextProviderWhenTheFirstFails() {
		newTenant("fallback");
		RecordingSender brevo = new RecordingSender("brevo", false);
		RecordingSender resend = new RecordingSender("resend", true);
		EmailDeliveryService service = new EmailDeliveryService(List.of(brevo, resend), emailOutboxRepository);

		boolean sent = service.sendBestEffort("new-hire@example.com", "Welcome", "<p>hi</p>");

		assertThat(sent).isTrue();
		assertThat(brevo.received).hasSize(1);
		assertThat(resend.received).hasSize(1);
		EmailOutbox outbox = emailOutboxRepository.findAll().get(0);
		assertThat(outbox.getStatus()).isEqualTo(EmailDeliveryStatus.SENT);
		assertThat(outbox.getProvider()).isEqualTo("resend");
	}

	@Test
	void neverCallsTheSecondProviderWhenTheFirstSucceeds() {
		newTenant("first-succeeds");
		RecordingSender brevo = new RecordingSender("brevo", true);
		RecordingSender resend = new RecordingSender("resend", true);
		EmailDeliveryService service = new EmailDeliveryService(List.of(brevo, resend), emailOutboxRepository);

		service.sendBestEffort("new-hire@example.com", "Welcome", "<p>hi</p>");

		assertThat(brevo.received).hasSize(1);
		assertThat(resend.received).isEmpty();
	}

	@Test
	void recordsFailedWhenEveryProviderFails() {
		newTenant("all-fail");
		EmailDeliveryService service = new EmailDeliveryService(
				List.of(new RecordingSender("brevo", false), new RecordingSender("resend", false)), emailOutboxRepository);

		boolean sent = service.sendBestEffort("new-hire@example.com", "Welcome", "<p>hi</p>");

		assertThat(sent).isFalse();
		EmailOutbox outbox = emailOutboxRepository.findAll().get(0);
		assertThat(outbox.getStatus()).isEqualTo(EmailDeliveryStatus.FAILED);
		assertThat(outbox.getFailureReason()).isNotBlank();
	}

	@Test
	void neverThrowsAndSkipsTheOutboxWhenThereIsNoRecipient() {
		newTenant("no-recipient");
		EmailDeliveryService service = new EmailDeliveryService(List.of(new RecordingSender("brevo", true)), emailOutboxRepository);

		boolean sent = service.sendBestEffort(null, "Welcome", "<p>hi</p>");

		assertThat(sent).isFalse();
		assertThat(emailOutboxRepository.findAll()).isEmpty();
	}
}
