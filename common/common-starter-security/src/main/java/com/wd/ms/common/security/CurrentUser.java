package com.wd.ms.common.security;

import com.wd.ms.common.core.BizException;
import com.wd.ms.common.core.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * 当前登录用户：从已经验过签名的 access token 里解析出来，不再查库。
 *
 * <p>代价：权限变更要等旧 token 过期（最长 5 分钟）才生效。这是 JWT 无状态带来的取舍，见文档"认证鉴权"。
 */
public record CurrentUser(long id, String username, String nickname, List<String> roles, List<String> permissions) {

    /** 权限码 "*" 表示拥有全部权限（和前端 Mock 的约定一致） */
    public static final String ALL_PERMISSIONS = "*";

    public CurrentUser {
        roles = List.copyOf(roles);
        permissions = List.copyOf(permissions);
    }

    public boolean hasPermission(String code) {
        return permissions.contains(ALL_PERMISSIONS) || permissions.contains(code);
    }

    /** 当前请求的用户；服务令牌（服务间调用）或匿名请求返回 empty */
    public static Optional<CurrentUser> find() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            return Optional.empty();
        }
        Jwt jwt = token.getToken();
        if (!TokenClaims.TYPE_USER.equals(jwt.getClaimAsString(TokenClaims.TYPE))) {
            return Optional.empty();
        }
        return Optional.of(new CurrentUser(
                Long.parseLong(jwt.getSubject()),
                jwt.getClaimAsString(TokenClaims.USERNAME),
                jwt.getClaimAsString(TokenClaims.NICKNAME),
                listClaim(jwt, TokenClaims.ROLES),
                listClaim(jwt, TokenClaims.PERMISSIONS)));
    }

    /** 业务代码里取当前用户：没有登录用户就是调用方式错了，按 401 处理 */
    public static CurrentUser get() {
        return find().orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
    }

    private static List<String> listClaim(Jwt jwt, String name) {
        List<String> values = jwt.getClaimAsStringList(name);
        return values == null ? List.of() : values;
    }
}
