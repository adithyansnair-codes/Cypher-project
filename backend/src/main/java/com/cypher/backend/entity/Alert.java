package com.cypher.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * An alert raised for an incident. Maps the {@code alerts} table.
 *
 * <p>Rows are normally created by the {@code create_incident_with_alert}
 * stored procedure rather than by application code. The
 * {@code trg_close_alerts} trigger sets {@code alertStatus} to CLOSED when the
 * parent incident is resolved.
 */
@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alert_id")
    private Long alertId;

    @Column(name = "incident_id", nullable = false)
    private Long incidentId;

    @Column(name = "alert_type", nullable = false, length = 50)
    private String alertType;

    @Column(name = "alert_message", columnDefinition = "text")
    private String alertMessage;

    @Column(name = "alert_status", nullable = false, length = 20)
    private String alertStatus = "PENDING";

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    public Alert() {
    }

    public Long getAlertId() {
        return alertId;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public String getAlertType() {
        return alertType;
    }

    public String getAlertMessage() {
        return alertMessage;
    }

    public String getAlertStatus() {
        return alertStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
