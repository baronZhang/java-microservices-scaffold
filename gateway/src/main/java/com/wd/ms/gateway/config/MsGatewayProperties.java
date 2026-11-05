package com.wd.ms.gateway.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关的自定义配置（前缀 {@code ms.gateway}）。
 *
 * <p>刻意写成带 setter 的普通类，而不是 record：配置中心推送变更后，Spring Cloud 的
 * ConfigurationPropertiesRebinder 会就地重新绑定这个对象；record 的字段是 final，重新绑定不了。
 * 过滤器每次请求都通过 getter 读最新值，所以限流阈值、灰度名单改了立即生效，不用重启。
 */
@ConfigurationProperties("ms.gateway")
public class MsGatewayProperties {

    /** 不需要登录的接口 */
    private List<String> publicPaths = new ArrayList<>();
    /** 认证服务的公钥地址（服务名，经负载均衡） */
    private String jwkSetUri = "http://auth-service/oauth2/jwks";

    private String issuer = "ms-auth";
    private RateLimit rateLimit = new RateLimit();
    private Gray gray = new Gray();

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public Gray getGray() {
        return gray;
    }

    public void setGray(Gray gray) {
        this.gray = gray;
    }

    /** 令牌桶限流：每秒补充 replenishRate 个令牌，桶容量 burstCapacity（允许的瞬时突发） */
    public static class RateLimit {

        private boolean enabled = true;
        /** memory：单网关实例的内存令牌桶；redis：多个网关实例共享的 Redis 令牌桶 */
        private String backend = "memory";
        /** 普通接口：按用户（未登录按 IP）限流 */
        private Policy defaults = new Policy(20, 40);
        /** 登录接口：按 IP 限流，防暴力破解 */
        private Policy login = new Policy(1, 5);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBackend() {
            return backend;
        }

        public void setBackend(String backend) {
            this.backend = backend;
        }

        public Policy getDefaults() {
            return defaults;
        }

        public void setDefaults(Policy defaults) {
            this.defaults = defaults;
        }

        public Policy getLogin() {
            return login;
        }

        public void setLogin(Policy login) {
            this.login = login;
        }
    }

    public static class Policy {

        private int replenishRate;
        private int burstCapacity;

        public Policy() {}

        public Policy(int replenishRate, int burstCapacity) {
            this.replenishRate = replenishRate;
            this.burstCapacity = burstCapacity;
        }

        public int getReplenishRate() {
            return replenishRate;
        }

        public void setReplenishRate(int replenishRate) {
            this.replenishRate = replenishRate;
        }

        public int getBurstCapacity() {
            return burstCapacity;
        }

        public void setBurstCapacity(int burstCapacity) {
            this.burstCapacity = burstCapacity;
        }
    }

    /** 灰度：哪些请求打上灰度标签（X-Gray: gray），由负载均衡路由到 hint=gray 的实例 */
    public static class Gray {

        private boolean enabled = true;
        /** 是否允许客户端直接带 X-Gray 请求头指定灰度（测试同学用；生产一般只对内网 / 白名单开放） */
        private boolean allowHeaderOverride = true;
        /** 灰度用户白名单 */
        private List<Long> userIds = new ArrayList<>();
        /** 按用户 id 取模的灰度比例，0~100 */
        private int percentage;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAllowHeaderOverride() {
            return allowHeaderOverride;
        }

        public void setAllowHeaderOverride(boolean allowHeaderOverride) {
            this.allowHeaderOverride = allowHeaderOverride;
        }

        public List<Long> getUserIds() {
            return userIds;
        }

        public void setUserIds(List<Long> userIds) {
            this.userIds = userIds;
        }

        public int getPercentage() {
            return percentage;
        }

        public void setPercentage(int percentage) {
            this.percentage = percentage;
        }
    }
}
