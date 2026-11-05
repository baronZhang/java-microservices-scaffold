package com.wd.ms.auth.application;

import com.wd.ms.auth.domain.client.ServiceClient;
import com.wd.ms.auth.domain.client.ServiceClientRegistry;
import com.wd.ms.auth.domain.token.AccessTokenIssuer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * OAuth2 client_credentials：服务用自己的 id 和密钥换一个服务令牌（scope=internal），用来调用其他服务的内部接口。
 *
 * <p>为什么服务间也要认证：内网不等于可信。没有服务身份，任何能连上内网的进程都能调 /internal/** 改库存。
 */
@Service
public class ClientCredentialsService {

    private final ServiceClientRegistry clients;
    private final AccessTokenIssuer tokenIssuer;

    public ClientCredentialsService(ServiceClientRegistry clients, AccessTokenIssuer tokenIssuer) {
        this.clients = clients;
        this.tokenIssuer = tokenIssuer;
    }

    /** 校验失败返回 empty，由接口层按 OAuth2 规范返回 invalid_client */
    public Optional<AccessTokenIssuer.IssuedToken> issue(String clientId, String secret) {
        return clients.find(clientId)
                .filter(client -> secret != null && constantTimeEquals(client.secret(), secret))
                .map(ServiceClient::scopes)
                .map(scopes -> tokenIssuer.issueServiceToken(clientId, scopes));
    }

    /** 常量时间比较：比较耗时不随"前几个字符对不对"变化，防止逐字符猜密钥 */
    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
