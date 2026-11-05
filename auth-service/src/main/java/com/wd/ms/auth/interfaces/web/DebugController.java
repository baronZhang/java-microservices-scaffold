package com.wd.ms.auth.interfaces.web;

import com.wd.ms.auth.application.RefreshStats;
import com.wd.ms.common.core.Result;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 演示辅助接口，只在 demo profile 下存在：前端"Token 无感刷新"演示页用它查看后端收到了几次刷新请求。
 *
 * <p>另一个演示接口 POST /api/debug/expire-token 由网关自己处理（token 吊销清单在网关），不在这里。
 */
@RestController
@Profile("demo")
public class DebugController {

    private final RefreshStats stats;

    public DebugController(RefreshStats stats) {
        this.stats = stats;
    }

    @GetMapping("/api/debug/stats")
    public Result<Map<String, Long>> stats() {
        return Result.ok(Map.of("refreshCount", stats.refreshCount()));
    }
}
