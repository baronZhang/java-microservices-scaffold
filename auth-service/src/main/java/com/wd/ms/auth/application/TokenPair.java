package com.wd.ms.auth.application;

/** 登录、刷新返回的一对 token，对应前端 src/types/auth.ts 的 TokenPair。 */
public record TokenPair(String accessToken, String refreshToken, long expiresIn) {}
