package com.operion.common.api;

import java.util.Map;

import com.operion.authorization.AuthorizationDeniedException;
import com.operion.finance.PaymentGatewayException;
import com.operion.finance.WebhookVerificationException;
import com.operion.identity.auth.AuthenticationFailedException;
import com.operion.platform.auth.PlatformAuthenticationFailedException;
import com.operion.reporting.ReportExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Applies application-wide, not just to one module's controllers. */
@RestControllerAdvice
class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<Map<String, String>> validationFailed(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.map(error -> error.getField() + " " + error.getDefaultMessage())
				.reduce((a, b) -> a + "; " + b)
				.orElse("Validation failed");
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
	}

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

	@ExceptionHandler(ReportExecutionException.class)
	ResponseEntity<Map<String, String>> reportExecutionFailed(ReportExecutionException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
	}

	/** Catch-all for anything not mapped above - an uncaught bug must still return the
	 * app's standard {error} shape instead of leaking a raw 500 or stack trace. Logged at
	 * error level (with the full exception) since, unlike every handler above, this always
	 * represents a genuine defect rather than an expected business-rule rejection. */
	@ExceptionHandler(Exception.class)
	ResponseEntity<Map<String, String>> unexpected(Exception ex) {
		log.error("Unhandled exception", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred"));
	}
}
