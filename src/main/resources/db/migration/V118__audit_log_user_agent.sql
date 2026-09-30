-- ip_address already exists (V1) but was never populated; user_agent is the device signal.
ALTER TABLE audit_logs ADD COLUMN user_agent VARCHAR(512);
