package com.operion.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.operion.backup.BackupFailedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Raw JDK/framework messages (enum class names, parse errors, mysqldump output) must not reach API clients. */
class ApiExceptionHandlerLeakTest {

	private final ApiExceptionHandler handler = new ApiExceptionHandler();

	@Test
	void enumValueOfMessageIsReplacedByAGenericOne() {
		var response = handler.notFound(new IllegalArgumentException("No enum constant com.operion.transport.VehicleType.HOVERCRAFT"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody().get("error")).doesNotContain("com.operion").doesNotContain("enum constant");
	}

	@Test
	void numberFormatAndClassNameMessagesAreReplaced() {
		assertThat(handler.notFound(new NumberFormatException("For input string: \"abc\"")).getBody().get("error")).doesNotContain("abc");
		assertThat(handler.conflict(new IllegalStateException("Cannot invoke \"java.util.List.size()\" because x is null")).getBody().get("error"))
				.doesNotContain("java.util");
	}

	@Test
	void aNullMessageNoLongerBlowsUpTheHandler() {
		assertThat(handler.notFound(new IllegalArgumentException()).getBody().get("error")).isNotBlank();
	}

	@Test
	void messagesWrittenForUsersStillPassThrough() {
		assertThat(handler.notFound(new IllegalArgumentException("No student with id 7")).getBody().get("error")).isEqualTo("No student with id 7");
		var conflict = handler.conflict(new IllegalStateException("Academic year is closed"));
		assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(conflict.getBody().get("error")).isEqualTo("Academic year is closed");
	}

	@Test
	void backupFailureDetailsStayInTheLog() {
		var response = handler.backupFailed(new BackupFailedException("mysqldump exited 2: /var/backups/operion jdbc:mysql://db:3306/operion access denied"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getBody().get("error")).doesNotContain("mysqldump").doesNotContain("/var/backups").doesNotContain("jdbc");
	}

	@Test
	void anUnsupportedHttpMethodIsA405NotA500() {
		var response = handler.methodNotSupported(new org.springframework.web.HttpRequestMethodNotSupportedException("DELETE"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
		assertThat(response.getBody().get("error")).contains("DELETE");
	}

	@Test
	void aDatabaseDeadlockIsA409WithAGenericMessage() {
		var response = handler.lockContention(new org.springframework.dao.CannotAcquireLockException(
				"could not execute statement; Deadlock found when trying to get lock; try restarting transaction on student_id_counters"));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody().get("error")).isEqualTo("The system is busy, please retry").doesNotContain("student_id_counters");
	}
}
