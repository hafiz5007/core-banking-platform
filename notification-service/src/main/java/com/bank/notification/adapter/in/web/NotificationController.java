package com.bank.notification.adapter.in.web;

import com.bank.notification.application.NotificationService;
import com.bank.notification.domain.Notification;
import com.bank.notification.domain.NotificationChannel;
import com.bank.notification.domain.NotificationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/alerts")
    public ResponseEntity<AlertResponse> sendAlert(@Valid @RequestBody AlertRequest request) {
        Notification n = notificationService.sendAlert(
                request.recipient(), request.type(), request.channel(), request.message());
        return ResponseEntity.status(201)
                .body(new AlertResponse(n.getId(), n.getStatus().name()));
    }

    @PostMapping("/otp")
    public ResponseEntity<OtpIssuedResponse> issueOtp(@Valid @RequestBody OtpRequest request) {
        UUID challengeId = notificationService.issueOtp(request.recipient(), request.channel());
        return ResponseEntity.status(201).body(new OtpIssuedResponse(challengeId));
    }

    @PostMapping("/otp/verify")
    public OtpVerifyResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        boolean verified = notificationService.verifyOtp(request.challengeId(), request.code());
        return new OtpVerifyResponse(verified);
    }

    public record AlertRequest(
            @NotBlank String recipient,
            @NotNull NotificationType type,
            @NotNull NotificationChannel channel,
            @NotBlank String message) {
    }

    public record AlertResponse(UUID id, String status) {
    }

    public record OtpRequest(@NotBlank String recipient, @NotNull NotificationChannel channel) {
    }

    public record OtpIssuedResponse(UUID challengeId) {
    }

    public record OtpVerifyRequest(@NotNull UUID challengeId, @NotBlank String code) {
    }

    public record OtpVerifyResponse(boolean verified) {
    }
}
