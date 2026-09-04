package com.example.qas.services.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EmailProviderImpl implements EmailProvider {

    private final WebClient webClient;

    @Value("${sendgrid.enabled:false}")
    private boolean enabled;

    @Value("${sendgrid.api-key:}")
    private String apiKey;

    @Value("${sendgrid.api-url:https://api.sendgrid.com/v3/mail/send}")
    private String apiUrl;

    @Value("${app.notification.email-from}")
    private String fromEmail;

    @Value("${sendgrid.timeout:10000}")
    private int timeoutMillis;

    @Override
    public void sendEmail(String to, String subject, String body, boolean isHtml) {
        if (!enabled) {
            throw new IllegalStateException("SendGrid email delivery is disabled");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("SENDGRID_API_KEY is not configured");
        }
        if (to == null || to.isBlank()) {
            throw new IllegalArgumentException("Recipient email is required");
        }
        if (fromEmail == null || fromEmail.isBlank()) {
            throw new IllegalStateException("MAIL_FROM is not configured");
        }

        Map<String, Object> payload = Map.of(
                "personalizations", List.of(Map.of("to", List.of(Map.of("email", to)))),
                "from", Map.of("email", fromEmail),
                "subject", subject == null ? "" : subject,
                "content", List.of(Map.of(
                        "type", isHtml ? "text/html" : "text/plain",
                        "value", body == null ? "" : body)));

        try {
            webClient.post()
                    .uri(apiUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofMillis(timeoutMillis))
                    .block();
        } catch (Exception e) {
            throw new RuntimeException("SendGrid email delivery failed: " + rootMessage(e), e);
        }
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? error.getClass().getSimpleName() : current.getMessage();
    }
}
