package com.operion.common.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Bound to {@code app.retention.*}. Periods are in days; {@code batchSize} is how many rows one delete
 * statement removes (ids are selected a page at a time, then deleted by id - portable across MySQL and H2).
 * audit_logs is deliberately not here: it is never purged.
 */
@Component
@ConfigurationProperties(prefix = "app.retention")
public class RetentionProperties {

	private boolean enabled = true;
	private String cron = "0 30 3 * * *";
	private int loginAttemptsDays = 30;
	private int emailOutboxDays = 180;
	private int notificationRecipientsDays = 365;
	private int batchSize = 1000;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getCron() {
		return cron;
	}

	public void setCron(String cron) {
		this.cron = cron;
	}

	public int getLoginAttemptsDays() {
		return loginAttemptsDays;
	}

	public void setLoginAttemptsDays(int loginAttemptsDays) {
		this.loginAttemptsDays = loginAttemptsDays;
	}

	public int getEmailOutboxDays() {
		return emailOutboxDays;
	}

	public void setEmailOutboxDays(int emailOutboxDays) {
		this.emailOutboxDays = emailOutboxDays;
	}

	public int getNotificationRecipientsDays() {
		return notificationRecipientsDays;
	}

	public void setNotificationRecipientsDays(int notificationRecipientsDays) {
		this.notificationRecipientsDays = notificationRecipientsDays;
	}

	public int getBatchSize() {
		return batchSize;
	}

	public void setBatchSize(int batchSize) {
		this.batchSize = batchSize;
	}
}
