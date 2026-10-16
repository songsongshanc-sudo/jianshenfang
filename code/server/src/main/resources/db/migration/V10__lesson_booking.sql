ALTER TABLE course_pack ADD COLUMN room_cols INT NOT NULL DEFAULT 5;
ALTER TABLE course_pack ADD COLUMN room_rows INT NOT NULL DEFAULT 5;

CREATE TABLE coach_schedule (
  id         BIGINT      NOT NULL,
  coach_id   BIGINT      NOT NULL,
  start_hm   VARCHAR(5)  NOT NULL,
  sort_no    INT         NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (coach_id, start_hm)
);

CREATE INDEX idx_coach_schedule_coach ON coach_schedule (coach_id);

CREATE TABLE lesson_booking (
  id              BIGINT       NOT NULL,
  user_id         BIGINT       NOT NULL,
  pack_id         BIGINT       NOT NULL,
  coach_id        BIGINT       NOT NULL,
  store_id        BIGINT       NOT NULL,
  class_date      DATE         NOT NULL,
  start_hm        VARCHAR(5)   NOT NULL,
  start_at        DATETIME(3)  NOT NULL,
  end_at          DATETIME(3)  NOT NULL,
  seat_row        INT          NOT NULL,
  seat_col        INT          NOT NULL,
  seat_no         VARCHAR(16)  NOT NULL,
  status          VARCHAR(24)  NOT NULL,
  pending_until   DATETIME(3)  NULL,
  reject_reason   VARCHAR(500) NULL,
  cancel_reason   VARCHAR(255) NULL,
  confirmed_at    DATETIME(3)  NULL,
  cancelled_at    DATETIME(3)  NULL,
  refunded_at     DATETIME(3)  NULL,
  created_at      DATETIME(3)  NOT NULL,
  updated_at      DATETIME(3)  NOT NULL,
  PRIMARY KEY (id)
);

CREATE INDEX idx_lesson_booking_user ON lesson_booking (user_id, status);
CREATE INDEX idx_lesson_booking_coach ON lesson_booking (coach_id, status, start_at);
CREATE INDEX idx_lesson_booking_slot ON lesson_booking (pack_id, class_date, start_hm, status);
CREATE INDEX idx_lesson_booking_pending ON lesson_booking (status, pending_until);

CREATE TABLE lesson_appeal (
  id             BIGINT       NOT NULL,
  booking_id     BIGINT       NOT NULL,
  user_id        BIGINT       NOT NULL,
  reason         VARCHAR(500) NOT NULL,
  status         VARCHAR(24)  NOT NULL,
  reject_reason  VARCHAR(500) NULL,
  created_at     DATETIME(3)  NOT NULL,
  resolved_at    DATETIME(3)  NULL,
  PRIMARY KEY (id)
);

CREATE INDEX idx_lesson_appeal_booking ON lesson_appeal (booking_id, status);
CREATE INDEX idx_lesson_appeal_user ON lesson_appeal (user_id, status);

ALTER TABLE inbox_message ADD COLUMN biz_type VARCHAR(32) NULL;
ALTER TABLE inbox_message ADD COLUMN biz_id BIGINT NULL;
ALTER TABLE inbox_message ADD COLUMN store_id BIGINT NULL;
ALTER TABLE inbox_message ADD COLUMN coach_id BIGINT NULL;

CREATE INDEX idx_inbox_store_coach ON inbox_message (store_id, coach_id, created_at);
CREATE INDEX idx_inbox_biz ON inbox_message (biz_type, biz_id);

CREATE TABLE message_audit (
  id         BIGINT       NOT NULL,
  title      VARCHAR(64)  NOT NULL,
  content    VARCHAR(500) NOT NULL,
  biz_type   VARCHAR(32)  NULL,
  biz_id     BIGINT       NULL,
  store_id   BIGINT       NULL,
  coach_id   BIGINT       NULL,
  user_id    BIGINT       NULL,
  created_at DATETIME(3)  NOT NULL,
  PRIMARY KEY (id)
);

CREATE INDEX idx_message_audit_store ON message_audit (store_id, created_at);
CREATE INDEX idx_message_audit_coach ON message_audit (coach_id, created_at);
CREATE INDEX idx_message_audit_biz ON message_audit (biz_type, biz_id);
