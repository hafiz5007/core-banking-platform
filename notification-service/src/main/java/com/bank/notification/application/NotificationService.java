package com.bank.notification.application;

import com.bank.common.error.BusinessException;
import com.bank.common.error.ErrorCode;
import com.bank.notification.adapter.out.persistence.NotificationRepository;
import com.bank.notification.adapter.out.persistence.OtpChallengeRepository;
import com.bank.notification.application.port.NotificationSenderPort;
import com.bank.notification.domain.Notification;
import com.bank.notification.domain.NotificationChannel;
import com.bank.notification.domain.NotificationType;
import com.bank.notification.domain.OtpChallenge;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends customer notifications and manages one-time passcodes (FR-STM-002/003). OTP codes are
 * returned to the caller only via the delivery channel; only a hash is persisted.
 */
@Service
public class NotificationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int OTP_TTL_SECONDS = 300;

    private final NotificationRepository notificationRepository;
    private final OtpChallengeRepository otpRepository;
    private final NotificationSenderPort sender;
    private final ChangeLogRecorder changeLog;

    public NotificationService(NotificationRepository notificationRepository,
                               OtpChallengeRepository otpRepository,
                               NotificationSenderPort sender,
                               ChangeLogRecorder changeLog) {
        this.notificationRepository = notificationRepository;
        this.otpRepository = otpRepository;
        this.sender = sender;
        this.changeLog = changeLog;
    }

    @Transactional
    public Notification sendAlert(String recipient, NotificationType type,
                                  NotificationChannel channel, String message) {
        Notification notification = new Notification(recipient, type, channel, message);
        dispatch(notification);
        Notification saved = notificationRepository.save(notification);
        changeLog.record("Notification", saved.getId().toString(),
                com.bank.notification.domain.ChangeType.CREATE, null,
                type + " via " + channel);
        return saved;
    }

    /** Issue an OTP: generate a 6-digit code, deliver it, and persist only its hash. */
    @Transactional
    public UUID issueOtp(String recipient, NotificationChannel channel) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        OtpChallenge challenge = new OtpChallenge(recipient, hash(code),
                Instant.now().plus(OTP_TTL_SECONDS, ChronoUnit.SECONDS));
        otpRepository.save(challenge);
        Notification notification = new Notification(recipient, NotificationType.OTP, channel,
                "Your verification code is " + code + " (valid 5 minutes)");
        dispatch(notification);
        notificationRepository.save(notification);
        return challenge.getId();
    }

    @Transactional
    public boolean verifyOtp(UUID challengeId, String code) {
        OtpChallenge challenge = otpRepository.findById(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "OTP challenge not found"));
        boolean ok = challenge.verify(hash(code));
        otpRepository.save(challenge);
        return ok;
    }

    private void dispatch(Notification notification) {
        if (sender.send(notification)) {
            notification.markSent();
        } else {
            notification.markFailed();
        }
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Hashing failed", e);
        }
    }
}
