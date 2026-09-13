package com.operion.platform.api;

import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Deliberately narrower than the mockup's 5-chip panel (API/Database/Auth/Background
 * Jobs/Email) - this codebase has no scheduler/queue (nothing to check for "Background
 * Jobs"), and "Auth"/"Email" have no distinct health signal beyond "the API responded" /
 * a real third-party reachability check, which is out of scope for a lightweight
 * request-time check. Reporting only what's genuinely verifiable: the API itself
 * (trivially true - a response came back) and the primary database connection (an actual
 * `isValid` probe, not a hardcoded "operational"). See the ticket for the staged plan -
 * latency/error-rate metrics need real request-level instrumentation, a separate effort.
 */
@RestController
public class SystemHealthController {

	private final DataSource dataSource;

	public SystemHealthController(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@GetMapping("/api/v1/platform/system-health")
	public List<SystemHealthComponentResponse> systemHealth() {
		return List.of(new SystemHealthComponentResponse("API", true), new SystemHealthComponentResponse("Database", databaseIsUp()));
	}

	private boolean databaseIsUp() {
		try (Connection connection = dataSource.getConnection()) {
			return connection.isValid(2);
		} catch (Exception e) {
			return false;
		}
	}
}
