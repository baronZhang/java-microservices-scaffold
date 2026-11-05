package db.migration;

import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 种子数据：三个演示账号，和前端 Mock（mock/data.ts）的账号、昵称、角色、权限码完全一致。
 *
 * <p>用 Java 迁移而不是 SQL：入库前用 BCrypt 哈希，数据库里只有哈希值。
 * 演示密码只写在这一个地方（种子数据）；测试和脚本都从这里读取，不再另外硬编码。
 */
public class V2__SeedDemoAccounts extends BaseJavaMigration {

    /** 与前端 Mock 的 DEMO_PASSWORD 相同，登录页已经预填 */
    public static final String DEMO_PASSWORD = "123456";

    /** 每行依次是：id、用户名、昵称、角色、权限 */
    private static final String[][] ACCOUNTS = {
        // 管理员：'*' 表示拥有全部权限
        {"1", "admin", "管理员", "admin", "*"},
        // 运营：能查看、启用/禁用用户，但没有删除权限 → 演示按钮级权限
        {"2", "editor", "运营小王", "editor", "user:list,user:edit"},
        // 访客：没有任何权限码 → 演示路由级权限
        {"3", "guest", "访客", "guest", ""},
    };

    @Override
    public void migrate(Context context) throws Exception {
        PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        String sql = "INSERT INTO account (id, username, password_hash, nickname, roles, permissions, created_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = context.getConnection().prepareStatement(sql)) {
            for (String[] account : ACCOUNTS) {
                statement.setLong(1, Long.parseLong(account[0]));
                statement.setString(2, account[1]);
                statement.setString(3, encoder.encode(DEMO_PASSWORD));
                statement.setString(4, account[2]);
                statement.setString(5, account[3]);
                statement.setString(6, account[4]);
                // 与 Mock 的 createdAt 一致：2026-01-01 09:00（北京时间）= 01:00 UTC
                statement.setObject(7, LocalDateTime.of(2026, 1, 1, 1, 0));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
