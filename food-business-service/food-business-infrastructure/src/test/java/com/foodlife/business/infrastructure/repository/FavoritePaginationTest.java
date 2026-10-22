package com.foodlife.business.infrastructure.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.foodlife.business.infrastructure.dao.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FavoritePaginationTest {
    @Test void offlineShopsDoNotConsumePageSlotsOrHideOlderFavorites() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE shop(id BIGINT PRIMARY KEY,name VARCHAR(64),category_id BIGINT,images VARCHAR(64),area VARCHAR(64),address VARCHAR(64),longitude DOUBLE,latitude DOUBLE,avg_price BIGINT,sold INT,comments INT,score INT,open_hours VARCHAR(64),status INT,create_time TIMESTAMP,update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE shop_favorite(id BIGINT PRIMARY KEY,user_id BIGINT,shop_id BIGINT,favorite_status INT,create_time TIMESTAMP,update_time TIMESTAMP)");
        jdbc.update("INSERT INTO shop(id,status) VALUES(1,1),(2,1),(3,0)");
        jdbc.update("INSERT INTO shop_favorite(id,user_id,shop_id,favorite_status) VALUES(1,7,1,1),(2,7,2,1),(3,7,3,1)");
        MybatisConfiguration config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
        config.addInterceptor(new com.foodlife.business.infrastructure.config.MybatisConfiguration().mybatisPlusInterceptor());
        config.addMapper(IShopFavoriteMapper.class); config.addMapper(IShopMapper.class);
        try (var session = new MybatisSqlSessionFactoryBuilder().build(config).openSession()) {
            var repository = new ShopFavoriteRepository(session.getMapper(IShopFavoriteMapper.class), session.getMapper(IShopMapper.class));
            var result = repository.listFavoriteShops(7L, null, 2);
            assertEquals(2, result.size()); assertEquals(1L, result.get(1).getShopId());
        }
    }
}
