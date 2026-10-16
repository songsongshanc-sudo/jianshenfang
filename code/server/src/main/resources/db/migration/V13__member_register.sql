ALTER TABLE `user` ALTER COLUMN openid VARCHAR(64) NULL;
ALTER TABLE `user` ADD COLUMN created_store_id BIGINT NULL;
ALTER TABLE app_config ALTER COLUMN config_value VARCHAR(512) NOT NULL;
INSERT INTO app_config (config_key, config_value, updated_at) VALUES ('privacy.guide.url', '', CURRENT_TIMESTAMP(3));
