package com.wd.ms.auth.domain.account;

import java.util.Optional;

/** 账号仓储。领域层只定义接口，JDBC 实现在基础设施层。 */
public interface AccountRepository {

    Optional<Account> findByUsername(String username);

    Optional<Account> findById(long id);
}
