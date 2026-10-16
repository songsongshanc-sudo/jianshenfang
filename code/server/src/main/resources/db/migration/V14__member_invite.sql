ALTER TABLE `user` ADD COLUMN invite_code VARCHAR(16) NULL;
ALTER TABLE `user` ADD COLUMN invite_fail INT NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX uk_user_invite_code ON `user` (invite_code);
