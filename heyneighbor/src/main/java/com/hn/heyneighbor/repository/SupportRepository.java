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

    public List<Map<String, Object>> searchOpenSupportRequests() {

        return jdbc.queryForList("""
                SELECT
                    support_request_id,
                    subject,
                    request_type,
                    status,
                    priority,
                    created_at
                FROM support_request
                WHERE status = 'open'
                ORDER BY created_at DESC
                LIMIT 200
                """);
    }
}
