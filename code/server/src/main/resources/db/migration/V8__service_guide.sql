CREATE TABLE service_guide (
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
