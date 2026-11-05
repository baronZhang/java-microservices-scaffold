package com.wd.ms.common.core;

/**
 * 分页参数。规则与前端 Mock 一致：页码从 1 开始；每页条数 1~100，默认 10。
 *
 * <p>在构造时就把非法值修正掉，后面的代码不用再判断。
 */
public record PageQuery(int page, int pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;

    public PageQuery {
        page = Math.max(1, page);
        pageSize = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(MAX_PAGE_SIZE, pageSize);
    }

    /** 允许参数缺省（null），方便直接接收 @RequestParam(required = false) 的值 */
    public static PageQuery of(Integer page, Integer pageSize) {
        return new PageQuery(page == null ? 1 : page, pageSize == null ? DEFAULT_PAGE_SIZE : pageSize);
    }

    public int offset() {
        return (page - 1) * pageSize;
    }
}
