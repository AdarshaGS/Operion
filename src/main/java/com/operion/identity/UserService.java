package com.operion.identity;

import java.util.Map;

import com.operion.audit.AuditLogService;
import com.operion.authorization.AuthorizationDeniedException;
import com.operion.authorization.MembershipStatus;
import com.operion.authorization.OrganisationMembershipRepository;
import com.operion.authorization.PermissionChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

	private final UserRepository userRepository;
	private final AuditLogService auditLogService;
	private final OrganisationMembershipRepository membershipRepository;
	private final PermissionChecker permissionChecker;

	public UserService(UserRepository userRepository, AuditLogService auditLogService,
			OrganisationMembershipRepository membershipRepository) {
		this.userRepository = userRepository;
		this.auditLogService = auditLogService;
		this.membershipRepository = membershipRepository;
		this.permissionChecker = new PermissionChecker(membershipRepository);
	}

	/** Also the deactivate path (status=DISABLED) - User is a shared/global login identity
	 * referenced by memberships across orgs, so a hard delete isn't offered, same reasoning
	 * RoleService.changeStatus uses for Role. */
	@Transactional
	public User changeStatus(Long userId, UserStatus status) {
		User user = findOrThrow(userId);
		if (status != UserStatus.ACTIVE && isOnlyActiveOwner(userId)) {
			throw new IllegalStateException("The organisation's only Owner cannot be deactivated - ownership transfer is not supported yet");
		}
		UserStatus previous = user.getStatus();
		user.setStatus(status);
		user = userRepository.save(user);
		auditLogService.record("User", user.getId(), "STATUS_CHANGE", previous, status);
		return user;
	}

	@Transactional
	public User update(Long userId, String email, String phone) {
		return update(userId, email, phone, null);
	}

	/** {@code actorId} is the signed-in user on the HTTP path. A login email is how a password reset reaches an account, so
	 * changing someone else's is only allowed when the caller already holds everything that account holds - otherwise
	 * MEMBERSHIP_MANAGE could redirect the Owner's (or an admin's) email and take the account over through a reset (SEC-M1). */
	@Transactional
	public User update(Long userId, String email, String phone, Long actorId) {
		if (actorId != null && !actorId.equals(userId)) {
			boolean targetIsOwner = membershipRepository.existsByUserIdAndStatusAndOwner(userId, MembershipStatus.ACTIVE, true);
			boolean actorIsOwner = membershipRepository.existsByUserIdAndStatusAndOwner(actorId, MembershipStatus.ACTIVE, true);
			if (targetIsOwner && !actorIsOwner) {
				throw new AuthorizationDeniedException("Only the Owner can change the Owner's email or phone");
			}
			permissionChecker.requireAll(actorId, membershipRepository.findActivePermissionCodesForUser(userId),
					"You can only change the email or phone of someone whose access you already have yourself");
		}
		User user = findOrThrow(userId);
		Map<String, String> before = Map.of("email", user.getEmail(), "phone", String.valueOf(user.getPhone()));
		user.setEmail(email);
		user.setPhone(phone);
		user = userRepository.save(user);
		auditLogService.record("User", user.getId(), "CONTACT_UPDATE", before, Map.of("email", email, "phone", String.valueOf(phone)));
		return user;
	}

	private boolean isOnlyActiveOwner(Long userId) {
		var activeOwners = membershipRepository.findByStatus(MembershipStatus.ACTIVE).stream().filter(m -> m.isOwner()).toList();
		return activeOwners.stream().anyMatch(m -> m.getUser().getId().equals(userId))
				&& activeOwners.stream().allMatch(m -> m.getUser().getId().equals(userId));
	}

	private User findOrThrow(Long userId) {
		return userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("No user with id " + userId));
	}
}
