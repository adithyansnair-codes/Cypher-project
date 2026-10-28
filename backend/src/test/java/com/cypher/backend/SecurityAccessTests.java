package com.cypher.backend;

import com.cypher.backend.entity.Incident;
import com.cypher.backend.repository.IncidentRepository;
import com.cypher.backend.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the authorization boundaries that previously disagreed with the
 * seeded permission matrix, and the media write path's safe S3-disabled mode.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityAccessTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private IncidentRepository incidents;

    @Autowired
    private JwtService jwt;

    @Test
    void securityManagerCanReadAuditTrail() throws Exception {
        mvc.perform(get("/api/audit").header("Authorization", bearer("SECURITY_MANAGER")))
                .andExpect(status().isOk());
    }

    @Test
    void viewerCannotReadAuditTrail() throws Exception {
        mvc.perform(get("/api/audit").header("Authorization", bearer("VIEWER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void viewerCannotDeleteIncidentMedia() throws Exception {
        Incident incident = firstIncident();
        mvc.perform(delete("/api/incidents/{id}/media/{mediaId}",
                        incident.getIncidentId(), 999_999)
                        .header("Authorization", bearer("VIEWER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void operatorCanReachMediaDeleteHandler() throws Exception {
        Incident incident = firstIncident();
        mvc.perform(delete("/api/incidents/{id}/media/{mediaId}",
                        incident.getIncidentId(), 999_999)
                        .header("Authorization", bearer("OPERATOR")))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadFailsSafelyWhenS3IsNotConfigured() throws Exception {
        Incident incident = firstIncident();
        MockMultipartFile file = new MockMultipartFile(
                "file", "evidence.jpg", MediaType.IMAGE_JPEG_VALUE,
                new byte[]{1, 2, 3, 4});

        mvc.perform(multipart("/api/incidents/{id}/media", incident.getIncidentId())
                        .file(file)
                        .header("Authorization", bearer("ADMIN")))
                .andExpect(status().isServiceUnavailable());
    }

    private Incident firstIncident() {
        return incidents.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Seed incident is missing"));
    }

    private String bearer(String role) {
        return "Bearer " + jwt.generateToken(role.toLowerCase() + "@test.cypher", role);
    }
}
