package com.operion.identity;

import java.time.Instant;

import com.operion.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Auth identity only - no name, no role, no business meaning. Deliberately global
 * (not organisation-scoped): the same person may need to log into multiple schools
 * with one identity, per ai-context/erp-system-plan.md §1.1.
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

	@Column(nullable = false, unique = true)
	private String email;

	@Column(unique = true)
	private String phone;

	@Setter
	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Setter
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserStatus status;

	@Setter
	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	public User(String email, String phone, String passwordHash) {
		this.email = email;
		this.phone = phone;
		this.passwordHash = passwordHash;
		this.status = UserStatus.ACTIVE;
	}
}
