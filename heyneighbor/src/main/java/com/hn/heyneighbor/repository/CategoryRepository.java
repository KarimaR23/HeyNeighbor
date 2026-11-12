package com.hn.heyneighbor.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class CategoryRepository {

    private final JdbcTemplate jdbc;

    public CategoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SELECT = """
            SELECT c.category_id, c.parent_category_id, p.name AS parent_name,
                   c.name, c.description, c.display_order, c.is_active,
                   (SELECT COUNT(*) FROM listing l
                     WHERE l.category_id = c.category_id AND l.listing_status = 'active') AS active_listings
            FROM category c
            LEFT JOIN category p ON p.category_id = c.parent_category_id
            """;

    public List<Map<String, Object>> list(String show) {
        String where = switch (show) {
            case "active" -> " WHERE c.is_active = true ";
            case "inactive" -> " WHERE c.is_active = false ";
            default -> " ";
        };
        return jdbc.queryForList(SELECT + where + " ORDER BY c.is_active DESC, c.display_order, lower(c.name)");
    }

    public Optional<Map<String, Object>> find(int id) {
        List<Map<String, Object>> rows = jdbc.queryForList(SELECT + " WHERE c.category_id = ?", id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Map<String, Object> counts() {
        return jdbc.queryForMap("""
                SELECT COUNT(*) FILTER (WHERE is_active)     AS active,
                       COUNT(*) FILTER (WHERE NOT is_active) AS inactive
                FROM category
                """);
    }

    public boolean nameTaken(String name, Integer exceptId) {
        if (exceptId == null) {
            return Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM category WHERE lower(name) = lower(?))", Boolean.class, name));
        }
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM category WHERE lower(name) = lower(?) AND category_id <> ?)",
                Boolean.class, name, exceptId));
    }

    public boolean wouldCreateLoop(int id, int parentId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                WITH RECURSIVE up AS (
                    SELECT category_id, parent_category_id FROM category WHERE category_id = ?
                    UNION
                    SELECT c.category_id, c.parent_category_id
                    FROM category c JOIN up ON c.category_id = up.parent_category_id
                )
                SELECT EXISTS (SELECT 1 FROM up WHERE category_id = ?)
                """, Boolean.class, parentId, id));
    }

    public int create(String name, String description, Integer parentId, boolean active) {
        Integer id = jdbc.queryForObject("""
                INSERT INTO category (name, description, parent_category_id, display_order, is_active)
                VALUES (?, ?, ?, COALESCE((SELECT MAX(display_order) FROM category), 0) + 1, ?)
                RETURNING category_id
                """, Integer.class, name, description, parentId, active);
        return id == null ? 0 : id;
    }

    public void update(int id, String name, String description, Integer parentId, boolean active) {
        jdbc.update("""
                UPDATE category
                SET name = ?, description = ?, parent_category_id = ?, is_active = ?
                WHERE category_id = ?
                """, name, description, parentId, active, id);
    }

    public void setActive(int id, boolean active) {
        jdbc.update("UPDATE category SET is_active = ? WHERE category_id = ?", active, id);
    }
}