package com.wd.ms.user.domain.user;

import com.wd.ms.common.core.BizException;
import java.time.Instant;

/**
 * 用户（领域模型）。业务规则写在模型自己身上，而不是散落在 Service 里——这样规则有唯一的归属，也最好测。
 *
 * @param role 角色：admin / editor / guest。演示里只是展示用，真正的权限码在认证服务
 */
public record User(
        long id, String username, String nickname, String email, String role, UserStatus status, Instant createdAt) {

    /** 规则：不能修改当前登录账号自己的状态（避免把自己锁在门外） */
    public User changeStatus(UserStatus newStatus, long operatorId) {
        if (id == operatorId) {
            throw BizException.rule("不能禁用当前登录的账号");
        }
        return new User(id, username, nickname, email, role, newStatus, createdAt);
    }

    /** 规则：不能删除当前登录的账号 */
    public void checkDeletableBy(long operatorId) {
        if (id == operatorId) {
            throw BizException.rule("不能删除当前登录的账号");
        }
    }
}
