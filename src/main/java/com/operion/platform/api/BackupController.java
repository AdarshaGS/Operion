package com.operion.platform.api;

import com.operion.backup.BackupService;
import com.operion.backup.BackupService.BackupStatus;
import com.operion.backup.BackupService.BackupSummary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local backups for platform admins: the schedule, the last attempt and the backups on disk, and a
 * "back up now". Restoring is deliberately not an endpoint - it replaces both databases, so it is done
 * with scripts/restore-backup.sh while the app is stopped (docs/setup/backup-and-recovery.md).
 */
@RestController
public class BackupController {

	private final BackupService backupService;

	public BackupController(BackupService backupService) {
		this.backupService = backupService;
	}

	@GetMapping("/api/v1/platform/backups")
	public BackupStatus status() {
		return backupService.status();
	}

	@PostMapping("/api/v1/platform/backups")
	public BackupSummary backUpNow() {
		return backupService.runBackup();
	}
}
