package com.cypher.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectRequest(

        @NotBlank(message = "projectName is required")
        @Size(max = 150, message = "projectName must be at most 150 characters")
        String projectName,

        String description,

        @Size(max = 255, message = "location must be at most 255 characters")
        String location
) {
}
