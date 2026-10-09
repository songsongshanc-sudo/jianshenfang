-- P1 / P2 表。不改 V1。

CREATE TABLE repair_ticket (
  id           BIGINT       NOT NULL,
  user_id      BIGINT       NOT NULL,
  store_id     BIGINT       NOT NULL,
  equipment_code VARCHAR(64) NULL,
  content      VARCHAR(500) NOT NULL,
  image_url    VARCHAR(512) NULL,
  status       VARCHAR(16)  NOT NULL,
  created_at   DATETIME(3)  NOT NULL,
  updated_at   DATETIME(3)  NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE complaint_ticket (
  id         BIGINT       NOT NULL,
  user_id    BIGINT       NOT NULL,
  store_id   BIGINT       NOT NULL,
  category   VARCHAR(16)  NOT NULL,
  content    VARCHAR(500) NOT NULL,
  image_url  VARCHAR(512) NULL,
  status     VARCHAR(16)  NOT NULL,
  created_at DATETIME(3)  NOT NULL,
  updated_at DATETIME(3)  NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE lost_item (
  id          BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  store_id    BIGINT       NOT NULL,
  kind        VARCHAR(16)  NOT NULL,
  name        VARCHAR(64)  NOT NULL,
  content     VARCHAR(500) NOT NULL,
  image_url   VARCHAR(512) NULL,
  visible     TINYINT      NOT NULL DEFAULT 1,
  resolved    TINYINT      NOT NULL DEFAULT 0,
  created_at  DATETIME(3)  NOT NULL,
  updated_at  DATETIME(3)  NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE equipment (
  id         BIGINT       NOT NULL,
  store_id   BIGINT       NOT NULL,
  code       VARCHAR(64)  NOT NULL,
  name       VARCHAR(64)  NOT NULL,
  intro      VARCHAR(1000) NULL,
  created_at DATETIME(3)  NOT NULL,
  updated_at DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (store_id, code)
);

CREATE TABLE equipment_media (
  id           BIGINT       NOT NULL,
  equipment_id BIGINT       NOT NULL,
  media_type   VARCHAR(16)  NOT NULL,
  url          VARCHAR(512) NOT NULL,
  sort_no      INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
);

CREATE TABLE groupon_rule (
  id              BIGINT       NOT NULL,
  store_id        BIGINT       NOT NULL,
  platform        VARCHAR(16)  NOT NULL,
  code_hash       VARCHAR(64)  NOT NULL,
  card_product_id BIGINT       NOT NULL,
  created_at      DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (code_hash)
);

CREATE TABLE groupon_redeem (
  id         BIGINT      NOT NULL,
  rule_id    BIGINT      NOT NULL,
  user_id    BIGINT      NOT NULL,
  store_id   BIGINT      NOT NULL,
  order_id   BIGINT      NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (rule_id)
);

CREATE TABLE coach (
  id         BIGINT       NOT NULL,
  store_id   BIGINT       NOT NULL,
  name       VARCHAR(64)  NOT NULL,
  phone      VARCHAR(20)  NOT NULL,
  intro      VARCHAR(1000) NULL,
  created_at DATETIME(3)  NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE course_pack (
  id            BIGINT       NOT NULL,
  coach_id      BIGINT       NOT NULL,
  store_id      BIGINT       NOT NULL,
  name          VARCHAR(64)  NOT NULL,
  price_fen     BIGINT       NOT NULL,
  lesson_count  INT          NOT NULL,
  content       VARCHAR(1000) NULL,
  audience      VARCHAR(500) NULL,
  status        VARCHAR(16)  NOT NULL,
  created_at    DATETIME(3)  NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE lesson_account (
  id         BIGINT      NOT NULL,
  user_id    BIGINT      NOT NULL,
  pack_id    BIGINT      NOT NULL,
  remaining  INT         NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (user_id, pack_id)
);

CREATE TABLE lesson_ledger (
  id         BIGINT      NOT NULL,
  user_id    BIGINT      NOT NULL,
  pack_id    BIGINT      NOT NULL,
  delta      INT         NOT NULL,
  reason     VARCHAR(32) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE franchise_page (
  id         BIGINT        NOT NULL,
  intro      VARCHAR(2000) NULL,
  hotline    VARCHAR(32)   NULL,
  points     VARCHAR(2000) NULL,
  support    VARCHAR(2000) NULL,
  steps      VARCHAR(2000) NULL,
  updated_at DATETIME(3)   NOT NULL,
  PRIMARY KEY (id)
);

INSERT INTO franchise_page (id, intro, hotline, points, support, steps, updated_at)
VALUES (1, '', '', '', '', '', CURRENT_TIMESTAMP(3));

CREATE TABLE franchise_lead (
  id         BIGINT       NOT NULL,
  name       VARCHAR(32)  NOT NULL,
  phone      VARCHAR(20)  NOT NULL,
  budget     VARCHAR(64)  NULL,
  province   VARCHAR(32)  NOT NULL,
  city       VARCHAR(32)  NOT NULL,
  created_at DATETIME(3)  NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE inbox_message (
  id         BIGINT       NOT NULL,
  user_id    BIGINT       NOT NULL,
  title      VARCHAR(64)  NOT NULL,
  content    VARCHAR(500) NOT NULL,
  read_at    DATETIME(3)  NULL,
  created_at DATETIME(3)  NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE membership_day_adjust (
  id            BIGINT      NOT NULL,
  membership_id BIGINT      NOT NULL,
  user_id       BIGINT      NOT NULL,
  delta_days    INT         NOT NULL,
  reason        VARCHAR(255) NOT NULL,
  admin_id      BIGINT      NOT NULL,
  created_at    DATETIME(3) NOT NULL,
  PRIMARY KEY (id)
);

CREATE TABLE student_cert (
  user_id    BIGINT      NOT NULL,
  status     VARCHAR(16) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (user_id)
);
