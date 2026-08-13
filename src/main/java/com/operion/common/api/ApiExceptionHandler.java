package com.operion.common.api;

import java.util.Map;

import com.operion.authorization.AuthorizationDeniedException;
import com.operion.finance.PaymentGatewayException;
import com.operion.finance.WebhookVerificationException;
import com.operion.identity.auth.AuthenticationFailedException;
import com.operion.platform.auth.PlatformAuthenticationFailedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Applies application-wide, not just to one module's controllers. */
@RestControllerAdvice
class ApiExceptionHandler {

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<Map<String, String>> notFound(IllegalArgumentException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
	}

	@ExceptionHandler(IllegalStateException.class)
	ResponseEntity<Map<String, String>> conflict(IllegalStateException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
	}

	/** A DB-level unique constraint firing (e.g. a duplicate code/name within an org) is
	 * still a conflict, not a server fault - controllers that skip a pre-check and rely
	 * on the constraint itself (simple "create" endpoints with no dedicated service,
	 * like Person/Campus/AcademicYear) would otherwise surface a raw 500. */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<Map<String, String>> dataIntegrityConflict(DataIntegrityViolationException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "This record conflicts with an existing one"));
	}

	@ExceptionHandler(AuthenticationFailedException.class)
	ResponseEntity<Map<String, String>> unauthorized(AuthenticationFailedException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", ex.getMessage()));
	}

	@ExceptionHandler(PlatformAuthenticationFailedException.class)
	ResponseEntity<Map<String, String>> platformUnauthorized(PlatformAuthenticationFailedException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", ex.getMessage()));
	}

	@ExceptionHandler(WebhookVerificationException.class)
	ResponseEntity<Map<String, String>> webhookUnauthorized(WebhookVerificationException ex) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", ex.getMessage()));
	}

	@ExceptionHandler(PaymentGatewayException.class)
	ResponseEntity<Map<String, String>> paymentGatewayFailure(PaymentGatewayException ex) {
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
	}

	@ExceptionHandler(AuthorizationDeniedException.class)
	ResponseEntity<Map<String, String>> forbidden(AuthorizationDeniedException ex) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
	}
}
