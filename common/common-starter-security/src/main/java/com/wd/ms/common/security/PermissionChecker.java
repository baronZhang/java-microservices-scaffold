package com.wd.ms.common.security;

/**
 * 接口级权限检查，以 Bean 名 {@code perm} 注册，在注解里这样用：
 *
 * <pre>{@code @PreAuthorize("@perm.has('user:delete')")}</pre>
 *
 * <p>为什么不用 {@code hasAuthority('user:delete')}：管理员的权限码是通配符 "*"，
 * Spring Security 的 hasAuthority 只做精确匹配，所以自己实现一个支持通配符的检查。
 */
public class PermissionChecker {

    public boolean has(String code) {
        return CurrentUser.find().map(user -> user.hasPermission(code)).orElse(false);
    }
}
