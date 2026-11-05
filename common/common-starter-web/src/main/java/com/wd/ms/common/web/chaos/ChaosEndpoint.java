package com.wd.ms.common.web.chaos;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;

/**
 * 故障注入端点：{@code /actuator/chaos}，只在 demo profile 打开（{@code ms.chaos.enabled=true}）。
 *
 * <pre>
 * curl -X POST localhost:9084/actuator/chaos -H 'Content-Type: application/json' \
 *      -d '{"delayMs":3000,"pathPattern":"/internal/**"}'     # 让库存服务的内部接口变慢 3 秒
 * curl -X DELETE localhost:9084/actuator/chaos                # 恢复正常
 * </pre>
 *
 * <p>做成 actuator 端点而不是普通接口：它属于"运维面"，生产里和其他管理端点一起放在单独的管理端口、单独鉴权。
 */
@Endpoint(id = "chaos")
public class ChaosEndpoint {

    private final ChaosState state;

    public ChaosEndpoint(ChaosState state) {
        this.state = state;
    }

    @ReadOperation
    public Map<String, Object> current() {
        return state.snapshot();
    }

    @WriteOperation
    public Map<String, Object> configure(
            @Nullable Long delayMs, @Nullable Double failureRate, @Nullable String pathPattern) {
        state.update(delayMs, failureRate, pathPattern);
        return state.snapshot();
    }

    @DeleteOperation
    public Map<String, Object> reset() {
        state.reset();
        return state.snapshot();
    }
}
