package com.wd.ms.auth.domain.token;

import java.util.Map;

/** 对外公布的公钥集合（JWKS 格式）。接口层只依赖这个接口，密钥怎么生成、怎么轮换是基础设施层的事。 */
public interface JwkSetSource {

    Map<String, Object> publicJwkSet();
}
