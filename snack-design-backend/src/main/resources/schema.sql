CREATE TABLE IF NOT EXISTS app_users (
  id VARCHAR(64) PRIMARY KEY,
  identity_key VARCHAR(128) NOT NULL UNIQUE,
  created_at BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS sessions (
  token_hash VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL REFERENCES app_users(id),
  expires_at BIGINT NOT NULL
);
CREATE TABLE IF NOT EXISTS ingredient_knowledge (
  name VARCHAR(150) PRIMARY KEY,
  group_id VARCHAR(24) NOT NULL,
  role VARCHAR(100) NOT NULL,
  benefit VARCHAR(2000) NOT NULL,
  risk VARCHAR(2000) NOT NULL,
  source VARCHAR(200) NOT NULL,
  url VARCHAR(1000) NOT NULL
);
CREATE TABLE IF NOT EXISTS ingredient_records (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL REFERENCES app_users(id),
  raw_text VARCHAR(5000) NOT NULL,
  kind VARCHAR(32) NOT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_records_user ON ingredient_records(user_id, updated_at);
CREATE TABLE IF NOT EXISTS research_reports (
  id VARCHAR(64) PRIMARY KEY,
  record_id VARCHAR(64) NOT NULL REFERENCES ingredient_records(id) ON DELETE CASCADE,
  user_id VARCHAR(64) NOT NULL REFERENCES app_users(id),
  raw_snapshot VARCHAR(5000) NOT NULL,
  food_category VARCHAR(200) NOT NULL,
  status VARCHAR(24) NOT NULL,
  model VARCHAR(200) NOT NULL,
  findings_json CLOB NOT NULL,
  error_message VARCHAR(1000) NOT NULL,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_reports_user_record ON research_reports(user_id, record_id, created_at);
