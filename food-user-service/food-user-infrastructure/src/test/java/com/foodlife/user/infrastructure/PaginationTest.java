package com.foodlife.user.infrastructure;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.session.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.util.Map;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PaginationTest {
    @Test void componentScannedPaginationActuallyAddsSqlLimit() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.scan("com.foodlife.user.infrastructure.config"); context.refresh();
            assertEquals(1, context.getBeansOfType(MybatisPlusInterceptor.class).size(), "SQL pagination must be registered");
            var interceptor = context.getBean(MybatisPlusInterceptor.class);
            Configuration config = new Configuration();
            var sql = new StaticSqlSource(config, "SELECT id FROM shop ORDER BY id", List.of());
            var statement = new MappedStatement.Builder(config, "page", sql, SqlCommandType.SELECT).build();
            var params = Map.of("page", new Page<>(2, 10, false));
            BoundSql bound = sql.getBoundSql(params);
            for (var plugin : interceptor.getInterceptors()) plugin.beforeQuery(null, statement, params, RowBounds.DEFAULT, null, bound);
            assertTrue(bound.getSql().toUpperCase().contains("LIMIT"), bound.getSql());
            assertEquals(2, bound.getParameterMappings().size());
        }
    }
}
