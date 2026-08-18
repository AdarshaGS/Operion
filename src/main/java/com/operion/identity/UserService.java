package com.operion.identity;

import java.util.Map;

import com.operion.audit.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

	private final UserRepository userRepository;
	private final AuditLogService auditLogService;

	public UserService(UserRepository userRepository, AuditLogService auditLogService) {
		this.userRepository = userRepository;
		this.auditLogService = auditLogService;
	}

	/** Also the deactivate path (status=DISABLED) - User is a shared/global login identity
	 * referenced by memberships across orgs, so a hard delete isn't offered, same reasoning
	 * RoleService.changeStatus uses for Role. */
	@Transactional
	public User changeStatus(Long userId, UserStatus status) {
		User user = findOrThrow(userId);
		UserStatus previous = user.getStatus();
		user.setStatus(status);
		user = userRepository.save(user);
		auditLogService.record("User", user.getId(), "STATUS_CHANGE", previous, status);
		return user;
	}

	@Transactional
	public User update(Long userId, String email, String phone) {
		User user = findOrThrow(userId);
		Map<String, String> before = Map.of("email", user.getEmail(), "phone", String.valueOf(user.getPhone()));
		user.setEmail(email);
		user.setPhone(phone);
		user = userRepository.save(user);
		auditLogService.record("User", user.getId(), "CONTACT_UPDATE", before, Map.of("email", email, "phone", String.valueOf(phone)));
		return user;
	}

	private User findOrThrow(Long userId) {
		return userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("No user with id " + userId));
	}
}
