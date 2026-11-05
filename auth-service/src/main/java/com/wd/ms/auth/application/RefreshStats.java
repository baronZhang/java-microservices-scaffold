package com.wd.ms.auth.application;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * 累计收到的刷新请求次数，给前端"Token 无感刷新"演示页看：并发 401 时后端是不是只收到 1 次刷新。
 * 只是进程内计数，多实例部署时每个实例各算各的（演示够用）。
 */
@Component
public class RefreshStats {

    private final AtomicLong refreshCount = new AtomicLong();

    public void increment() {
        refreshCount.incrementAndGet();
    }

    public long refreshCount() {
        return refreshCount.get();
    }
}
