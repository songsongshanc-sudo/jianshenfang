-- 厂商闸机：设备签名与固件、进店记录允许陌生人、人脸下发任务。

ALTER TABLE gate_device ADD COLUMN token VARCHAR(128) NULL;
ALTER TABLE gate_device ADD COLUMN firmware VARCHAR(64) NULL;

ALTER TABLE door_log MODIFY COLUMN user_id BIGINT NULL;
ALTER TABLE door_log ADD COLUMN recog_time DATETIME(3) NULL;
ALTER TABLE door_log ADD COLUMN pass_status INT NULL;
ALTER TABLE door_log ADD COLUMN member_key VARCHAR(64) NULL;

CREATE INDEX idx_door_log_recog ON door_log (device_sn, recog_time, member_key);

CREATE TABLE gate_sync_task (
  id         BIGINT       NOT NULL,
  device_id  BIGINT       NOT NULL,
  user_id    BIGINT       NOT NULL,
  cmd        VARCHAR(16)  NOT NULL,
  status     VARCHAR(16)  NOT NULL,
  attempts   INT          NOT NULL DEFAULT 0,
  last_error VARCHAR(255) NULL,
  created_at DATETIME(3)  NOT NULL,
  updated_at DATETIME(3)  NOT NULL,
  PRIMARY KEY (id)
);

CREATE INDEX idx_gate_sync_device ON gate_sync_task (device_id, status);
CREATE INDEX idx_gate_sync_user ON gate_sync_task (user_id, device_id);
