package com.wd.ms.user.application;

import com.wd.ms.common.core.BizException;
import com.wd.ms.common.core.PageResult;
import com.wd.ms.user.domain.user.User;
import com.wd.ms.user.domain.user.UserQuery;
import com.wd.ms.user.domain.user.UserRepository;
import com.wd.ms.user.domain.user.UserStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户管理用例。应用层只做编排：取数据 → 调领域规则 → 存回去；规则本身在 {@link User} 里。 */
@Service
public class UserApplicationService {

    private final UserRepository users;

    public UserApplicationService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PageResult<User> page(UserQuery query) {
        return users.page(query);
    }

    @Transactional(readOnly = true)
    public User get(long id) {
        return find(id);
    }

    @Transactional
    public User changeStatus(long id, UserStatus status, long operatorId) {
        User changed = find(id).changeStatus(status, operatorId);
        users.updateStatus(id, changed.status());
        return changed;
    }

    @Transactional
    public void delete(long id, long operatorId) {
        find(id).checkDeletableBy(operatorId);
        users.delete(id);
    }

    private User find(long id) {
        return users.findById(id).orElseThrow(() -> BizException.notFound("用户 " + id + " 不存在"));
    }
}
