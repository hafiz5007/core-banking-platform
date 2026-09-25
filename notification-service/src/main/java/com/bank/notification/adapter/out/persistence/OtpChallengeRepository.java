package com.bank.notification.adapter.out.persistence;

import com.bank.notification.domain.OtpChallenge;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, UUID> {}
