package com.operion.communication;

import com.operion.email.EmailDeliveryService;
import com.operion.sms.SmsDeliveryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Actually dispatches one PENDING EMAIL/SMS NotificationRecipient row via a real
 * provider. REQUIRES_NEW (own transaction per row, same pattern as
 * StudentRowImportService) so a slow/unreachable provider on one row can't roll back or
 * block any other row in the same NotificationDispatchWorker poll tick.
 */
@Service
public class NotificationDispatchService {

	private final NotificationRecipientRepository notificationRecipientRepository;
	private final EmailDeliveryService emailDeliveryService;
	private final SmsDeliveryService smsDeliveryService;

	public NotificationDispatchService(NotificationRecipientRepository notificationRecipientRepository,
			EmailDeliveryService emailDeliveryService, SmsDeliveryService smsDeliveryService) {
		this.notificationRecipientRepository = notificationRecipientRepository;
		this.emailDeliveryService = emailDeliveryService;
		this.smsDeliveryService = smsDeliveryService;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void dispatch(NotificationRecipient recipient) {
		switch (recipient.getChannel()) {
			case EMAIL -> emailDeliveryService.trySend(recipient.getPerson().getEmail(), recipient.getSubject(), recipient.getBody())
					.ifPresentOrElse(result -> recipient.markSent(result.provider(), result.messageId()), () -> markFailed(recipient));
			case SMS -> smsDeliveryService.trySend(recipient.getPerson().getPhone(), recipient.getBody())
					.ifPresentOrElse(result -> recipient.markSent(result.provider(), result.messageId()), () -> markFailed(recipient));
			case IN_APP -> throw new IllegalStateException("IN_APP is never PENDING - see NotificationRecipient's constructor");
		}
		notificationRecipientRepository.save(recipient);
	}

	private void markFailed(NotificationRecipient recipient) {
		recipient.markFailed("All configured " + recipient.getChannel() + " providers failed or are unconfigured");
	}
}
