package com.operion.finance;

import java.util.List;

import com.operion.communication.CommunicationService;
import com.operion.communication.NotificationChannel;
import com.operion.communication.NotificationTemplate;
import com.operion.communication.NotificationTemplateRepository;
import com.operion.identity.Person;
import com.operion.parent.StudentGuardianRepository;
import com.operion.parent.StudentGuardianStatus;
import com.operion.student.Student;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The proactive-nudge half of #237 - the demand view itself (InvoiceRepository.findOverdue)
 * needed no new module, but "send a reminder" is genuinely cross-cutting between finance
 * (who's overdue) and communication (how a person actually gets notified), so it lives
 * here rather than growing FeeService with a dependency on two unrelated modules' services.
 * Reuses the existing notification pipeline (real EMAIL/SMS dispatch, not a stub) via
 * CommunicationService.sendTemplatedNotification rather than building a second one -
 * per #237's own explicit scope note. Recipients are a student's guardians who've
 * opted in to communication (StudentGuardian.canReceiveCommunication) - same field
 * this codebase already uses to gate guardian contact elsewhere.
 */
@Service
public class FeeReminderService {

	private static final String TEMPLATE_CODE = "FEE_OVERDUE_REMINDER";
	private static final String DEFAULT_SUBJECT = "Fee payment reminder";
	private static final String DEFAULT_BODY =
			"This is a reminder that a fee installment for your ward is overdue. Please log in or contact the school office to clear the outstanding balance.";

	private final StudentGuardianRepository studentGuardianRepository;
	private final NotificationTemplateRepository notificationTemplateRepository;
	private final CommunicationService communicationService;

	public FeeReminderService(StudentGuardianRepository studentGuardianRepository,
			NotificationTemplateRepository notificationTemplateRepository, CommunicationService communicationService) {
		this.studentGuardianRepository = studentGuardianRepository;
		this.notificationTemplateRepository = notificationTemplateRepository;
		this.communicationService = communicationService;
	}

	/** Returns how many guardians were actually notified - 0 if this student has no
	 * guardian who's opted in to communication. */
	@Transactional
	public int sendOverdueReminder(Invoice invoice) {
		Student student = invoice.getStudentFeeAssignment().getStudentEnrollment().getStudent();
		List<Person> recipients = studentGuardianRepository.findByStudentId(student.getId()).stream()
				.filter(studentGuardian -> studentGuardian.getStatus() == StudentGuardianStatus.ACTIVE && studentGuardian.isCanReceiveCommunication())
				.map(studentGuardian -> studentGuardian.getGuardian().getPerson())
				.distinct()
				.toList();
		if (recipients.isEmpty()) {
			return 0;
		}
		return communicationService.sendTemplatedNotification(reminderTemplate(), recipients).size();
	}

	/** Lazy, at most one row per organisation - same convention as ExaminationSettings. */
	private NotificationTemplate reminderTemplate() {
		return notificationTemplateRepository.findByCode(TEMPLATE_CODE)
				.orElseGet(() -> notificationTemplateRepository.save(
						new NotificationTemplate(TEMPLATE_CODE, NotificationChannel.EMAIL, DEFAULT_SUBJECT, DEFAULT_BODY)));
	}
}
