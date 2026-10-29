ALTER TABLE coach ADD COLUMN gate_until DATETIME(3) NULL;

CREATE TABLE door_staff (
  id         BIGINT       NOT NULL,
  store_id   BIGINT       NOT NULL,
  user_id    BIGINT       NOT NULL,
  name       VARCHAR(64)  NOT NULL,
  phone      VARCHAR(20)  NOT NULL,
  gate_until DATETIME(3)  NOT NULL,
  created_at DATETIME(3)  NOT NULL,
  updated_at DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE (phone),
  UNIQUE (user_id)
);
