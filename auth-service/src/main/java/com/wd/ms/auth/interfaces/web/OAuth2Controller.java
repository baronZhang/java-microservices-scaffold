package com.wd.ms.auth.interfaces.web;

import com.wd.ms.auth.application.ClientCredentialsService;
import com.wd.ms.auth.domain.token.AccessTokenIssuer;
import com.wd.ms.auth.domain.token.JwkSetSource;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 给其他服务用的两个 OAuth2 端点。它们不经过网关（网关只转发 /api/**），返回格式遵循 OAuth2 规范而不是统一响应结构：
 * 资源服务器和 OAuth2 客户端库都按规范的格式解析。
 *
 * <ul>
 *   <li>GET /oauth2/jwks：公钥集合（JWKS）。网关和各服务用它验 token 签名；只暴露公钥
 *   <li>POST /oauth2/token：client_credentials 换服务令牌
 * </ul>
 */
@RestController
@RequestMapping("/oauth2")
public class OAuth2Controller {

    private final JwkSetSource jwtKeys;
    private final ClientCredentialsService clientCredentials;

    public OAuth2Controller(JwkSetSource jwtKeys, ClientCredentialsService clientCredentials) {
        this.jwtKeys = jwtKeys;
        this.clientCredentials = clientCredentials;
    }

    @GetMapping("/jwks")
    public Map<String, Object> jwks() {
        return jwtKeys.publicJwkSet();
    }

    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam("grant_type") String grantType,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (!"client_credentials".equals(grantType)) {
            return ResponseEntity.badRequest().body(Map.of("error", "unsupported_grant_type"));
        }
        Optional<String[]> credentials = basicCredentials(authorization);
        Optional<AccessTokenIssuer.IssuedToken> token =
                credentials.flatMap(pair -> clientCredentials.issue(pair[0], pair[1]));
        if (token.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid_client"));
        }
        return ResponseEntity.ok(Map.of(
                "access_token",
                token.get().value(),
                "token_type",
                "Bearer",
                "expires_in",
                token.get().expiresInSeconds(),
                "scope",
                "internal"));
    }

    private static Optional<String[]> basicCredentials(String authorization) {
        if (authorization == null || !authorization.startsWith("Basic ")) {
            return Optional.empty();
        }
        String decoded = new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8);
        int colon = decoded.indexOf(':');
        return colon < 0
                ? Optional.empty()
                : Optional.of(new String[] {decoded.substring(0, colon), decoded.substring(colon + 1)});
    }
}
