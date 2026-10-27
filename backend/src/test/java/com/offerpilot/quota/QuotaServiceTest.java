package com.offerpilot.quota;

import com.offerpilot.common.exception.BusinessException;
import com.offerpilot.quota.service.QuotaService;
import com.offerpilot.user.domain.User;
import com.offerpilot.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class QuotaServiceTest {

    @Autowired
    private QuotaService quotaService;

    @Autowired
    private UserMapper userMapper;

    @Test
    void limitsFreeUserToThreeSessions() {
        SecurityContextHolder.clearContext();
        User user = new User();
        user.setEmail("quota-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$placeholder");
        user.setNickname("Quota Tester");
        user.setRole("USER");
        user.setStatus("ACTIVE");
        userMapper.insert(user);

        quotaService.checkAndConsumeSession(user.getId());
        quotaService.checkAndConsumeSession(user.getId());
        quotaService.checkAndConsumeSession(user.getId());

        assertThatThrownBy(() -> quotaService.checkAndConsumeSession(user.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("额度");
        assertThat(quotaService.me(user.getId()).remainingSessions()).isZero();
    }
}
