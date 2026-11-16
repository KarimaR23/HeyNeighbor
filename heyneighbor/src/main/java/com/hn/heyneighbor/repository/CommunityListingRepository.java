package com.hn.heyneighbor.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class CommunityListingRepository {
    private final JdbcTemplate jdbc;

    public CommunityListingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> categories() {
        return jdbc.queryForList("SELECT category_id, name FROM category WHERE is_active = true ORDER BY display_order, name");
    }

    public boolean hasLocation(long userId) {
        Boolean found = jdbc.queryForObject("SELECT latitude IS NOT NULL AND longitude IS NOT NULL FROM user_account WHERE user_id = ?", Boolean.class, userId);
        return Boolean.TRUE.equals(found);
    }

    /** Only active listings from active owners and approved organizations are public. */
    public List<Map<String, Object>> browse(long userId, String query, Integer categoryId, Integer radiusMiles) {
        StringBuilder sql = new StringBuilder("""
            SELECT l.listing_id, l.title, l.description, l.listing_type, l.offering_kind,
                   l.city, l.postal_code, l.quantity_available, l.availability_notes,
                   l.listing_images, c.name AS category_name,
                   COALESCE(o.org_name, owner.display_name) AS posted_by,
                   fm.method_name AS fulfillment_method,
                   CASE WHEN me.latitude IS NOT NULL AND me.longitude IS NOT NULL THEN
                       3958.7613 * 2 * asin(sqrt(least(1.0, greatest(0.0,
                           power(sin(radians((l.latitude - me.latitude)::double precision) / 2), 2)
                           + cos(radians(me.latitude::double precision)) * cos(radians(l.latitude::double precision))
                           * power(sin(radians((l.longitude - me.longitude)::double precision) / 2), 2)
                       ))))
                   ELSE NULL END AS distance_miles
            FROM listing l
            JOIN user_account owner ON owner.user_id = l.owner_user_id
            JOIN category c ON c.category_id = l.category_id
            JOIN fulfillment_method fm ON fm.method_id = l.fulfillment_method_id
            LEFT JOIN organization o ON o.organization_id = l.organization_id
            JOIN user_account me ON me.user_id = ?
            WHERE l.listing_status = 'active'
              AND owner.account_status = 'active'
              AND (l.organization_id IS NULL OR o.approval_status = 'approved')
              AND (l.expires_at IS NULL OR l.expires_at > CURRENT_TIMESTAMP)
            """);
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (query != null && !query.isBlank()) {
            sql.append(" AND (l.title ILIKE ? ESCAPE '\\' OR l.description ILIKE ? ESCAPE '\\')");
            args.add(Like.contains(query));
            args.add(Like.contains(query));
        }
        if (categoryId != null) {
            sql.append(" AND l.category_id = ?");
            args.add(categoryId);
        }
        if (radiusMiles != null) {
            sql.append(" AND me.latitude IS NOT NULL AND me.longitude IS NOT NULL");
            sql.append(" AND (3958.7613 * 2 * asin(sqrt(least(1.0, greatest(0.0,");
            sql.append(" power(sin(radians((l.latitude - me.latitude)::double precision) / 2), 2)");
            sql.append(" + cos(radians(me.latitude::double precision)) * cos(radians(l.latitude::double precision))");
            sql.append(" * power(sin(radians((l.longitude - me.longitude)::double precision) / 2), 2)" );
            sql.append(" ))))) <= ?");
            args.add(radiusMiles);
        }
        sql.append(" ORDER BY l.created_at DESC LIMIT 100");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }
}
