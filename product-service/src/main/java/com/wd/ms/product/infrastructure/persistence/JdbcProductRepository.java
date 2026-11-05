package com.wd.ms.product.infrastructure.persistence;

import com.wd.ms.common.core.PageQuery;
import com.wd.ms.common.core.PageResult;
import com.wd.ms.product.domain.product.Product;
import com.wd.ms.product.domain.product.ProductRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 商品表的 JDBC 实现。 */
@Repository
public class JdbcProductRepository implements ProductRepository {

    private static final String COLUMNS = "id, name, price, description, on_sale";
    private final JdbcClient jdbc;

    public JdbcProductRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<Product> page(String keyword, PageQuery page) {
        String like = "%"
                + (keyword == null ? "" : keyword.trim())
                        .replace("!", "!!")
                        .replace("%", "!%")
                        .replace("_", "!_") + "%";
        long total = jdbc.sql("SELECT COUNT(*) FROM product WHERE name LIKE ? ESCAPE '!'")
                .param(like)
                .query(Long.class)
                .single();
        List<Product> list = jdbc.sql(
                        "SELECT " + COLUMNS + " FROM product WHERE name LIKE ? ESCAPE '!' ORDER BY id LIMIT ? OFFSET ?")
                .params(like, page.pageSize(), page.offset())
                .query(Product.class)
                .list();
        return new PageResult<>(list, total, page.page(), page.pageSize());
    }

    @Override
    public Optional<Product> findById(long id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM product WHERE id = ?")
                .param(id)
                .query(Product.class)
                .optional();
    }

    @Override
    public List<Product> findByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT " + COLUMNS + " FROM product WHERE id IN (:ids) ORDER BY id")
                .param("ids", ids)
                .query(Product.class)
                .list();
    }
}
