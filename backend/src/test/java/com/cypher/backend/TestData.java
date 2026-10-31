package com.cypher.backend;

import org.springframework.jdbc.core.JdbcTemplate;

final class TestData {
    static long camera(JdbcTemplate jdbc) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM cameras", Long.class) == 0) {
            jdbc.update("INSERT INTO projects (project_name, status) VALUES ('Test project', 'ACTIVE')");
            Long project = jdbc.queryForObject("SELECT MAX(project_id) FROM projects", Long.class);
            jdbc.update("""
                    INSERT INTO cameras (project_id, camera_name, camera_code, camera_status)
                    VALUES (?, 'Test camera', 'TEST-01', 'ONLINE')
                    """, project);
        }
        return jdbc.queryForObject("SELECT MIN(camera_id) FROM cameras", Long.class);
    }
}
