package com.operion.platform;

import com.operion.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A separate login identity from {@link com.operion.identity.User} - deliberately not
 * organisation-scoped (extends BaseEntity, not TenantScopedEntity, the same choice
 * Organisation itself makes) since a platform admin's whole purpose is to see across
 * every organisation. Authenticated via its own token plane (see
 * com.operion.platform.auth), never through JwtAuthenticationInterceptor /
 * OrganisationMembership / Role / Permission - those concepts don't apply here.
 */
@Getter
@Entity
@Table(name = "platform_admins")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformAdmin extends BaseEntity {

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private PlatformAdminStatus status;

	public PlatformAdmin(String name, String email, String passwordHash) {
		this.name = name;
		this.email = email;
		this.passwordHash = passwordHash;
		this.status = PlatformAdminStatus.ACTIVE;
	}

	public void changeStatus(PlatformAdminStatus target) {
		this.status = target;
	}
}
