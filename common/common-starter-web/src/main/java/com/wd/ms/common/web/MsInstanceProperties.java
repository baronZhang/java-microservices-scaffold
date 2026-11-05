package com.wd.ms.common.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 实例信息。{@code version} 会出现在 X-Served-By 响应头里，灰度实验用它区分新老版本。
 *
 * @param version 实例版本，比如 v1（稳定版）、v2（灰度版）
 */
@ConfigurationProperties("ms.instance")
public record MsInstanceProperties(String version) {

    public MsInstanceProperties {
        version = version == null || version.isBlank() ? "v1" : version;
    }
}
