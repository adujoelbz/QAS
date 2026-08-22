package com.example.qas.dto.response;

import com.example.qas.models.enums.NotificationStatus;
import com.example.qas.models.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private Long appointmentId;
    private String recipient;
    private NotificationType type;
    private NotificationStatus status;
    private String subject;
    private String content;
    private OffsetDateTime sentAt;
    private String errorMessage;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
