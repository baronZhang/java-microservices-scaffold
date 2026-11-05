package com.wd.ms.common.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 安全相关配置（前缀 {@code ms.security}）。
 *
 * @param jwkSetUri 认证服务的公钥地址。用服务名而不是 IP，由负载均衡解析成具体实例
 * @param issuer 签发方，校验 token 的 iss 声明，防止拿别的系统签发的 token 来用
 * @param publicPaths 不需要登录就能访问的路径（在网关之外再声明一次：服务自己也要知道哪些接口是公开的）
 * @param client 本服务调用其他服务时用的身份（OAuth2 client_credentials）；不调用别的服务就不用配
 */
@ConfigurationProperties("ms.security")
public record MsSecurityProperties(String jwkSetUri, String issuer, List<String> publicPaths, Client client) {

    public MsSecurityProperties {
        jwkSetUri = jwkSetUri == null ? "http://auth-service/oauth2/jwks" : jwkSetUri;
        issuer = issuer == null ? "ms-auth" : issuer;
        publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
    }

    /**
     * 服务身份。
     *
     * @param id 客户端 id，一般就是服务名
     * @param secret 客户端密钥。生产环境从 K8s Secret / 配置中心加密配置注入，不能写在代码仓库里
     * @param tokenUri 令牌端点
     */
    public record Client(String id, String secret, String tokenUri) {

        public Client {
            tokenUri = tokenUri == null ? "http://auth-service/oauth2/token" : tokenUri;
        }
    }
}
