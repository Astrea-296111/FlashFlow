CREATE TABLE app_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL, role VARCHAR(16) NOT NULL DEFAULT 'USER',
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;
CREATE TABLE event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(128) NOT NULL, venue VARCHAR(128) NOT NULL,
  sale_start_at TIMESTAMP(3) NOT NULL, sale_end_at TIMESTAMP(3) NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  INDEX idx_event_status_sale (status,sale_start_at,id), CHECK (sale_end_at>sale_start_at)
) ENGINE=InnoDB;
CREATE TABLE ticket_sku (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, event_id BIGINT NOT NULL, tier_name VARCHAR(64) NOT NULL,
  price DECIMAL(12,2) NOT NULL, total_stock INT NOT NULL, available_stock INT NOT NULL,
  version BIGINT NOT NULL DEFAULT 0, status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', mode VARCHAR(16) NOT NULL DEFAULT 'BASELINE',
  INDEX idx_sku_event(event_id,id), FOREIGN KEY(event_id) REFERENCES event(id),
  CHECK (price>=0), CHECK (total_stock>=0), CHECK (available_stock>=0 AND available_stock<=total_stock)
) ENGINE=InnoDB;
CREATE TABLE seckill_reservation (
  reservation_id VARCHAR(64) PRIMARY KEY, user_id BIGINT NOT NULL, event_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL, origin VARCHAR(16) NOT NULL, expire_at TIMESTAMP(3) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL, updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  INDEX idx_reservation_deadline(status,expire_at), INDEX idx_reservation_user(user_id,created_at),
  FOREIGN KEY(user_id) REFERENCES app_user(id), FOREIGN KEY(sku_id) REFERENCES ticket_sku(id)
) ENGINE=InnoDB;
CREATE TABLE orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, order_no CHAR(36) NOT NULL UNIQUE, reservation_id VARCHAR(64) NOT NULL UNIQUE,
  user_id BIGINT NOT NULL, event_id BIGINT NOT NULL, sku_id BIGINT NOT NULL, amount DECIMAL(12,2) NOT NULL,
  status VARCHAR(16) NOT NULL, expire_at TIMESTAMP(3) NOT NULL, created_at TIMESTAMP(3) NOT NULL,
  paid_at TIMESTAMP(3) NULL, closed_at TIMESTAMP(3) NULL,
  UNIQUE KEY uk_order_buyer(user_id,sku_id), INDEX idx_order_user(user_id,created_at,id), INDEX idx_order_expire(status,expire_at,id),
  FOREIGN KEY(reservation_id) REFERENCES seckill_reservation(reservation_id), FOREIGN KEY(sku_id) REFERENCES ticket_sku(id)
) ENGINE=InnoDB;
CREATE TABLE payment_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, payment_key VARCHAR(64) NOT NULL UNIQUE, order_no CHAR(36) NOT NULL,
  amount DECIMAL(12,2) NOT NULL, status VARCHAR(16) NOT NULL, created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_payment_order(order_no), FOREIGN KEY(order_no) REFERENCES orders(order_no)
) ENGINE=InnoDB;
CREATE TABLE message_outbox (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, business_key CHAR(36) NOT NULL UNIQUE, kind VARCHAR(32) NOT NULL,
  payload TEXT NOT NULL, delay_level INT NOT NULL DEFAULT 0, status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  attempts INT NOT NULL DEFAULT 0, created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX idx_outbox_status(status,id)
) ENGINE=InnoDB;
CREATE TABLE stock_release_outbox (
  reservation_id VARCHAR(64) PRIMARY KEY, sku_id BIGINT NOT NULL, status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), INDEX idx_release_status(status,created_at)
) ENGINE=InnoDB;
CREATE TABLE message_quarantine (
  message_hash CHAR(64) PRIMARY KEY, topic VARCHAR(128) NOT NULL, reason VARCHAR(255) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB;
