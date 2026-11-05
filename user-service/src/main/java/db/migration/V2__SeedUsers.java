package db.migration;

import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * 种子数据：60 个用户，逐行移植自前端 Mock 的 createUsers（mock/data.ts）。
 * 同样的线性同余伪随机数、同样的种子 7、同样的取值顺序，所以生成的昵称、角色、状态和 Mock 一模一样，
 * 前端切换到真实后端后看到的列表不变。
 */
public class V2__SeedUsers extends BaseJavaMigration {

    private static final String[] SURNAMES = "张王李赵刘陈杨黄周吴徐孙".split("");
    private static final String[] GIVEN_NAMES = "伟芳娜敏静磊洋勇艳杰涛明超婷".split("");
    private static final String[] ROLES = {"editor", "guest", "guest", "editor", "guest"};
    /** 2026-01-01T09:00:00+08:00 */
    private static final Instant BASE_TIME = Instant.parse("2026-01-01T01:00:00Z");

    /** 前 3 个用户就是三个演示账号 */
    private static final String[][] ACCOUNTS = {
        {"1", "admin", "管理员", "admin"},
        {"2", "editor", "运营小王", "editor"},
        {"3", "guest", "访客", "guest"},
    };

    /** 和 JS 版一样的线性同余发生器：state = (state * 9301 + 49297) % 233280 */
    static final class SeededRandom {
        private long state;

        SeededRandom(long seed) {
            this.state = seed;
        }

        double next() {
            state = (state * 9301 + 49297) % 233280;
            return state / 233280.0;
        }

        String pick(String[] items) {
            return items[(int) Math.floor(next() * items.length)];
        }
    }

    @Override
    public void migrate(Context context) throws Exception {
        String sql = "INSERT INTO users (id, username, nickname, email, role, status, created_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = context.getConnection().prepareStatement(sql)) {
            for (String[] account : ACCOUNTS) {
                add(
                        statement,
                        Long.parseLong(account[0]),
                        account[1],
                        account[2],
                        account[1] + "@example.com",
                        account[3],
                        "active",
                        BASE_TIME);
            }
            SeededRandom random = new SeededRandom(7);
            for (long id = ACCOUNTS.length + 1; id <= 60; id++) {
                // 取值顺序必须和 JS 对象字面量里的求值顺序一致：昵称（2~3 次）→ 角色 → 状态
                String nickname = random.pick(SURNAMES)
                        + random.pick(GIVEN_NAMES)
                        + (random.next() > 0.5 ? random.pick(GIVEN_NAMES) : "");
                String role = random.pick(ROLES);
                String status = random.next() > 0.2 ? "active" : "disabled";
                Instant createdAt = BASE_TIME.plusSeconds(id * 36 * 3600);
                add(
                        statement,
                        id,
                        "user%03d".formatted(id),
                        nickname,
                        "user" + id + "@example.com",
                        role,
                        status,
                        createdAt);
            }
            statement.executeBatch();
        }
    }

    private static void add(
            PreparedStatement statement,
            long id,
            String username,
            String nickname,
            String email,
            String role,
            String status,
            Instant createdAt)
            throws Exception {
        statement.setLong(1, id);
        statement.setString(2, username);
        statement.setString(3, nickname);
        statement.setString(4, email);
        statement.setString(5, role);
        statement.setString(6, status);
        statement.setObject(7, LocalDateTime.ofInstant(createdAt, ZoneOffset.UTC));
        statement.addBatch();
    }
}
