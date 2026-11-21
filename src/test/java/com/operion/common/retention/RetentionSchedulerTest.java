package com.operion.common.retention;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import com.operion.common.JpaConfig;
import com.operion.common.MultiTenancyConfig;
import com.operion.common.SchedulerHeartbeat;
import com.operion.common.TenantContext;
import com.operion.communication.NotificationChannel;
import com.operion.communication.NotificationRecipient;
import com.operion.communication.NotificationRecipientRepository;
import com.operion.email.EmailOutbox;
import com.operion.email.EmailOutboxRepository;
import com.operion.identity.Person;
import com.operion.identity.PersonRepository;
import com.operion.identity.auth.LoginAttempt;
import com.operion.identity.auth.LoginAttemptRepository;
import com.operion.organisation.Organisation;
import com.operion.organisation.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The retention purge removes only rows that are both old enough and finished with - an unexpired login
 * lock, a PENDING/SENT notification and a PENDING outbox row survive however old they are - and walks
 * several pages (batch size 2) per table. Rows are aged with plain SQL since created_at/updated_at are
 * managed by auditing. The scheduler is built by hand, like the other scheduler tests.
 */
@DataJpaTest
@Import({ MultiTenancyConfig.class, JpaConfig.class })
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RetentionSchedulerTest {

	private static final Instant NOW = Instant.parse("2026-11-11T03:30:00Z");

	@Autowired private OrganisationRepository organisationRepository;
	@Autowired private PersonRepository personRepository;
	@Autowired private LoginAttemptRepository loginAttemptRepository;
	@Autowired private EmailOutboxRepository emailOutboxRepository;
	@Autowired private NotificationRecipientRepository notificationRecipientRepository;
	@Autowired private JdbcTemplate jdbcTemplate;

	private RetentionScheduler scheduler;
	private Organisation organisation;
	private Person person;

	@BeforeEach
	void setUp() {
		RetentionProperties properties = new RetentionProperties();
		properties.setBatchSize(2);
		scheduler = new RetentionScheduler(organisationRepository, loginAttemptRepository, emailOutboxRepository,
				notificationRecipientRepository, properties, new SchedulerHeartbeat());
		organisation = organisationRepository.save(new Organisation("Test School", "Test School Trust", "ret-" + System.nanoTime()));
		TenantContext.set(organisation.getId(), null);
		person = personRepository.save(new Person("Meera", "Nair"));
	}

	@AfterEach
	void clearTenant() {
		TenantContext.clear();
	}

	private static Instant daysAgo(long days) {
		return NOW.minus(Duration.ofDays(days));
	}

	private void age(String table, String column, Object id, String idColumn, Instant at) {
		jdbcTemplate.update("UPDATE " + table + " SET " + column + " = ? WHERE " + idColumn + " = ?", Timestamp.from(at), id);
	}

	private LoginAttempt loginAttempt(String email, Instant updatedAt, Instant lockedUntil) {
		LoginAttempt attempt = new LoginAttempt(organisation.getId() + ":" + email, organisation.getId(), email);
		if (lockedUntil != null) {
			attempt.recordFailure(1, lockedUntil);
		}
		loginAttemptRepository.save(attempt);
		age("login_attempts", "updated_at", attempt.getKey(), "attempt_key", updatedAt);
		return attempt;
	}

	@Test
	void loginAttemptsPastTheCutoffAreDeletedUnlessStillLocked() {
		LoginAttempt old1 = loginAttempt("old1@example.com", daysAgo(40), null);
		LoginAttempt old2 = loginAttempt("old2@example.com", daysAgo(90), null);
		LoginAttempt old3 = loginAttempt("old3@example.com", daysAgo(31), null);
		LoginAttempt oldLockExpired = loginAttempt("expired@example.com", daysAgo(60), NOW.minus(Duration.ofDays(5)));
		LoginAttempt oldButStillLocked = loginAttempt("locked@example.com", daysAgo(60), NOW.plus(Duration.ofHours(1)));
		LoginAttempt recent = loginAttempt("recent@example.com", daysAgo(2), null);

		scheduler.purgeExpired(NOW);

		assertThat(loginAttemptRepository.findAll()).extracting(LoginAttempt::getKey)
				.containsExactlyInAnyOrder(oldButStillLocked.getKey(), recent.getKey())
				.doesNotContain(old1.getKey(), old2.getKey(), old3.getKey(), oldLockExpired.getKey());
	}

	@Test
	void onlySentOrFailedEmailOutboxRowsPastTheCutoffAreDeleted() {
		EmailOutbox oldSent1 = emailOutbox(EmailKind.SENT, 200);
		EmailOutbox oldSent2 = emailOutbox(EmailKind.SENT, 400);
		EmailOutbox oldFailed = emailOutbox(EmailKind.FAILED, 181);
		EmailOutbox oldPending = emailOutbox(EmailKind.PENDING, 400);
		EmailOutbox recentSent = emailOutbox(EmailKind.SENT, 10);
		EmailOutbox recentFailed = emailOutbox(EmailKind.FAILED, 179);

		scheduler.purgeExpired(NOW);
		TenantContext.set(organisation.getId(), null); // the purge clears the tenant it sets per organisation

		assertThat(emailOutboxRepository.findAll()).extracting(EmailOutbox::getId)
				.containsExactlyInAnyOrder(oldPending.getId(), recentSent.getId(), recentFailed.getId())
				.doesNotContain(oldSent1.getId(), oldSent2.getId(), oldFailed.getId());
	}

	@Test
	void onlyTerminalNotificationRecipientsPastTheCutoffAreDeleted() {
		NotificationRecipient oldDelivered = notification(NotifState.DELIVERED, 400);
		NotificationRecipient oldRead = notification(NotifState.READ, 366);
		NotificationRecipient oldFailed = notification(NotifState.FAILED, 500);
		NotificationRecipient oldPending = notification(NotifState.PENDING, 500);
		NotificationRecipient oldSent = notification(NotifState.SENT, 500);
		NotificationRecipient recentRead = notification(NotifState.READ, 30);
		NotificationRecipient recentFailed = notification(NotifState.FAILED, 364);

		scheduler.purgeExpired(NOW);
		TenantContext.set(organisation.getId(), null); // the purge clears the tenant it sets per organisation

		assertThat(notificationRecipientRepository.findAll()).extracting(NotificationRecipient::getId)
				.containsExactlyInAnyOrder(oldPending.getId(), oldSent.getId(), recentRead.getId(), recentFailed.getId())
				.doesNotContain(oldDelivered.getId(), oldRead.getId(), oldFailed.getId());
	}

	@Test
	void everyOrganisationIsPurgedUnderItsOwnTenantAndNothingNewerIsTouched() {
		EmailOutbox oldHere = emailOutbox(EmailKind.SENT, 300);
		Organisation other = organisationRepository.save(new Organisation("Other School", "Other Trust", "ret-other-" + System.nanoTime()));
		TenantContext.set(other.getId(), null);
		EmailOutbox oldThere = emailOutbox(EmailKind.SENT, 300);
		EmailOutbox recentThere = emailOutbox(EmailKind.SENT, 5);
		TenantContext.set(organisation.getId(), null);

		scheduler.purgeExpired(NOW);

		TenantContext.set(other.getId(), null);
		assertThat(emailOutboxRepository.findAll()).extracting(EmailOutbox::getId).containsExactly(recentThere.getId());
		TenantContext.set(organisation.getId(), null);
		assertThat(emailOutboxRepository.findAll()).extracting(EmailOutbox::getId).doesNotContain(oldHere.getId());
		assertThat(oldThere.getId()).isNotNull();
	}

	@Test
	void aDisabledScheduleDoesNothingWhenTriggeredByCron() {
		RetentionProperties disabled = new RetentionProperties();
		disabled.setEnabled(false);
		RetentionScheduler off = new RetentionScheduler(organisationRepository, loginAttemptRepository, emailOutboxRepository,
				notificationRecipientRepository, disabled, new SchedulerHeartbeat());
		EmailOutbox old = emailOutbox(EmailKind.SENT, 3000);

		off.scheduledPurge();
		TenantContext.set(organisation.getId(), null);

		assertThat(emailOutboxRepository.findAll()).extracting(EmailOutbox::getId).contains(old.getId());
	}

	private enum EmailKind { PENDING, SENT, FAILED }

	private EmailOutbox emailOutbox(EmailKind kind, long ageDays) {
		EmailOutbox outbox = new EmailOutbox("someone@example.com", "Hello");
		if (kind == EmailKind.SENT) {
			outbox.markSent("brevo");
		} else if (kind == EmailKind.FAILED) {
			outbox.markFailed("rejected");
		}
		outbox = emailOutboxRepository.save(outbox);
		age("email_outbox", "created_at", outbox.getId(), "id", daysAgo(ageDays));
		return outbox;
	}

	private enum NotifState { PENDING, SENT, DELIVERED, READ, FAILED }

	private NotificationRecipient notification(NotifState state, long ageDays) {
		NotificationRecipient recipient = new NotificationRecipient(null, person, NotificationChannel.EMAIL, "Subject", "Body text");
		switch (state) {
			case SENT -> recipient.markSent("brevo", "m-" + System.nanoTime());
			case DELIVERED -> {
				recipient.markSent("brevo", "m-" + System.nanoTime());
				recipient.markDelivered();
			}
			case READ -> {
				recipient.markSent("brevo", "m-" + System.nanoTime());
				recipient.markRead();
			}
			case FAILED -> recipient.markFailed("bounced");
			case PENDING -> {
			}
		}
		recipient = notificationRecipientRepository.save(recipient);
		age("notification_recipients", "created_at", recipient.getId(), "id", daysAgo(ageDays));
		return recipient;
	}
}
