package com.operion.platform.api;

import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

import com.operion.common.SchedulerHeartbeat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Narrower than the mockup's 5-chip panel (API/Database/Auth/Background Jobs/Email) -
 * "Auth"/"Email" still have no distinct health signal beyond "the API responded" / a
 * real third-party reachability check, out of scope for a lightweight request-time
 * check. Reports what's genuinely verifiable: the API itself (trivially true), the
 * primary database connection (an actual `isValid` probe), and background-job liveness
 * (SchedulerHeartbeat - there's still no job-queue table, so this is "did a poller tick
 * recently", not a queue-depth check). See the ticket for the staged plan - latency/
 * error-rate metrics need real request-level instrumentation, a separate effort.
 */
@RestController
public class SystemHealthController {

	private final DataSource dataSource;
	private final SchedulerHeartbeat schedulerHeartbeat;

	public SystemHealthController(DataSource dataSource, SchedulerHeartbeat schedulerHeartbeat) {
		this.dataSource = dataSource;
		this.schedulerHeartbeat = schedulerHeartbeat;
	}

	@GetMapping("/api/v1/platform/system-health")
	public List<SystemHealthComponentResponse> systemHealth() {
		return List.of(
				new SystemHealthComponentResponse("API", true),
				new SystemHealthComponentResponse("Database", databaseIsUp()),
				new SystemHealthComponentResponse("Background Jobs", schedulerHeartbeat.isHealthy()));
	}

	private boolean databaseIsUp() {
		try (Connection connection = dataSource.getConnection()) {
			return connection.isValid(2);
		} catch (Exception e) {
			return false;
		}
	}
}
