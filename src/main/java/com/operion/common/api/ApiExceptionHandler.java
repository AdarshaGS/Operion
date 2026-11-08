package com.operion.common.api;

import java.util.Map;
import java.util.regex.Pattern;

import com.operion.backup.BackupFailedException;
import com.operion.authorization.AuthorizationDeniedException;
import com.operion.finance.PaymentGatewayException;
import com.operion.common.InvalidRequestException;
import com.operion.common.TooManyRequestsException;
import com.operion.common.WebhookVerificationException;
import com.operion.identity.auth.AuthenticationFailedException;
import com.operion.platform.auth.PlatformAuthenticationFailedException;
import com.operion.reporting.ReportExecutionException;
import com.operion.student.AdmissionFeePendingException;
import com.operion.storage.AssetStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
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

	/** A verb the endpoint does not offer (e.g. POST/DELETE on a read-only list) is a 405, not a server error. */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<Map<String, String>> methodNotSupported(HttpRequestMethodNotSupportedException ex) {
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(Map.of("error", "Method " + ex.getMethod() + " is not supported for this endpoint"));
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
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", clientMessage(ex)));
	}

	@ExceptionHandler(InvalidRequestException.class)
	ResponseEntity<Map<String, String>> invalidRequest(InvalidRequestException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
	}

	@ExceptionHandler(TooManyRequestsException.class)
	ResponseEntity<Map<String, String>> tooManyRequests(TooManyRequestsException ex) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", ex.getMessage()));
	}

	/** An academic write for a student whose admission fee is unpaid, in an organisation that blocks it. Same {error}
	 * shape as the rest, plus a stable code and the offending enrollment ids so the client can drop those rows and retry. */
	@ExceptionHandler(AdmissionFeePendingException.class)
	ResponseEntity<Map<String, Object>> admissionFeePending(AdmissionFeePendingException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(Map.of("error", ex.getMessage(), "code", "ADMISSION_FEE_PENDING", "enrollmentIds", ex.getEnrollmentIds()));
	}

	@ExceptionHandler(IllegalStateException.class)
	ResponseEntity<Map<String, String>> conflict(IllegalStateException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", clientMessage(ex)));
	}

	/** A DB-level unique constraint firing (e.g. a duplicate code/name within an org) is
	 * still a conflict, not a server fault - controllers that skip a pre-check and rely
	 * on the constraint itself (simple "create" endpoints with no dedicated service,
	 * like Person/Campus/AcademicYear) would otherwise surface a raw 500. */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<Map<String, String>> dataIntegrityConflict(DataIntegrityViolationException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "This record conflicts with an existing one"));
	}

	/** A lock wait timeout or deadlock the database resolved by rolling this request back (CannotAcquireLockException is
	 * a subtype). Nothing was committed, so the caller can simply retry - a 409, not a server fault. */
	@ExceptionHandler(PessimisticLockingFailureException.class)
	ResponseEntity<Map<String, String>> lockContention(PessimisticLockingFailureException ex) {
		log.warn("Request rolled back by database lock contention", ex);
		return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "The system is busy, please retry"));
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

	/** The backup itself failed (mysqldump missing or refused, disk full) - the server's problem, not the caller's.
	 * The exception message names paths, the mysqldump command and its stderr (and a database URL), so it goes to
	 * the log and the System Health backup status, never to the HTTP client. */
	@ExceptionHandler(BackupFailedException.class)
	ResponseEntity<Map<String, String>> backupFailed(BackupFailedException ex) {
		log.error("Backup failed", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "The backup failed - see the server log for details"));
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

	/** Framework/JDK wording that only appears when a raw exception message was never written for a user:
	 * Enum.valueOf ("No enum constant com.operion...."), Long.parseLong ("For input string: ...") or a class name. */
	private static final Pattern INTERNAL_DETAIL = Pattern.compile(
			"No enum constant|For input string|\\b(?:com\\.operion|java|javax|jakarta|org\\.springframework|org\\.hibernate|org\\.apache)\\.[A-Za-z_]");

	/**
	 * IllegalArgumentException/IllegalStateException carry both messages the application wrote for users ("No student with
	 * id 7") and raw JDK/framework ones that name internal classes. The former pass through; the latter are replaced by a
	 * generic line and logged. A null message would also make {@code Map.of} throw, hence the blank check.
	 */
	static String clientMessage(RuntimeException ex) {
		String message = ex.getMessage();
		if (message == null || message.isBlank()) {
			return "The request could not be processed";
		}
		if (ex instanceof NumberFormatException || INTERNAL_DETAIL.matcher(message).find()) {
			log.warn("Internal detail kept out of an API error message", ex);
			return "A value in the request is invalid";
		}
		return message;
	}
}
