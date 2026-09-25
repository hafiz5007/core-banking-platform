package com.bank.notification.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.bank.notification.AbstractIntegrationTest;
import com.bank.notification.adapter.in.web.NotificationController.OtpIssuedResponse;
import com.bank.notification.adapter.in.web.NotificationController.OtpRequest;
import com.bank.notification.adapter.in.web.NotificationController.OtpVerifyRequest;
import com.bank.notification.adapter.in.web.NotificationController.OtpVerifyResponse;
import com.bank.notification.domain.NotificationChannel;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** End-to-end test of OTP issuance and verification (wrong code is rejected). */
class NotificationControllerIT extends AbstractIntegrationTest {

  @Autowired TestRestTemplate rest;

  @Test
  void issuesOtpAndRejectsWrongCode() {
    ResponseEntity<OtpIssuedResponse> issued =
        rest.postForEntity(
            "/api/v1/notifications/otp",
            new OtpRequest("user@example.com", NotificationChannel.EMAIL),
            OtpIssuedResponse.class);
    assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    UUID challengeId = issued.getBody().challengeId();
    assertThat(challengeId).isNotNull();

    // A wrong code must not verify (the real code is delivered out-of-band, not returned).
    ResponseEntity<OtpVerifyResponse> verify =
        rest.postForEntity(
            "/api/v1/notifications/otp/verify",
            new OtpVerifyRequest(challengeId, "000000"),
            OtpVerifyResponse.class);
    assertThat(verify.getStatusCode()).isEqualTo(HttpStatus.OK);
    // The probability the random code is exactly 000000 is negligible.
    assertThat(verify.getBody().verified()).isFalse();
  }
}
