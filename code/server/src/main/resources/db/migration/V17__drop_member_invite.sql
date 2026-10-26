ALTER TABLE `user` DROP INDEX uk_user_invite_code;
ALTER TABLE `user` DROP COLUMN invite_code;
ALTER TABLE `user` DROP COLUMN invite_fail;
