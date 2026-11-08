package com.operion.identity;

import java.time.Instant;

import com.operion.common.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Auth identity only - no name, no role, no business meaning. Per-school: the account
 * lives in the shared School database and belongs to exactly one organisation, so the same
 * email at two schools is two independent accounts (own password, own lockout), unique only
 * within an organisation.
 */
@Getter
@Entity
@Table(name = "users", uniqueConstraints = {
		@UniqueConstraint(name = "uq_users_org_email", columnNames = { "organisation_id", "email" }),
		@UniqueConstraint(name = "uq_users_org_phone", columnNames = { "organisation_id", "phone" }) })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends TenantScopedEntity {

	@Setter
	@Column(nullable = false)
	private String email;

	@Setter
	@Column
	private String phone;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	/** Bumped on every password change and stamped into each access JWT ("cv" claim); the
	 * interceptor rejects a token whose version no longer matches, so changing or resetting a
	 * password cuts off access tokens already in someone's hands, not just future logins. */
	@Column(name = "credential_version", nullable = false)
	private long credentialVersion;

	@Setter
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserStatus status;

	@Setter
	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	@Setter
	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;

	/** Every route that replaces the hash (self-service change, reset, invite claim) goes
	 * through here, so each one invalidates previously issued access tokens without having to
	 * remember to. */
	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
		this.credentialVersion++;
	}

	public User(String email, String phone, String passwordHash) {
		this.email = email;
		this.phone = phone;
		this.passwordHash = passwordHash;
		this.status = UserStatus.ACTIVE;
	}
}
