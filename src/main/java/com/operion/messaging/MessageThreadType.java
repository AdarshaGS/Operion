package com.operion.messaging;

/** CLASS_GROUP auto-scopes to exactly one Section (sectionId set, exactly one thread per
 * section, get-or-created lazily - see MessagingService.getOrCreateClassGroupThread).
 * DIRECT is a 1:1 between exactly two Persons (sectionId null, exactly two
 * ThreadParticipant rows). */
public enum MessageThreadType {
	CLASS_GROUP,
	DIRECT
}
