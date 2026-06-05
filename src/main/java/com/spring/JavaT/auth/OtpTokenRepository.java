package com.spring.JavaT.auth;

import com.spring.JavaT.common.OtpPurpose;
import com.spring.JavaT.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {

    void deleteAllByUserAndPurpose(User user, OtpPurpose purpose);

    Optional<OtpToken> findTopByUserAndPurposeAndUsedFalseOrderByCreatedAtDesc(User user, OtpPurpose purpose);
}
