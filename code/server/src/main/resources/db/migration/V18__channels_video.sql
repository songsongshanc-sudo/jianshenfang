ALTER TABLE store ADD COLUMN channels_finder_id VARCHAR(64) NULL;
ALTER TABLE equipment ADD COLUMN feed_id VARCHAR(256) NULL;
INSERT INTO app_config (config_key, config_value, updated_at) VALUES ('channels.finder.id', '', CURRENT_TIMESTAMP(3));
