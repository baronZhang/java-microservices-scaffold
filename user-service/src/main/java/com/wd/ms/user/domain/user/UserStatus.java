package com.wd.ms.user.domain.user;

/** 用户状态：启用 / 禁用。对外（JSON）用小写，见接口层的转换。 */
public enum UserStatus {
    ACTIVE,
    DISABLED;

    public String code() {
        return name().toLowerCase();
    }

    /** 和 Mock 一致：只有明确传 disabled 才是禁用，其他值一律当作启用 */
    public static UserStatus fromCode(String code) {
        return "disabled".equals(code) ? DISABLED : ACTIVE;
    }
}
