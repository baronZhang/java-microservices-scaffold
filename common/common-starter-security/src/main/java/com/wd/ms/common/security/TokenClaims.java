package com.wd.ms.common.security;

/**
 * access token 里自定义声明的名字。签发方（auth-service）和校验方（网关、各服务）共用这一份常量，
 * 这也是放进公共模块的"契约"的一部分。
 */
public final class TokenClaims {

    /** token 类型：user（用户令牌）或 service（服务令牌） */
    public static final String TYPE = "typ";

    public static final String TYPE_USER = "user";
    public static final String TYPE_SERVICE = "service";

    public static final String USERNAME = "username";
    public static final String NICKNAME = "nickname";
    public static final String ROLES = "roles";
    public static final String PERMISSIONS = "perms";
    /** 会话 id：一次登录产生一个，refresh token 轮换时不变；退出登录按它作废整串 refresh token */
    public static final String SESSION_ID = "sid";
    /** OAuth2 标准的 scope 声明；服务令牌带 internal，才能访问 /internal/** */
    public static final String SCOPE = "scope";

    public static final String SCOPE_INTERNAL = "internal";

    private TokenClaims() {}
}
