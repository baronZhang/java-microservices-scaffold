package com.wd.ms.auth.domain.account;

import java.util.List;

/**
 * 账号：认证服务关心的"身份 + 权限"。用户资料（邮箱、状态……）归用户服务管，两边各存各的。
 *
 * <p>演示简化：角色和权限码直接挂在账号上。生产一般是 用户—角色—权限 三张表的 RBAC 模型。
 */
public record Account(
        long id, String username, String passwordHash, String nickname, List<String> roles, List<String> permissions) {

    public Account {
        roles = List.copyOf(roles);
        permissions = List.copyOf(permissions);
    }
}
