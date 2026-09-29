package com.operion.attendance;

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
 * The "student absent -> guardian alert" half of #165 - lives here rather than growing
 * AttendanceService with a dependency on communication/parent, same reasoning as
 * FeeReminderService staying out of FeeService. Reuses the real notification pipeline
 * (CommunicationService.sendTemplatedNotification) rather than a second one, and the same
 * StudentGuardian.canReceiveCommunication opt-in gate FeeReminderService already uses.
 */
@Service
public class AttendanceAlertService {

	private static final String TEMPLATE_CODE = "ATTENDANCE_ABSENCE_ALERT";
	private static final String DEFAULT_SUBJECT = "Absence notice";
	private static final String DEFAULT_BODY = "Your ward was marked absent today. Please contact the school office if this is unexpected.";

	private final StudentGuardianRepository studentGuardianRepository;
	private final NotificationTemplateRepository notificationTemplateRepository;
	private final CommunicationService communicationService;

	public AttendanceAlertService(StudentGuardianRepository studentGuardianRepository,
			NotificationTemplateRepository notificationTemplateRepository, CommunicationService communicationService) {
		this.studentGuardianRepository = studentGuardianRepository;
		this.notificationTemplateRepository = notificationTemplateRepository;
		this.communicationService = communicationService;
	}

	/** Returns how many guardians were actually notified - 0 if this student has no
	 * guardian who's opted in to communication. */
	@Transactional
	public int notifyAbsence(StudentAttendance attendance) {
		Student student = attendance.getStudentEnrollment().getStudent();
		List<Person> recipients = studentGuardianRepository.findByStudentId(student.getId()).stream()
				.filter(studentGuardian -> studentGuardian.getStatus() == StudentGuardianStatus.ACTIVE && studentGuardian.isCanReceiveCommunication())
				.map(studentGuardian -> studentGuardian.getGuardian().getPerson())
				.distinct()
				.toList();
		if (recipients.isEmpty()) {
			return 0;
		}
		return communicationService.sendTemplatedNotification(absenceAlertTemplate(), recipients).size();
	}

	/** Lazy, at most one row per organisation - same convention as FeeReminderService's templates. */
	private NotificationTemplate absenceAlertTemplate() {
		return notificationTemplateRepository.findByCode(TEMPLATE_CODE)
				.orElseGet(() -> notificationTemplateRepository.save(
						new NotificationTemplate(TEMPLATE_CODE, NotificationChannel.EMAIL, DEFAULT_SUBJECT, DEFAULT_BODY)));
	}
}
