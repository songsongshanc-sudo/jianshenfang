CREATE TABLE visit_pass (
  id                BIGINT       NOT NULL,
  user_id           BIGINT       NOT NULL,
  order_id          BIGINT       NOT NULL,
  deposit_fen       BIGINT       NOT NULL,
  duration_minutes  INT          NOT NULL,
  warn_minutes      INT          NOT NULL,
  valid_until       DATETIME(3)  NOT NULL,
  status            VARCHAR(24)  NOT NULL,
  store_id          BIGINT       NULL,
  entered_at        DATETIME(3)  NULL,
  due_exit_at       DATETIME(3)  NULL,
  exited_at         DATETIME(3)  NULL,
  forfeited_at      DATETIME(3)  NULL,
  refunded_at       DATETIME(3)  NULL,
  refund_error      VARCHAR(255) NULL,
  created_at        DATETIME(3)  NOT NULL,
  updated_at        DATETIME(3)  NOT NULL,
  PRIMARY KEY (id)
);

CREATE INDEX idx_visit_pass_user ON visit_pass (user_id, status);
CREATE INDEX idx_visit_pass_status ON visit_pass (status, valid_until);
CREATE INDEX idx_visit_pass_due ON visit_pass (status, due_exit_at);

INSERT INTO app_config (config_key, config_value, updated_at) VALUES
  ('visit.enabled', '0', CURRENT_TIMESTAMP(3)),
  ('visit.deposit.fen', '990', CURRENT_TIMESTAMP(3)),
  ('visit.duration.minutes', '60', CURRENT_TIMESTAMP(3)),
  ('visit.valid.days', '30', CURRENT_TIMESTAMP(3)),
  ('visit.warn.minutes', '5', CURRENT_TIMESTAMP(3)),
  ('visit.notice', '', CURRENT_TIMESTAMP(3));
