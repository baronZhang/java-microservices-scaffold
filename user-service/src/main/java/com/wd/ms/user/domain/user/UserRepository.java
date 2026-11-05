package com.wd.ms.user.domain.user;

import com.wd.ms.common.core.PageResult;
import java.util.Optional;

/** 用户仓储。 */
public interface UserRepository {

    PageResult<User> page(UserQuery query);

    Optional<User> findById(long id);

    void updateStatus(long id, UserStatus status);

    void delete(long id);
}
