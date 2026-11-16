package com.hn.heyneighbor.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class SupportRepository {

    private final JdbcTemplate jdbc;

    public SupportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> searchSupportRequests(
            String search,
            String status) {

        if (search == null || search.isBlank()) {

            return jdbc.queryForList(
                    """
                    SELECT
                        support_request_id,
                        subject,
                        request_type,
                        status,
                        priority,
                        created_at
                    FROM support_request
                    WHERE status = ?
                    ORDER BY created_at DESC
                    LIMIT 200
                    """,
                    status
            );
        }

        return jdbc.queryForList(
                """
                SELECT
                    support_request_id,
                    subject,
                    request_type,
                    status,
                    priority,
                    created_at
                FROM support_request
                WHERE status = ?
                AND (
                    LOWER(subject) LIKE LOWER(?)
                    OR LOWER(request_type) LIKE LOWER(?)
                )
                ORDER BY created_at DESC
                LIMIT 200
                """,
                status,
                "%" + search + "%",
                "%" + search + "%"
        );
    }

    public void updateStatus(
            int supportRequestId,
            String status) {

        jdbc.update(
                """
                UPDATE support_request
                SET status = ?
                WHERE support_request_id = ?
                """,
                status,
                supportRequestId
        );
    }
}