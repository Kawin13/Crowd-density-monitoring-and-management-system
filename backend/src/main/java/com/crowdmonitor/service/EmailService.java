package com.crowdmonitor.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.email.from}")
    private String fromEmail;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public void sendPasswordResetEmail(String toEmail, String name, String resetToken) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Password Reset - Crowd Monitoring System");
            message.setText(
                "Hello " + (name != null ? name : "") + ",\n\n" +
                "You requested to reset your password. Click the link below:\n\n" +
                frontendUrl + "/reset-password?token=" + resetToken + "\n\n" +
                "This link expires in 1 hour.\n\n" +
                "If you did not request this, ignore this email.\n\n" +
                "Crowd Monitoring Team"
            );
            mailSender.send(message);
            log.info("Password reset email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendAlertEmail(String toEmail, String cameraName, String alertType, double occupancy) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Crowd Alert: " + alertType + " - " + cameraName);
            message.setText(
                "CROWD DENSITY ALERT\n\n" +
                "Camera: " + cameraName + "\n" +
                "Alert Level: " + alertType + "\n" +
                "Occupancy: " + String.format("%.1f", occupancy) + "%\n\n" +
                "Please take immediate action.\n\n" +
                "Crowd Monitoring System"
            );
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send alert email: {}", e.getMessage());
        }
    }
}
