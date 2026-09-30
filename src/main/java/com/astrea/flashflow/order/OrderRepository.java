package com.astrea.flashflow.order;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {
  private final JdbcTemplate db;

  public OrderRepository(JdbcTemplate db) {
    this.db = db;
  }

  private OrderView map(ResultSet r, int n) throws SQLException {
    return new OrderView(
        r.getString("order_no"),
        r.getString("reservation_id"),
        r.getLong("user_id"),
        r.getLong("event_id"),
        r.getLong("sku_id"),
        r.getBigDecimal("amount"),
        OrderStatus.valueOf(r.getString("status")),
        r.getTimestamp("expire_at").toInstant(),
        r.getTimestamp("created_at").toInstant());
  }

  public Optional<OrderView> byNumber(String no, boolean lock) {
    return db
        .query("SELECT * FROM orders WHERE order_no=?" + (lock ? " FOR UPDATE" : ""), this::map, no)
        .stream()
        .findFirst();
  }

  public Optional<OrderView> byReservation(String id) {
    return db.query("SELECT * FROM orders WHERE reservation_id=?", this::map, id).stream()
        .findFirst();
  }

  public List<OrderView> forUser(long user) {
    return db.query(
        "SELECT * FROM orders WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT 100",
        this::map,
        user);
  }
}
