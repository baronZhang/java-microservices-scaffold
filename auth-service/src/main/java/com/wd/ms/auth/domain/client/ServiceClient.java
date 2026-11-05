package com.wd.ms.auth.domain.client;

import java.util.List;

/** 一个可以用 client_credentials 换服务令牌的服务。 */
public record ServiceClient(String clientId, String secret, List<String> scopes) {

    public ServiceClient {
        scopes = List.copyOf(scopes);
    }
}
