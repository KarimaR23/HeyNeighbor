package com.hn.heyneighbor.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Repository
public class CommunityListingRepository {
    private final JdbcTemplate jdbc;

    /** Miles between the signed-in user ("me") and the listing ("l"), or NULL when either has no location. */
    private static final String DISTANCE = """
            CASE WHEN me.latitude IS NOT NULL AND me.longitude IS NOT NULL THEN
                3958.7613 * 2 * asin(sqrt(least(1.0, greatest(0.0,
                    power(sin(radians((l.latitude - me.latitude)::double precision) / 2), 2)
                    + cos(radians(me.latitude::double precision)) * cos(radians(l.latitude::double precision))
                    * power(sin(radians((l.longitude - me.longitude)::double precision) / 2), 2)
                ))))
            ELSE NULL END""";

    /** Columns every listing card needs. */
    private static final String CARD_COLUMNS = """
            SELECT l.listing_id, l.title, l.description, l.listing_type, l.offering_kind, l.trade_wanted,
                   l.city, l.postal_code, l.quantity_available, l.availability_notes,
                   l.listing_images, c.name AS category_name,
                   COALESCE(o.org_name, owner.display_name) AS posted_by,
                   -- organizations have no picture of their own, so show their initial instead of the manager's photo
                   CASE WHEN l.organization_id IS NULL THEN owner.avatar_image END AS poster_avatar,
                   fm.method_name AS fulfillment_method,
            """ + DISTANCE + " AS distance_miles\n";

    /** Joins and the rule for which listings are public: active listings from active owners and approved organizations. */
    private static final String VISIBLE = """
            JOIN user_account owner ON owner.user_id = l.owner_user_id
            JOIN category c ON c.category_id = l.category_id
            JOIN fulfillment_method fm ON fm.method_id = l.fulfillment_method_id
            LEFT JOIN organization o ON o.organization_id = l.organization_id
            JOIN user_account me ON me.user_id = ?
            WHERE l.listing_status = 'active'
              AND owner.account_status = 'active'
              AND (l.organization_id IS NULL OR o.approval_status = 'approved')
              AND (l.expires_at IS NULL OR l.expires_at > CURRENT_TIMESTAMP)
            """;

    public CommunityListingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String, Object>> categories() {
        return jdbc.queryForList("SELECT category_id, name FROM category WHERE is_active = true ORDER BY display_order, name");
    }


    /** Available pickup and delivery options for listing forms. */
    public List<Map<String, Object>> fulfillmentMethods() {
        return jdbc.queryForList("""
                SELECT method_id, method_name
                FROM fulfillment_method
                WHERE is_active = true
                ORDER BY method_id
                """);
    }


    /** Create a listing using the signed-in user's saved location. */
    public int createListing(long userId, int categoryId,
                             int fulfillmentMethodId, String title,
                             String description, String listingType,
                             String offeringKind, String tradeWanted,
                             Integer quantityAvailable,
                             String pickupDropoffNotes) {

        Integer listingId = jdbc.queryForObject("""
                INSERT INTO listing (
                    owner_user_id,
                    category_id,
                    fulfillment_method_id,
                    title,
                    description,
                    listing_type,
                    offering_kind,
                    trade_wanted,
                    quantity_available,
                    pickup_dropoff_notes,
                    city,
                    postal_code,
                    latitude,
                    longitude,
                    listing_status,
                    published_at
                )
                SELECT
                    u.user_id,
                    ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    u.city,
                    u.postal_code,
                    u.latitude,
                    u.longitude,
                    'active',
                    CURRENT_TIMESTAMP
                FROM user_account u
                WHERE u.user_id = ?
                  AND u.account_status = 'active'
                  AND u.postal_code IS NOT NULL
                  AND u.latitude IS NOT NULL
                  AND u.longitude IS NOT NULL
                RETURNING listing_id
                """,
                Integer.class,
                categoryId,
                fulfillmentMethodId,
                title,
                description,
                listingType,
                offeringKind,
                tradeWanted,
                quantityAvailable,
                pickupDropoffNotes,
                userId
        );

        return Objects.requireNonNull(listingId);
    }

    /** Attach an uploaded picture to a listing owned by this user. */
    public int updateListingImage(long userId, int listingId, String filename) {
        return jdbc.update("""
            UPDATE listing
            SET listing_images = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE listing_id = ?
              AND owner_user_id = ?
              AND organization_id IS NULL
            """, filename, listingId, userId);
    }


    public boolean hasLocation(long userId) {
        Boolean found = jdbc.queryForObject("""
        SELECT postal_code IS NOT NULL
           AND latitude IS NOT NULL
           AND longitude IS NOT NULL
        FROM user_account
        WHERE user_id = ?
        """, Boolean.class, userId);

        return Boolean.TRUE.equals(found);
    }

    /** Browse page: search, category and distance filters, newest first. */
    public List<Map<String, Object>> browse(long userId, String query, Integer categoryId, Integer radiusMiles) {
        return find(userId, query, categoryId, radiusMiles, null, false, 100);
    }

    /** Home page "Explore" row: newest public listings from other neighbors. */
    public List<Map<String, Object>> newest(long userId, int limit) {
        return find(userId, null, null, null, null, true, limit);
    }

    /** Home page trades row: listings where the owner wants something in return. */
    public List<Map<String, Object>> trades(long userId, int limit) {
        return find(userId, null, null, null, "trade", true, limit);
    }

    /** Home page "Still interested?" row: listings this user opened recently that are still available. */
    public List<Map<String, Object>> recentlyViewed(long userId, int limit) {
        return jdbc.queryForList(CARD_COLUMNS + """
                FROM recent_view rv
                JOIN listing l ON l.listing_id = rv.listing_id
                """ + VISIBLE + """
                  AND rv.user_id = me.user_id
                ORDER BY rv.last_viewed_at DESC
                LIMIT ?
                """, userId, limit);
    }

    private List<Map<String, Object>> find(long userId, String query, Integer categoryId, Integer radiusMiles,
                                           String listingType, boolean othersOnly, int limit) {
        StringBuilder sql = new StringBuilder(CARD_COLUMNS).append("FROM listing l\n").append(VISIBLE);
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (othersOnly) {
            // home page rows show other neighbors' listings, not the user's own
            sql.append(" AND l.owner_user_id <> me.user_id");
        }
        if (query != null && !query.isBlank()) {
            sql.append(" AND (l.title ILIKE ? ESCAPE '\\' OR l.description ILIKE ? ESCAPE '\\')");
            args.add(Like.contains(query));
            args.add(Like.contains(query));
        }
        if (categoryId != null) {
            sql.append(" AND l.category_id = ?");
            args.add(categoryId);
        }
        if (listingType != null) {
            sql.append(" AND l.listing_type = ?");
            args.add(listingType);
        }
        if (radiusMiles != null) {
            sql.append(" AND me.latitude IS NOT NULL AND me.longitude IS NOT NULL AND (")
               .append(DISTANCE).append(") <= ?");
            args.add(radiusMiles);
        }
        sql.append(" ORDER BY l.created_at DESC LIMIT ?");
        args.add(limit);
        return jdbc.queryForList(sql.toString(), args.toArray());
    }
}
