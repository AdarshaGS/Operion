package com.operion.communication;

import com.operion.common.TenantScopedEntity;
import com.operion.identity.Person;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-person, per-channel opt-in/out. Absence of a row means enabled (opt-out model,
 * not opt-in) - CommunicationService defaults to enabled when no row exists, so a
 * person doesn't have to explicitly opt into every channel to receive anything.
 */
@Getter
@Entity
@Table(name = "notification_preferences")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationPreference extends TenantScopedEntity {

	@ManyToOne(optional = false)
	@JoinColumn(name = "person_id")
	private Person person;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NotificationChannel channel;

	@Setter
	@Column(name = "is_enabled", nullable = false)
	private boolean enabled;

	public NotificationPreference(Person person, NotificationChannel channel, boolean enabled) {
		this.person = person;
		this.channel = channel;
		this.enabled = enabled;
	}
}
