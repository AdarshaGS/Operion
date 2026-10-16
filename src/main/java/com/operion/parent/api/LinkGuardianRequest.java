package com.operion.parent.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Flags left out default to what the link form pre-selects: not primary, not an emergency contact, may pick up and receive communication, priority 1. */
public record LinkGuardianRequest(@NotNull Long guardianId, @NotBlank String relationshipType, Boolean primaryGuardian,
		Boolean emergencyContact, Boolean canPickup, Boolean canReceiveCommunication, Integer contactPriority) {
}
