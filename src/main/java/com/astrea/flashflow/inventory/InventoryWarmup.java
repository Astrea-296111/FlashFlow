package com.astrea.flashflow.inventory;

import com.astrea.flashflow.common.BusinessException;
import com.astrea.flashflow.seckill.RedisReservations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryWarmup {
  private final InventoryRepository inventory;
  private final RedisReservations redis;
  private final JdbcTemplate db;

  public InventoryWarmup(InventoryRepository inventory, RedisReservations redis, JdbcTemplate db) {
    this.inventory = inventory;
    this.redis = redis;
    this.db = db;
  }

  @Transactional
  public void warm(long id) {
    var sku = inventory.get(id, true);
    if ("ASYNC".equals(sku.mode())) {
      if (redis.getTemplateStockMissing(id))
        throw new BusinessException("LIVE_REWARM_FORBIDDEN", 409);
      return;
    }
    if (sku.availableStock() != sku.totalStock()
        || db.queryForObject("SELECT COUNT(*) FROM orders WHERE sku_id=?", Integer.class, id) > 0)
      throw new BusinessException("LIVE_REWARM_FORBIDDEN", 409);
    db.update("UPDATE ticket_sku SET mode='ASYNC' WHERE id=?", id);
    redis.initialize(sku);
  }
}
