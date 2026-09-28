package com.operion.transport;

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

/**
 * Fires guardian notifications for the two Transport events the product spec calls
 * out - a trip cancellation and a route/stop reassignment - via the shared
 * notification pipeline (CommunicationService.sendTemplatedNotification), same
 * precedent as FeeReminderService/LibraryReminderService rather than a parallel one.
 * Recipients are a student's active guardians who've opted in to communication
 * (StudentGuardian.canReceiveCommunication), same gate used elsewhere. "Significant
 * delay" from the spec has no modeled trigger yet (TripLog has no delayed state) so
 * it isn't wired here.
 */
@Service
public class TransportNotificationService {

	private static final String TRIP_CANCELLED_TEMPLATE_CODE = "TRANSPORT_TRIP_CANCELLED";
	private static final String TRIP_CANCELLED_SUBJECT = "Transport trip cancelled";
	private static final String TRIP_CANCELLED_BODY =
			"A scheduled transport trip on your ward's route has been cancelled. Please arrange alternate transport for today.";

	private static final String ROUTE_REASSIGNED_TEMPLATE_CODE = "TRANSPORT_ROUTE_REASSIGNED";
	private static final String ROUTE_REASSIGNED_SUBJECT = "Transport route updated";
	private static final String ROUTE_REASSIGNED_BODY =
			"Your ward's transport route/stop has been updated. Please check the parent portal for the new pickup/drop details.";

	private final StudentGuardianRepository studentGuardianRepository;
	private final NotificationTemplateRepository notificationTemplateRepository;
	private final CommunicationService communicationService;

	public TransportNotificationService(StudentGuardianRepository studentGuardianRepository,
			NotificationTemplateRepository notificationTemplateRepository, CommunicationService communicationService) {
		this.studentGuardianRepository = studentGuardianRepository;
		this.notificationTemplateRepository = notificationTemplateRepository;
		this.communicationService = communicationService;
	}

	/** Notifies the guardians of every student with an active assignment on the cancelled
	 * trip's route. Returns how many guardians were actually notified. */
	public int notifyTripCancelled(List<StudentTransportAssignment> affectedAssignments) {
		List<Person> recipients = affectedAssignments.stream()
				.map(assignment -> assignment.getStudentEnrollment().getStudent())
				.distinct()
				.flatMap(student -> guardiansOf(student).stream())
				.distinct()
				.toList();
		if (recipients.isEmpty()) {
			return 0;
		}
		return communicationService.sendTemplatedNotification(tripCancelledTemplate(), recipients).size();
	}

	/** Notifies the guardians of the one student whose route/stop was just reassigned.
	 * Returns how many guardians were actually notified. */
	public int notifyRouteReassigned(StudentTransportAssignment newAssignment) {
		List<Person> recipients = guardiansOf(newAssignment.getStudentEnrollment().getStudent());
		if (recipients.isEmpty()) {
			return 0;
		}
		return communicationService.sendTemplatedNotification(routeReassignedTemplate(), recipients).size();
	}

	private List<Person> guardiansOf(Student student) {
		return studentGuardianRepository.findByStudentId(student.getId()).stream()
				.filter(studentGuardian -> studentGuardian.getStatus() == StudentGuardianStatus.ACTIVE && studentGuardian.isCanReceiveCommunication())
				.map(studentGuardian -> studentGuardian.getGuardian().getPerson())
				.distinct()
				.toList();
	}

	/** Lazy, at most one row per organisation - same convention as FeeReminderService's templates. */
	private NotificationTemplate tripCancelledTemplate() {
		return notificationTemplateRepository.findByCode(TRIP_CANCELLED_TEMPLATE_CODE)
				.orElseGet(() -> notificationTemplateRepository.save(
						new NotificationTemplate(TRIP_CANCELLED_TEMPLATE_CODE, NotificationChannel.EMAIL, TRIP_CANCELLED_SUBJECT, TRIP_CANCELLED_BODY)));
	}

	private NotificationTemplate routeReassignedTemplate() {
		return notificationTemplateRepository.findByCode(ROUTE_REASSIGNED_TEMPLATE_CODE)
				.orElseGet(() -> notificationTemplateRepository.save(
						new NotificationTemplate(ROUTE_REASSIGNED_TEMPLATE_CODE, NotificationChannel.EMAIL, ROUTE_REASSIGNED_SUBJECT, ROUTE_REASSIGNED_BODY)));
	}
}
