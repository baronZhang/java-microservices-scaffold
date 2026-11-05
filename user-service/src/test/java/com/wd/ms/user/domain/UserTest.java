package com.wd.ms.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wd.ms.common.core.BizException;
import com.wd.ms.user.domain.user.User;
import com.wd.ms.user.domain.user.UserStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 领域规则是纯 Java，不需要 Spring 上下文，毫秒级就能跑完。 */
class UserTest {

    private final User user =
            new User(5, "user005", "杨芳艳", "user5@example.com", "guest", UserStatus.ACTIVE, Instant.EPOCH);

    @Test
    void 可以修改别人的状态() {
        assertThat(user.changeStatus(UserStatus.DISABLED, 1).status()).isEqualTo(UserStatus.DISABLED);
    }

    @Test
    void 不能修改自己的状态() {
        assertThatThrownBy(() -> user.changeStatus(UserStatus.DISABLED, 5))
                .isInstanceOf(BizException.class)
                .hasMessage("不能禁用当前登录的账号");
    }

    @Test
    void 状态码只有disabled是禁用() {
        assertThat(UserStatus.fromCode("disabled")).isEqualTo(UserStatus.DISABLED);
        assertThat(UserStatus.fromCode("whatever")).isEqualTo(UserStatus.ACTIVE);
    }
}
