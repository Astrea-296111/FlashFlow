package com.astrea.flashflow.inventory;

import com.astrea.flashflow.common.BusinessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryRepository {
  private final JdbcTemplate db;

  public InventoryRepository(JdbcTemplate db) {
    this.db = db;
  }

  private TicketSku map(ResultSet r, int n) throws SQLException {
    return new TicketSku(
        r.getLong("id"),
        r.getLong("event_id"),
        r.getString("tier_name"),
        r.getBigDecimal("price"),
        r.getInt("total_stock"),
        r.getInt("available_stock"),
        r.getString("status"),
        r.getString("mode"),
        r.getTimestamp("sale_start_at").toInstant(),
        r.getTimestamp("sale_end_at").toInstant(),
        r.getString("event_status"));
  }

  public TicketSku get(long id, boolean lock) {
    var rows =
        db.query(
            "SELECT s.*,e.sale_start_at,e.sale_end_at,e.status AS event_status FROM ticket_sku s JOIN event e ON e.id=s.event_id WHERE s.id=?"
                + (lock ? " FOR UPDATE" : ""),
            this::map,
            id);
    if (rows.isEmpty()) throw new BusinessException("SKU_NOT_FOUND", 404);
    return rows.getFirst();
  }

  public int deduct(long id, String mode) {
    return db.update(
        "UPDATE ticket_sku SET available_stock=available_stock-1,version=version+1 WHERE id=? AND available_stock>0 AND status='ACTIVE' AND mode=?",
        id,
        mode);
  }

  public void release(long id) {
    if (db.update(
            "UPDATE ticket_sku SET available_stock=available_stock+1,version=version+1 WHERE id=? AND available_stock<total_stock",
            id)
        != 1) throw new IllegalStateException("Inventory release invariant failed");
  }

  public List<Long> asyncSkus() {
    return db.queryForList(
        "SELECT id FROM ticket_sku WHERE mode='ASYNC' ORDER BY id LIMIT 1000", Long.class);
  }
}
