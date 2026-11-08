package com.operion.messaging;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.operion.academic.Section;
import com.operion.academic.TeacherAssignment;
import com.operion.academic.TeacherAssignmentRepository;
import com.operion.academic.TeacherAssignmentStatus;
import com.operion.authorization.AuthorizationDeniedException;
import com.operion.identity.Person;
import com.operion.messaging.api.MessageResponse;
import com.operion.parent.StudentGuardianRepository;
import com.operion.student.Student;
import com.operion.student.StudentEnrollment;
import com.operion.student.StudentEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two-way messaging (#239) - distinct from CommunicationService's one-way Announcement
 * fan-out: a CLASS_GROUP thread is get-or-created lazily per Section (auto-membership:
 * enrolled students + their guardians + the section's active teacher assignments - the
 * one deliberate gap CommunicationService's own doc comment left open for "a v2 scope
 * call"), a DIRECT thread is get-or-created lazily between any two Persons. Membership in
 * a thread (ThreadParticipant) IS the authorization model here - there's no separate
 * MESSAGE_SEND permission, matching how ORG audience already treats "every active
 * membership" as the baseline scope for this module.
 */
@Service
public class MessagingService {

	private final MessageThreadRepository messageThreadRepository;
	private final ThreadParticipantRepository threadParticipantRepository;
	private final MessageRepository messageRepository;
	private final StudentEnrollmentRepository studentEnrollmentRepository;
	private final StudentGuardianRepository studentGuardianRepository;
	private final TeacherAssignmentRepository teacherAssignmentRepository;
	private final MessageBroadcaster broadcaster;

	public MessagingService(MessageThreadRepository messageThreadRepository, ThreadParticipantRepository threadParticipantRepository,
			MessageRepository messageRepository, StudentEnrollmentRepository studentEnrollmentRepository,
			StudentGuardianRepository studentGuardianRepository, TeacherAssignmentRepository teacherAssignmentRepository,
			MessageBroadcaster broadcaster) {
		this.messageThreadRepository = messageThreadRepository;
		this.threadParticipantRepository = threadParticipantRepository;
		this.messageRepository = messageRepository;
		this.studentEnrollmentRepository = studentEnrollmentRepository;
		this.studentGuardianRepository = studentGuardianRepository;
		this.teacherAssignmentRepository = teacherAssignmentRepository;
		this.broadcaster = broadcaster;
	}

	/**
	 * The REST entry point for a class-group thread. Access rule: the requester must be one of the
	 * section's would-be members (an enrolled student, one of an enrolled student's guardians, or an
	 * ACTIVE teacher assignment of the section) - exactly the set classGroupMembers() computes, so a
	 * person can open the thread if and only if they belong in it. Anyone else gets
	 * AuthorizationDeniedException (403) and nothing is created: without this any authenticated member
	 * of the organisation could create the thread for any section and read back its full roster
	 * (students' and guardians' names). There is deliberately no admin override - an admin who is not
	 * a member could not read or post in the thread anyway (requireParticipant), so letting them open
	 * it would only expose the roster; whether admins should be able to moderate class groups is a
	 * product decision, not something to invent a permission for here.
	 *
	 * <p>The participant list is also re-synced to the section's current membership every time the
	 * thread is opened (a student enrolled, withdrawn or moved, a guardian linked, a teacher assigned
	 * or unassigned since the thread was created): missing members are added and people who no longer
	 * belong are removed. It is NOT re-synced on read/send/subscribe, so a person who left stays in the
	 * thread until someone next opens it.
	 */
	@Transactional
	public MessageThread openClassGroupThread(Section section, Person requester) {
		Map<Long, Person> members = new LinkedHashMap<>();
		classGroupMembers(section).forEach(person -> members.put(person.getId(), person));
		if (!members.containsKey(requester.getId())) {
			throw new AuthorizationDeniedException("Only a student, guardian or teacher of this section can open its class group");
		}
		Optional<MessageThread> existing = messageThreadRepository.findBySectionIdAndType(section.getId(), MessageThreadType.CLASS_GROUP);
		if (existing.isEmpty()) {
			return createClassGroupThread(section, members.values());
		}
		syncClassGroupParticipants(existing.get(), members);
		return existing.get();
	}

	/** Creation without an access check or re-sync - package-private so only this service's own flows
	 * (and its test) can reach it; callers outside go through openClassGroupThread. */
	@Transactional
	MessageThread getOrCreateClassGroupThread(Section section) {
		return messageThreadRepository.findBySectionIdAndType(section.getId(), MessageThreadType.CLASS_GROUP)
				.orElseGet(() -> createClassGroupThread(section, classGroupMembers(section)));
	}

	private MessageThread createClassGroupThread(Section section, Collection<Person> members) {
		MessageThread thread = messageThreadRepository.save(new MessageThread(MessageThreadType.CLASS_GROUP, section));
		for (Person person : members) {
			threadParticipantRepository.save(new ThreadParticipant(thread, person));
		}
		return thread;
	}

	private void syncClassGroupParticipants(MessageThread thread, Map<Long, Person> members) {
		Set<Long> present = new HashSet<>();
		for (ThreadParticipant participant : threadParticipantRepository.findByThreadId(thread.getId())) {
			Long personId = participant.getPerson().getId();
			if (members.containsKey(personId)) {
				present.add(personId);
			} else {
				threadParticipantRepository.delete(participant);
			}
		}
		members.forEach((personId, person) -> {
			if (!present.contains(personId)) {
				threadParticipantRepository.save(new ThreadParticipant(thread, person));
			}
		});
	}

	private Set<Person> classGroupMembers(Section section) {
		Set<Person> persons = new LinkedHashSet<>();
		for (StudentEnrollment enrollment : studentEnrollmentRepository.findBySectionIdAndCurrentTrue(section.getId())) {
			Student student = enrollment.getStudent();
			persons.add(student.getPerson());
			studentGuardianRepository.findByStudentId(student.getId())
					.forEach(studentGuardian -> persons.add(studentGuardian.getGuardian().getPerson()));
		}
		teacherAssignmentRepository.findBySectionId(section.getId()).stream()
				.filter(assignment -> assignment.getStatus() == TeacherAssignmentStatus.ACTIVE)
				.map(TeacherAssignment::getTeacherPerson)
				.forEach(persons::add);
		return persons;
	}

	/** Idempotent both ways - the same pair in either order resolves to the same thread. */
	@Transactional
	public MessageThread getOrCreateDirectThread(Person personA, Person personB) {
		if (personA.getId().equals(personB.getId())) {
			throw new IllegalArgumentException("Cannot start a direct message thread with yourself");
		}
		return messageThreadRepository.findDirectThreadBetween(personA.getId(), personB.getId()).orElseGet(() -> {
			MessageThread thread = messageThreadRepository.save(new MessageThread(MessageThreadType.DIRECT, null));
			threadParticipantRepository.save(new ThreadParticipant(thread, personA));
			threadParticipantRepository.save(new ThreadParticipant(thread, personB));
			return thread;
		});
	}

	/** Sending also marks the sender's own read position current - they obviously just
	 * read up to what they wrote, so it never shows as unread for them. */
	@Transactional
	public Message sendMessage(MessageThread thread, Person sender, String body) {
		ThreadParticipant senderParticipant = requireParticipant(thread, sender);
		Message message = messageRepository.save(new Message(thread, sender, body));
		thread.touch(message.getCreatedAt());
		messageThreadRepository.save(thread);
		senderParticipant.markRead(message.getCreatedAt());
		threadParticipantRepository.save(senderParticipant);
		broadcaster.broadcast(thread.getId(), MessageResponse.from(message));
		return message;
	}

	@Transactional(readOnly = true)
	public List<Message> listMessages(MessageThread thread, Person requester) {
		requireParticipant(thread, requester);
		return messageRepository.findByThreadIdOrderByCreatedAtAsc(thread.getId());
	}

	@Transactional(readOnly = true)
	public List<ThreadSummary> listThreadsForPerson(Person person) {
		return messageThreadRepository.findByParticipantPersonId(person.getId()).stream()
				.map(thread -> new ThreadSummary(thread, isUnreadFor(thread, person)))
				.toList();
	}

	@Transactional(readOnly = true)
	public boolean isUnreadFor(MessageThread thread, Person person) {
		if (thread.getLastMessageAt() == null) {
			return false;
		}
		ThreadParticipant participant = requireParticipant(thread, person);
		return participant.getLastReadAt() == null || participant.getLastReadAt().isBefore(thread.getLastMessageAt());
	}

	@Transactional
	public void markRead(MessageThread thread, Person person) {
		ThreadParticipant participant = requireParticipant(thread, person);
		participant.markRead(Instant.now());
		threadParticipantRepository.save(participant);
	}

	public List<ThreadParticipant> listParticipants(MessageThread thread) {
		return threadParticipantRepository.findByThreadId(thread.getId());
	}

	public boolean isParticipant(Long threadId, Long personId) {
		return threadParticipantRepository.existsByThreadIdAndPersonId(threadId, personId);
	}

	private ThreadParticipant requireParticipant(MessageThread thread, Person person) {
		return threadParticipantRepository.findByThreadIdAndPersonId(thread.getId(), person.getId())
				.orElseThrow(() -> new AuthorizationDeniedException("Not a participant of this thread"));
	}

	public record ThreadSummary(MessageThread thread, boolean unread) {
	}
}
