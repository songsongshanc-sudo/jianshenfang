CREATE TABLE `user` (
  id               BIGINT       NOT NULL,
  openid           VARCHAR(64)  NOT NULL,
  unionid          VARCHAR(64)  NULL,
  phone            VARCHAR(20)  NULL,
  member_no        CHAR(9)      NULL,
  nickname         VARCHAR(64)  NOT NULL DEFAULT '微信用户',
  avatar_url       VARCHAR(512) NULL,
  register_status  VARCHAR(16)  NOT NULL DEFAULT 'NEED_PHONE',
  registered_at    DATETIME(3)  NULL,
  status           VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  created_at       DATETIME(3)  NOT NULL,
  updated_at       DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (openid),
  UNIQUE (phone),
  UNIQUE (member_no)
);

CREATE TABLE user_face (
  id              BIGINT       NOT NULL,
  user_id         BIGINT       NOT NULL,
  object_key      VARCHAR(512) NOT NULL,
  vendor_face_id  VARCHAR(64)  NULL,
  status          VARCHAR(16)  NOT NULL,
  fail_reason     VARCHAR(128) NULL,
  created_at      DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (vendor_face_id)
);

CREATE INDEX idx_user_face_user ON user_face (user_id);

CREATE TABLE member_no_seq (
  id      TINYINT NOT NULL,
  next_no BIGINT  NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE store (
  id             BIGINT        NOT NULL,
  code           VARCHAR(16)   NOT NULL,
  name           VARCHAR(64)   NOT NULL,
  province       VARCHAR(32)   NOT NULL,
  city           VARCHAR(32)   NOT NULL,
  address        VARCHAR(255)  NOT NULL,
  longitude      DECIMAL(10,6) NOT NULL,
  latitude       DECIMAL(10,6) NOT NULL,
  cover_url      VARCHAR(512)  NULL,
  business_hours VARCHAR(32)   NOT NULL DEFAULT '24h',
  status         VARCHAR(16)   NOT NULL DEFAULT 'OPEN',
  wifi_ssid      VARCHAR(64)   NULL,
  wifi_password  VARCHAR(64)   NULL,
  mch_id         VARCHAR(32)   NULL,
  created_at     DATETIME(3)   NOT NULL,
  updated_at     DATETIME(3)   NOT NULL,
  deleted        TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE (code)
);

CREATE TABLE store_phone (
  id         BIGINT      NOT NULL,
  store_id   BIGINT      NOT NULL,
  phone_type VARCHAR(16) NOT NULL,
  phone      VARCHAR(20) NOT NULL,
  time_start TIME        NULL,
  time_end   TIME        NULL,
  sort_no    INT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE INDEX idx_store_phone_store ON store_phone (store_id);

CREATE TABLE store_guide (
  id         BIGINT       NOT NULL,
  store_id   BIGINT       NOT NULL,
  image_url  VARCHAR(512) NOT NULL,
  caption    VARCHAR(255) NOT NULL,
  sort_no    INT          NOT NULL DEFAULT 0,
  created_at DATETIME(3)  NOT NULL,
  updated_at DATETIME(3)  NOT NULL,
  PRIMARY KEY (id)
);

CREATE INDEX idx_store_guide_store ON store_guide (store_id, sort_no);

CREATE TABLE banner (
  id         BIGINT       NOT NULL,
  title      VARCHAR(64)  NOT NULL,
  image_url  VARCHAR(512) NOT NULL,
  link_url   VARCHAR(512) NULL,
  sort_no    INT          NOT NULL DEFAULT 0,
  status     VARCHAR(16)  NOT NULL DEFAULT 'ON',
  created_at DATETIME(3)  NOT NULL,
  updated_at DATETIME(3)  NOT NULL,
  deleted    TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE notice (
  id         BIGINT       NOT NULL,
  content    VARCHAR(255) NOT NULL,
  status     VARCHAR(16)  NOT NULL DEFAULT 'ON',
  sort_no    INT          NOT NULL DEFAULT 0,
  created_at DATETIME(3)  NOT NULL,
  updated_at DATETIME(3)  NOT NULL,
  deleted    TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE agreement (
  id         BIGINT       NOT NULL,
  title      VARCHAR(64)  NOT NULL,
  version_no INT          NOT NULL,
  content    LONGTEXT     NOT NULL,
  status     VARCHAR(16)  NOT NULL,
  created_at DATETIME(3)  NOT NULL,
  updated_at DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (version_no)
);

CREATE TABLE card_product (
  id             BIGINT       NOT NULL,
  store_id       BIGINT       NOT NULL,
  name           VARCHAR(64)  NOT NULL,
  price_fen      BIGINT       NOT NULL,
  display_text   VARCHAR(64)  NULL,
  valid_days     INT          NOT NULL,
  duration_mode  VARCHAR(16)  NOT NULL DEFAULT 'HOURS_24',
  stock_total    INT          NULL,
  stock_sold     INT          NOT NULL DEFAULT 0,
  stock_reserved INT          NOT NULL DEFAULT 0,
  unlock_type    VARCHAR(24)  NOT NULL DEFAULT 'NONE',
  unlock_days    INT          NULL,
  cross_store    TINYINT      NOT NULL DEFAULT 0,
  home_visible   TINYINT      NOT NULL DEFAULT 0,
  sort_no        INT          NOT NULL DEFAULT 0,
  status         VARCHAR(16)  NOT NULL DEFAULT 'ON',
  created_at     DATETIME(3)  NOT NULL,
  updated_at     DATETIME(3)  NOT NULL,
  deleted        TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE INDEX idx_card_store_status ON card_product (store_id, status);

CREATE TABLE trade_order (
  id                BIGINT       NOT NULL,
  order_no          VARCHAR(32)  NOT NULL,
  user_id           BIGINT       NOT NULL,
  store_id          BIGINT       NOT NULL,
  biz_type          VARCHAR(16)  NOT NULL,
  product_id        BIGINT       NOT NULL,
  product_name      VARCHAR(64)  NOT NULL,
  amount_fen        BIGINT       NOT NULL,
  status            VARCHAR(16)  NOT NULL,
  agreement_id      BIGINT       NULL,
  agreement_version INT          NULL,
  idempotency_key   VARCHAR(64)  NULL,
  expire_at         DATETIME(3)  NOT NULL,
  paid_at           DATETIME(3)  NULL,
  created_at        DATETIME(3)  NOT NULL,
  updated_at        DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (order_no),
  UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_trade_order_user ON trade_order (user_id, created_at);

CREATE TABLE payment (
  id             BIGINT       NOT NULL,
  order_id       BIGINT       NOT NULL,
  mch_id         VARCHAR(32)  NOT NULL,
  prepay_id      VARCHAR(64)  NULL,
  transaction_id VARCHAR(64)  NULL,
  amount_fen     BIGINT       NOT NULL,
  status         VARCHAR(16)  NOT NULL,
  raw_notify     LONGTEXT     NULL,
  created_at     DATETIME(3)  NOT NULL,
  updated_at     DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (order_id),
  UNIQUE (transaction_id)
);

CREATE TABLE membership (
  id              BIGINT      NOT NULL,
  user_id         BIGINT      NOT NULL,
  store_id        BIGINT      NOT NULL,
  order_id        BIGINT      NOT NULL,
  card_product_id BIGINT      NOT NULL,
  source          VARCHAR(16) NOT NULL,
  start_at        DATETIME(3) NOT NULL,
  end_at          DATETIME(3) NOT NULL,
  status          VARCHAR(16) NOT NULL,
  cross_store     TINYINT     NOT NULL DEFAULT 0,
  created_at      DATETIME(3) NOT NULL,
  PRIMARY KEY (id)
);

CREATE INDEX idx_membership_user_store ON membership (user_id, store_id, status, end_at);

CREATE TABLE gate_device (
  id           BIGINT       NOT NULL,
  store_id     BIGINT       NOT NULL,
  device_sn    VARCHAR(64)  NOT NULL,
  secret       VARCHAR(128) NOT NULL,
  name         VARCHAR(64)  NOT NULL,
  status       VARCHAR(16)  NOT NULL DEFAULT 'ENABLED',
  last_beat_at DATETIME(3)  NULL,
  PRIMARY KEY (id),
  UNIQUE (device_sn)
);

CREATE TABLE door_log (
  id            BIGINT      NOT NULL,
  user_id       BIGINT      NOT NULL,
  store_id      BIGINT      NOT NULL,
  device_sn     VARCHAR(64) NOT NULL,
  membership_id BIGINT      NULL,
  channel       VARCHAR(16) NOT NULL DEFAULT 'FACE',
  result        VARCHAR(16) NOT NULL,
  created_at    DATETIME(3) NOT NULL,
  PRIMARY KEY (id)
);

CREATE INDEX idx_door_log_store ON door_log (store_id, created_at);
CREATE INDEX idx_door_log_user ON door_log (user_id, created_at);

CREATE TABLE admin_user (
  id            BIGINT       NOT NULL,
  username      VARCHAR(64)  NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  role          VARCHAR(16)  NOT NULL,
  store_id      BIGINT       NULL,
  status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  created_at    DATETIME(3)  NOT NULL,
  updated_at    DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (username),
  CHECK (
    (role = 'MASTER' AND store_id IS NULL) OR
    (role = 'STORE' AND store_id IS NOT NULL)
  )
);

CREATE TABLE admin_audit (
  id          BIGINT      NOT NULL,
  admin_id    BIGINT      NOT NULL,
  action      VARCHAR(64) NOT NULL,
  target_type VARCHAR(32) NULL,
  target_id   VARCHAR(64) NULL,
  created_at  DATETIME(3) NOT NULL,
  PRIMARY KEY (id)
);

CREATE INDEX idx_admin_audit_admin ON admin_audit (admin_id, created_at);

CREATE TABLE app_config (
  config_key   VARCHAR(64)  NOT NULL,
  config_value VARCHAR(255) NOT NULL,
  updated_at   DATETIME(3)  NOT NULL,
  PRIMARY KEY (config_key)
);

CREATE TABLE file_object (
  id           BIGINT       NOT NULL,
  owner_id     BIGINT       NULL,
  biz          VARCHAR(32)  NOT NULL,
  object_key   VARCHAR(512) NOT NULL,
  content_type VARCHAR(64)  NULL,
  created_at   DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (object_key)
);

INSERT INTO member_no_seq (id, next_no) VALUES (1, 100000001);

INSERT INTO app_config (config_key, config_value, updated_at) VALUES
  ('entry.debounce.seconds', '15', CURRENT_TIMESTAMP(3)),
  ('online.window.minutes', '90', CURRENT_TIMESTAMP(3)),
  ('order.expire.minutes', '15', CURRENT_TIMESTAMP(3)),
  ('refund.daily.deduct.fen', '', CURRENT_TIMESTAMP(3));

INSERT INTO agreement (id, title, version_no, content, status, created_at, updated_at)
VALUES (1, '会员协议', 1, '', 'DRAFT', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3));

-- 本地种子总账号 admin / admin123。上线后立即修改密码。
INSERT INTO admin_user (id, username, password_hash, role, store_id, status, created_at, updated_at)
VALUES (
  1,
  'admin',
  '$2b$10$EiIt.KaOkuGv1RB2bo6Vh.wg9O0zpnx/6ZEemp9qZU15iMwhIMF42',
  'MASTER',
  NULL,
  'ACTIVE',
  CURRENT_TIMESTAMP(3),
  CURRENT_TIMESTAMP(3)
);
