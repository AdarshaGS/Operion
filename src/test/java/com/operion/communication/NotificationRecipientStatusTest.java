package com.operion.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.operion.identity.Person;
import org.junit.jupiter.api.Test;

/**
 * Proves the delivery-status split NotificationChannel's class doc describes: IN_APP
 * lands SENT at construction, EMAIL/SMS start PENDING until NotificationDispatchService
 * actually calls markSent/markFailed - and that those two can't fire twice or out of order.
 */
class NotificationRecipientStatusTest {

	private final Person person = new Person("Ira", "Shah");

	@Test
	void inAppLandsSentImmediately() {
		NotificationRecipient recipient = new NotificationRecipient(null, person, NotificationChannel.IN_APP, "Title", "Body");

		assertThat(recipient.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
		assertThat(recipient.getSentAt()).isNotNull();
	}

	@Test
	void emailStartsPendingUntilDispatched() {
		NotificationRecipient recipient = new NotificationRecipient(null, person, NotificationChannel.EMAIL, "Title", "Body");

		assertThat(recipient.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
		assertThat(recipient.getSentAt()).isNull();
	}

	@Test
	void smsStartsPendingUntilDispatched() {
		NotificationRecipient recipient = new NotificationRecipient(null, person, NotificationChannel.SMS, null, "Body");

		assertThat(recipient.getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
	}

	@Test
	void markSentMovesPendingToSentAndRecordsTheProvider() {
		NotificationRecipient recipient = new NotificationRecipient(null, person, NotificationChannel.EMAIL, "Title", "Body");

		recipient.markSent("resend", "abc-123");

		assertThat(recipient.getDeliveryStatus()).isEqualTo(DeliveryStatus.SENT);
		assertThat(recipient.getSentAt()).isNotNull();
		assertThat(recipient.getProvider()).isEqualTo("resend");
		assertThat(recipient.getProviderMessageId()).isEqualTo("abc-123");
	}

	@Test
	void markDeliveredMovesSentToDelivered() {
		NotificationRecipient recipient = new NotificationRecipient(null, person, NotificationChannel.EMAIL, "Title", "Body");
		recipient.markSent("resend", "abc-123");

		recipient.markDelivered();

		assertThat(recipient.getDeliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
		assertThat(recipient.getDeliveredAt()).isNotNull();
	}

	@Test
	void markDeliveredRejectsAPendingRow() {
		NotificationRecipient recipient = new NotificationRecipient(null, person, NotificationChannel.EMAIL, "Title", "Body");

		assertThatThrownBy(recipient::markDelivered).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void markFailedMovesPendingToFailedWithReason() {
		NotificationRecipient recipient = new NotificationRecipient(null, person, NotificationChannel.SMS, null, "Body");

		recipient.markFailed("No configured SMS provider");

		assertThat(recipient.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
		assertThat(recipient.getFailureReason()).isEqualTo("No configured SMS provider");
	}

	@Test
	void markSentRejectsAnAlreadySentInAppRow() {
		NotificationRecipient recipient = new NotificationRecipient(null, person, NotificationChannel.IN_APP, "Title", "Body");

		assertThatThrownBy(() -> recipient.markSent("resend", "abc-123")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void markFailedRejectsARowThatsAlreadySent() {
		NotificationRecipient recipient = new NotificationRecipient(null, person, NotificationChannel.EMAIL, "Title", "Body");
		recipient.markSent("resend", "abc-123");

		assertThatThrownBy(() -> recipient.markFailed("late failure")).isInstanceOf(IllegalStateException.class);
	}
}
