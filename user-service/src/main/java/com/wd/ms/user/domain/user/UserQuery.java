package com.wd.ms.user.domain.user;

import com.wd.ms.common.core.PageQuery;

/**
 * 用户列表的查询条件，与前端 src/types/user.ts 的 UserQuery 对应。
 *
 * @param keyword 按用户名（不区分大小写）或昵称模糊搜索，空表示不过滤
 * @param status 按状态过滤，null 表示全部
 */
public record UserQuery(String keyword, UserStatus status, PageQuery page) {

    public UserQuery {
        keyword = keyword == null ? "" : keyword.trim().toLowerCase();
    }
}
