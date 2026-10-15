package com.cypher.backend.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * One line of reasoning from the AI engine, streamed to the cockpit's live
 * trace. Deliberately not persisted: it is telemetry, not a record.
 */
public record TraceRequest(

        @NotNull(message = "cameraId is required")
        Long cameraId,

        @NotEmpty(message = "steps are required")
        List<String> steps,

        /** INFO | DECISION | BLOCKED — drives the colour in the cockpit. */
        String level,

        String model,

        Double threshold,

        Double inferenceMs
) {
}
