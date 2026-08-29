package com.operion.messaging.api;

import java.time.Instant;
import java.util.List;

import com.operion.messaging.MessageThread;
import com.operion.messaging.MessagingService;
import com.operion.messaging.ThreadParticipant;

public record MessageThreadResponse(Long id, String type, Long sectionId, String sectionLabel, Instant lastMessageAt, boolean unread,
		List<ParticipantSummary> participants) {

	public static MessageThreadResponse from(MessagingService.ThreadSummary summary, List<ThreadParticipant> participants) {
		MessageThread thread = summary.thread();
		List<ParticipantSummary> summaries = participants.stream()
				.map(participant -> new ParticipantSummary(participant.getPerson().getId(), displayName(participant)))
				.toList();
		return new MessageThreadResponse(thread.getId(), thread.getType().name(),
				thread.getSection() == null ? null : thread.getSection().getId(), sectionLabel(thread), thread.getLastMessageAt(),
				summary.unread(), summaries);
	}

	private static String sectionLabel(MessageThread thread) {
		if (thread.getSection() == null) {
			return null;
		}
		String className = thread.getSection().getSchoolClass().getDisplayName();
		return (className == null ? "Class #" + thread.getSection().getSchoolClass().getId() : className) + " - "
				+ thread.getSection().getName();
	}

	private static String displayName(ThreadParticipant participant) {
		String first = participant.getPerson().getFirstName();
		String last = participant.getPerson().getLastName();
		return last == null ? first : first + " " + last;
	}

	public record ParticipantSummary(Long personId, String name) {
	}
}
