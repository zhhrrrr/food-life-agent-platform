package com.foodlife.user.infrastructure;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.foodlife.user.infrastructure.dao.*;
import com.foodlife.user.infrastructure.repository.UserFollowRepository;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FollowPaginationTest {
    private SqlSession session;
    private JdbcTemplate jdbc;
    private UserFollowRepository repository;
    @BeforeEach void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE user(id BIGINT PRIMARY KEY,phone VARCHAR(32),password VARCHAR(64),nick_name VARCHAR(64),icon VARCHAR(64),status INT,create_time TIMESTAMP,update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE user_follow(id BIGINT PRIMARY KEY,user_id BIGINT,follow_user_id BIGINT,follow_status INT,create_time TIMESTAMP,update_time TIMESTAMP)");
        jdbc.update("INSERT INTO user(id,status) VALUES(1,1),(2,1),(3,0)");
        jdbc.update("INSERT INTO user_follow(id,user_id,follow_user_id,follow_status) VALUES(1,7,1,1),(2,7,2,1),(3,7,3,1),(4,2,7,1),(5,3,7,1),(6,1,7,1)");
        MybatisConfiguration config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
        config.addInterceptor(new com.foodlife.user.infrastructure.config.MybatisConfiguration().mybatisPlusInterceptor());
        config.addMapper(IUserFollowMapper.class); config.addMapper(IUserMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(config).openSession();
        repository = new UserFollowRepository(session.getMapper(IUserFollowMapper.class), session.getMapper(IUserMapper.class));
    }
    @AfterEach void close() { session.close(); }

    @Test void inactiveTargetsDoNotConsumeFollowingOrFanPageSlots() {
        assertEquals(2, repository.listFollowingUsers(7L, null, 2).size());
        assertEquals(2, repository.listFans(7L, null, 2).size());
    }

    @Test void commonFollowingDoesNotMissMatchesBeyondAnArbitraryRecentWindow() {
        for (int id=100; id<250; id++) {
            jdbc.update("INSERT INTO user(id,status) VALUES(?,1)", id);
            jdbc.update("INSERT INTO user_follow(id,user_id,follow_user_id,follow_status) VALUES(?,7,?,1)", id, id);
        }
        jdbc.update("INSERT INTO user_follow(id,user_id,follow_user_id,follow_status) VALUES(1000,8,1,1)");
        var result = repository.listCommonFollowUsers(7L, 8L, 2);
        assertEquals(1, result.size()); assertEquals(1L, result.get(0).getUserId());
    }
}
