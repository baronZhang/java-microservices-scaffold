package com.wd.ms.product.domain.product;

import com.wd.ms.common.core.PageQuery;
import com.wd.ms.common.core.PageResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 商品仓储。 */
public interface ProductRepository {

    PageResult<Product> page(String keyword, PageQuery page);

    Optional<Product> findById(long id);

    List<Product> findByIds(Collection<Long> ids);
}
