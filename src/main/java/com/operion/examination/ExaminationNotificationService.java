package com.operion.examination;

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
 * The "exam result published -> guardian notification" half of #165 - lives here rather
 * than growing ExaminationService with a dependency on communication/parent, same
 * reasoning as FeeReminderService staying out of FeeService. Reuses the real notification
 * pipeline and the same StudentGuardian.canReceiveCommunication opt-in gate.
 */
@Service
public class ExaminationNotificationService {

	private static final String TEMPLATE_CODE = "EXAM_RESULT_PUBLISHED";
	private static final String DEFAULT_SUBJECT = "Exam result published";
	private static final String DEFAULT_BODY = "Your ward's exam result has been published. Please log in to view the report card.";

	private final StudentGuardianRepository studentGuardianRepository;
	private final NotificationTemplateRepository notificationTemplateRepository;
	private final CommunicationService communicationService;

	public ExaminationNotificationService(StudentGuardianRepository studentGuardianRepository,
			NotificationTemplateRepository notificationTemplateRepository, CommunicationService communicationService) {
		this.studentGuardianRepository = studentGuardianRepository;
		this.notificationTemplateRepository = notificationTemplateRepository;
		this.communicationService = communicationService;
	}

	/** Returns how many guardians were actually notified - 0 if this student has no
	 * guardian who's opted in to communication. */
	@Transactional
	public int notifyResultPublished(ReportCard reportCard) {
		Student student = reportCard.getStudentEnrollment().getStudent();
		List<Person> recipients = studentGuardianRepository.findByStudentId(student.getId()).stream()
				.filter(studentGuardian -> studentGuardian.getStatus() == StudentGuardianStatus.ACTIVE && studentGuardian.isCanReceiveCommunication())
				.map(studentGuardian -> studentGuardian.getGuardian().getPerson())
				.distinct()
				.toList();
		if (recipients.isEmpty()) {
			return 0;
		}
		return communicationService.sendTemplatedNotification(resultPublishedTemplate(), recipients).size();
	}

	/** Lazy, at most one row per organisation - same convention as FeeReminderService's templates. */
	private NotificationTemplate resultPublishedTemplate() {
		return notificationTemplateRepository.findByCode(TEMPLATE_CODE)
				.orElseGet(() -> notificationTemplateRepository.save(
						new NotificationTemplate(TEMPLATE_CODE, NotificationChannel.EMAIL, DEFAULT_SUBJECT, DEFAULT_BODY)));
	}
}
