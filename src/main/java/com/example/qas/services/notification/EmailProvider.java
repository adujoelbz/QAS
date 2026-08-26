package com.example.qas.services.notification;

public interface EmailProvider {
    void sendEmail(String to, String subject, String body, boolean isHtml);
}