package com.wd.ms.auth.domain.token;

import com.wd.ms.auth.domain.account.Account;
import java.util.List;

/** 签发 access token。领域层不关心是 JWT 还是别的格式，具体实现（Nimbus + RSA）在基础设施层。 */
public interface AccessTokenIssuer {

    IssuedToken issueUserToken(Account account, String sessionId);

    IssuedToken issueServiceToken(String clientId, List<String> scopes);

    /** 签发结果：token 字符串和有效期（秒） */
    record IssuedToken(String value, long expiresInSeconds) {}
}
