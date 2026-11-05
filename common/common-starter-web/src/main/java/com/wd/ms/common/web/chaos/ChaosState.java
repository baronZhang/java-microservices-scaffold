package com.wd.ms.common.web.chaos;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 当前的故障注入设置。字段用 volatile：端点线程写、请求线程读，只需要可见性，不需要原子的复合操作。
 */
public class ChaosState {

    /** 每个匹配的请求先睡这么久，模拟"服务变慢" */
    private volatile long delayMs;
    /** 按这个概率直接抛异常，模拟"服务出错"，取值 0~1 */
    private volatile double failureRate;
    /** 只对匹配这个路径模式的请求生效，比如 /internal/** */
    private volatile String pathPattern = "/**";

    public void update(Long delayMs, Double failureRate, String pathPattern) {
        if (delayMs != null) {
            this.delayMs = Math.max(0, delayMs);
        }
        if (failureRate != null) {
            this.failureRate = Math.min(1, Math.max(0, failureRate));
        }
        if (pathPattern != null && !pathPattern.isBlank()) {
            this.pathPattern = pathPattern;
        }
    }

    public void reset() {
        delayMs = 0;
        failureRate = 0;
        pathPattern = "/**";
    }

    public long delayMs() {
        return delayMs;
    }

    public double failureRate() {
        return failureRate;
    }

    public String pathPattern() {
        return pathPattern;
    }

    public boolean active() {
        return delayMs > 0 || failureRate > 0;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("delayMs", delayMs);
        map.put("failureRate", failureRate);
        map.put("pathPattern", pathPattern);
        return map;
    }
}
