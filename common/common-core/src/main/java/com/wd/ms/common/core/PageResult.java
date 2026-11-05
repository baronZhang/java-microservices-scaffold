package com.wd.ms.common.core;

import java.util.List;
import java.util.function.Function;

/** 分页结果，对应前端的 {@code PageResult<T>}：{@code {list, total, page, pageSize}}。 */
public record PageResult<T>(List<T> list, long total, int page, int pageSize) {

    public PageResult {
        list = List.copyOf(list);
    }

    /** 转换列表元素的类型（比如领域对象 → 响应 DTO），分页信息原样保留 */
    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        return new PageResult<>(list.stream().<R>map(mapper).toList(), total, page, pageSize);
    }
}
