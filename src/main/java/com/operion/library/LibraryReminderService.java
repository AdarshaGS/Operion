package com.operion.library;

import java.util.List;

import com.operion.communication.CommunicationService;
import com.operion.communication.NotificationChannel;
import com.operion.communication.NotificationTemplate;
import com.operion.communication.NotificationTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reminder action on the Overdue list (#171) - cross-cutting between library (who's
 * overdue) and communication (how they get notified), same shape and reasoning as
 * finance's FeeReminderService. Unlike FeeReminderService, there's no guardian
 * indirection here: BorrowRecord.borrower is already the Person to notify (a student or
 * staff member, not necessarily a minor with a guardian on file).
 */
@Service
public class LibraryReminderService {

	private static final String TEMPLATE_CODE = "LIBRARY_OVERDUE_REMINDER";
	private static final String DEFAULT_SUBJECT = "Library book overdue";
	private static final String DEFAULT_BODY =
			"This is a reminder that a library book you borrowed is overdue. Please return it or contact the library to avoid further fines.";

	private final NotificationTemplateRepository notificationTemplateRepository;
	private final CommunicationService communicationService;

	public LibraryReminderService(NotificationTemplateRepository notificationTemplateRepository, CommunicationService communicationService) {
		this.notificationTemplateRepository = notificationTemplateRepository;
		this.communicationService = communicationService;
	}

	/** Returns how many notifications were actually queued - 0 if the borrower has no
	 * usable contact channel on file. */
	@Transactional
	public int sendOverdueReminder(BorrowRecord borrowRecord) {
		return communicationService.sendTemplatedNotification(reminderTemplate(), List.of(borrowRecord.getBorrower())).size();
	}

	/** Lazy, at most one row per organisation - same convention as ExaminationSettings/FeeReminderService. */
	private NotificationTemplate reminderTemplate() {
		return notificationTemplateRepository.findByCode(TEMPLATE_CODE)
				.orElseGet(() -> notificationTemplateRepository.save(
						new NotificationTemplate(TEMPLATE_CODE, NotificationChannel.EMAIL, DEFAULT_SUBJECT, DEFAULT_BODY)));
	}
}
