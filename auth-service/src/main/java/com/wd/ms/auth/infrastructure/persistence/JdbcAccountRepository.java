package com.wd.ms.auth.infrastructure.persistence;

import com.wd.ms.auth.domain.account.Account;
import com.wd.ms.auth.domain.account.AccountRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 账号表的 JDBC 实现。用 JdbcClient 手写 SQL 而不是 JPA：表少、查询简单，SQL 一目了然；
 * 库存扣减这类"条件更新"本来就要手写 SQL，统一风格。
 */
@Repository
public class JdbcAccountRepository implements AccountRepository {

    private final JdbcClient jdbc;

    public JdbcAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Account> findByUsername(String username) {
        return jdbc.sql("SELECT * FROM account WHERE username = ?")
                .param(username)
                .query(JdbcAccountRepository::map)
                .optional();
    }

    @Override
    public Optional<Account> findById(long id) {
        return jdbc.sql("SELECT * FROM account WHERE id = ?")
                .param(id)
                .query(JdbcAccountRepository::map)
                .optional();
    }

    private static Account map(ResultSet rs, int row) throws SQLException {
        return new Account(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("nickname"),
                split(rs.getString("roles")),
                split(rs.getString("permissions")));
    }

    /** 演示简化：角色、权限码用逗号分隔存在一列里 */
    private static List<String> split(String value) {
        return value == null || value.isBlank() ? List.of() : Arrays.asList(value.split(","));
    }
}
