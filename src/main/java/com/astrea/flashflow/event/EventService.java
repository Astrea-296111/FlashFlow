package com.astrea.flashflow.event;

import com.astrea.flashflow.common.BusinessException;
import com.astrea.flashflow.inventory.InventoryRepository;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {
  public record EventInput(
      @NotBlank @Size(max = 128) String name,
      @NotBlank @Size(max = 128) String venue,
      @NotNull Instant saleStartAt,
      @NotNull Instant saleEndAt) {}

  public record SkuInput(
      @NotBlank @Size(max = 64) String tierName,
      @NotNull @DecimalMin("0.00") @DecimalMax("9999999999.99") @Digits(integer = 10, fraction = 2)
          BigDecimal price,
      @Min(0) @Max(1000000) int stock) {}

  private final JdbcTemplate db;
  private final InventoryRepository inventory;

  public EventService(JdbcTemplate db, InventoryRepository inventory) {
    this.db = db;
    this.inventory = inventory;
  }

  public List<Map<String, Object>> list() {
    return db.queryForList(
        "SELECT * FROM event WHERE status='ACTIVE' ORDER BY sale_start_at,id LIMIT 100");
  }

  public Map<String, Object> detail(long id) {
    var rows = db.queryForList("SELECT * FROM event WHERE id=?", id);
    if (rows.isEmpty()) throw new BusinessException("EVENT_NOT_FOUND", 404);
    return Map.of(
        "event",
        rows.getFirst(),
        "skus",
        db.queryForList(
            "SELECT id,event_id,tier_name,price,total_stock,status,mode FROM ticket_sku WHERE event_id=? ORDER BY id",
            id));
  }

  private void validate(EventInput e) {
    if (!e.saleEndAt().isAfter(e.saleStartAt()))
      throw new BusinessException("INVALID_SALE_WINDOW", 400);
  }

  @Transactional
  public long create(EventInput e) {
    validate(e);
    var key = new GeneratedKeyHolder();
    db.update(
        c -> {
          var p =
              c.prepareStatement(
                  "INSERT INTO event(name,venue,sale_start_at,sale_end_at) VALUES(?,?,?,?)",
                  new String[] {"id"});
          p.setString(1, e.name());
          p.setString(2, e.venue());
          p.setTimestamp(3, Timestamp.from(e.saleStartAt()));
          p.setTimestamp(4, Timestamp.from(e.saleEndAt()));
          return p;
        },
        key);
    return key.getKey().longValue();
  }

  @Transactional
  public void update(long id, EventInput e) {
    validate(e);
    freezeGuard(id);
    if (db.update(
            "UPDATE event SET name=?,venue=?,sale_start_at=?,sale_end_at=? WHERE id=?",
            e.name(),
            e.venue(),
            Timestamp.from(e.saleStartAt()),
            Timestamp.from(e.saleEndAt()),
            id)
        != 1) throw new BusinessException("EVENT_NOT_FOUND", 404);
  }

  @Transactional
  public void archive(long id) {
    freezeGuard(id);
    if (db.update("UPDATE event SET status='ARCHIVED' WHERE id=?", id) != 1)
      throw new BusinessException("EVENT_NOT_FOUND", 404);
  }

  private void freezeGuard(long id) {
    var row = db.queryForList("SELECT id FROM event WHERE id=? FOR UPDATE", id);
    if (row.isEmpty()) throw new BusinessException("EVENT_NOT_FOUND", 404);
    if (db.queryForObject(
            "SELECT COUNT(*) FROM ticket_sku WHERE event_id=? AND (mode='ASYNC' OR available_stock<>total_stock)",
            Integer.class,
            id)
        > 0) throw new BusinessException("SALE_FROZEN", 409);
  }

  @Transactional
  public long addSku(long eventId, SkuInput s) {
    if (db.queryForList("SELECT id FROM event WHERE id=? AND status='ACTIVE' FOR UPDATE", eventId)
        .isEmpty()) throw new BusinessException("EVENT_NOT_FOUND", 404);
    var key = new GeneratedKeyHolder();
    db.update(
        c -> {
          var p =
              c.prepareStatement(
                  "INSERT INTO ticket_sku(event_id,tier_name,price,total_stock,available_stock) VALUES(?,?,?,?,?)",
                  new String[] {"id"});
          p.setLong(1, eventId);
          p.setString(2, s.tierName());
          p.setBigDecimal(3, s.price());
          p.setInt(4, s.stock());
          p.setInt(5, s.stock());
          return p;
        },
        key);
    return key.getKey().longValue();
  }
}
