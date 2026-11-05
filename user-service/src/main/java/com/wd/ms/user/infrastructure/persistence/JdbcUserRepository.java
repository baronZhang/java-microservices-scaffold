package com.wd.ms.user.infrastructure.persistence;

import com.wd.ms.common.core.PageResult;
import com.wd.ms.common.core.UtcTimes;
import com.wd.ms.user.domain.user.User;
import com.wd.ms.user.domain.user.UserQuery;
import com.wd.ms.user.domain.user.UserRepository;
import com.wd.ms.user.domain.user.UserStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 用户表的 JDBC 实现。 */
@Repository
public class JdbcUserRepository implements UserRepository {

    private final JdbcClient jdbc;

    public JdbcUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<User> page(UserQuery query) {
        // 动态拼 WHERE，但值一律走参数绑定，绝不把用户输入拼进 SQL 字符串
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (!query.keyword().isEmpty()) {
            String like = "%" + escapeLike(query.keyword()) + "%";
            where.append(" AND (LOWER(username) LIKE ? ESCAPE '!' OR nickname LIKE ? ESCAPE '!')");
            params.add(like);
            params.add(like);
        }
        if (query.status() != null) {
            where.append(" AND status = ?");
            params.add(query.status().code());
        }
        long total = jdbc.sql("SELECT COUNT(*) FROM users" + where)
                .params(params)
                .query(Long.class)
                .single();
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(query.page().pageSize());
        pageParams.add(query.page().offset());
        List<User> list = jdbc.sql("SELECT * FROM users" + where + " ORDER BY id LIMIT ? OFFSET ?")
                .params(pageParams)
                .query(JdbcUserRepository::map)
                .list();
        return new PageResult<>(list, total, query.page().page(), query.page().pageSize());
    }

    @Override
    public Optional<User> findById(long id) {
        return jdbc.sql("SELECT * FROM users WHERE id = ?")
                .param(id)
                .query(JdbcUserRepository::map)
                .optional();
    }

    @Override
    public void updateStatus(long id, UserStatus status) {
        jdbc.sql("UPDATE users SET status = ? WHERE id = ?")
                .params(status.code(), id)
                .update();
    }

    @Override
    public void delete(long id) {
        jdbc.sql("DELETE FROM users WHERE id = ?").param(id).update();
    }

    /** LIKE 的通配符也要转义：用户搜 "50%" 时，% 应该按字面匹配，而不是"任意字符" */
    static String escapeLike(String keyword) {
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static User map(ResultSet rs, int row) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("nickname"),
                rs.getString("email"),
                rs.getString("role"),
                UserStatus.fromCode(rs.getString("status")),
                UtcTimes.fromDb(rs.getObject("created_at", LocalDateTime.class)));
    }
}
