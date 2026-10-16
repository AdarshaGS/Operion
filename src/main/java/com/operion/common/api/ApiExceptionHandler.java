package com.operion.common.api;

import java.util.Map;

import com.operion.authorization.AuthorizationDeniedException;
import com.operion.finance.PaymentGatewayException;
import com.operion.common.InvalidRequestException;
import com.operion.common.TooManyRequestsException;
import com.operion.common.WebhookVerificationException;
import com.operion.identity.auth.AuthenticationFailedException;
import com.operion.platform.auth.PlatformAuthenticationFailedException;
import com.operion.reporting.ReportExecutionException;
import com.operion.storage.AssetStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import com.operion.common.imports.ImportFileException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

	/** Constraints on a parameter that is not a single bean, e.g. the elements of a {@code @RequestBody List<@Valid Dto>}. */
	@ExceptionHandler(HandlerMethodValidationException.class)
	ResponseEntity<Map<String, String>> parameterValidationFailed(HandlerMethodValidationException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid request: a required field is missing or invalid"));
	}

	/** Spring's own request-binding failures are the caller's mistake, not a server fault -
	 * left unmapped they fall into the catch-all below and surface as a 500. */
	@ExceptionHandler(MissingServletRequestParameterException.class)
	ResponseEntity<Map<String, String>> missingParameter(MissingServletRequestParameterException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(Map.of("error", "Required parameter '" + ex.getParameterName() + "' is missing"));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<Map<String, String>> parameterTypeMismatch(MethodArgumentTypeMismatchException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid value for parameter '" + ex.getName() + "'"));
	}

	/** Malformed JSON, or a value Jackson can't bind (e.g. an unknown enum constant). */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<Map<String, String>> unreadableBody(HttpMessageNotReadableException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Malformed or invalid request body"));
	}

	/** An unknown path (e.g. a stale client calling a removed endpoint) is a 404, not a logged server error. */
	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<Map<String, String>> noSuchResource(NoResourceFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No such endpoint"));
	}

	/** An unusable import file, a missing "file" part, or a non-multipart body - all the caller's mistake. */
	@ExceptionHandler(ImportFileException.class)
	ResponseEntity<Map<String, String>> badImportFile(ImportFileException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
	}

	@ExceptionHandler(MissingServletRequestPartException.class)
	ResponseEntity<Map<String, String>> missingPart(MissingServletRequestPartException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Missing required part '" + ex.getRequestPartName() + "' - attach the file"));
	}

	@ExceptionHandler(MultipartException.class)
	ResponseEntity<Map<String, String>> notMultipart(MultipartException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Request must be multipart/form-data with the file attached"));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<Map<String, String>> notFound(IllegalArgumentException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
	}

	@ExceptionHandler(InvalidRequestException.class)
	ResponseEntity<Map<String, String>> invalidRequest(InvalidRequestException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
	}

	@ExceptionHandler(TooManyRequestsException.class)
	ResponseEntity<Map<String, String>> tooManyRequests(TooManyRequestsException ex) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", ex.getMessage()));
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

	@ExceptionHandler(AssetStorageException.class)
	ResponseEntity<Map<String, String>> assetStorageFailed(AssetStorageException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
	}

	/** Spring rejects an oversized multipart body before the controller (and so
	 * AssetStorageService's own size check) ever runs - same 400 shape either way. */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<Map<String, String>> uploadTooLarge(MaxUploadSizeExceededException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "File exceeds the 5MB size limit"));
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
