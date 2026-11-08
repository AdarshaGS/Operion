-- Auth hardening (SEC): credential version, SHA-256 token lookup.

-- Bumped on every password change and stamped into access JWTs ("cv" claim); a token whose version no
-- longer matches the user's is rejected, so a password change/reset cuts off access tokens already issued.
ALTER TABLE `users` ADD COLUMN `credential_version` bigint NOT NULL DEFAULT 0;

-- Reset/verification tokens are now stored as a SHA-256 hex digest and looked up by it (equality), instead
-- of bcrypt-comparing every unconsumed row. Tokens issued before this migration hold a bcrypt hash and can
-- never match, so they are marked consumed here rather than left dangling: any outstanding reset or
-- verification link simply stops working and the user requests a new one.
UPDATE `password_reset_tokens` SET `consumed` = 1 WHERE `consumed` = 0;
UPDATE `email_verification_tokens` SET `consumed` = 1 WHERE `consumed` = 0;

CREATE INDEX `idx_password_reset_tokens_org_hash` ON `password_reset_tokens` (`organisation_id`, `token_hash`);
CREATE INDEX `idx_email_verification_tokens_org_hash` ON `email_verification_tokens` (`organisation_id`, `token_hash`);
