package com.example.qas.dto.request;

import com.example.qas.models.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {
    @NotNull
    private Long appointmentId;

    @NotBlank
    private String recipient;

    @NotNull
    private NotificationType type;

    private String subject;
    private String content;
}
