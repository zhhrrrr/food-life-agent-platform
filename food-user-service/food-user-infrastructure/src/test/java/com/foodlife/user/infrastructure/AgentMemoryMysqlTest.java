package com.foodlife.user.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodlife.user.domain.memory.model.UserMemory;
import com.foodlife.user.infrastructure.dao.IAgentMemoryMapper;
import com.foodlife.user.infrastructure.mq.AgentMemoryOutboxPublisher;
import com.foodlife.user.infrastructure.repository.AgentMemoryRepository;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@EnabledIfEnvironmentVariable(named="TEST_MYSQL_URL", matches=".+")
class AgentMemoryMysqlTest {
    private static String schema;
    private static SqlSessionFactory factory;
    private static PooledDataSource dataSource;

    @BeforeAll static void initialize() throws Exception {
        schema="memory_test_" + UUID.randomUUID().toString().replace("-","").substring(0,16);
        String base=System.getenv("TEST_MYSQL_URL"), user=System.getenv("TEST_MYSQL_USER"), password=System.getenv("TEST_MYSQL_PASSWORD");
        try (var db=DriverManager.getConnection(base,user,password); var sql=db.createStatement()) {
            sql.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4");
            sql.execute("USE " + schema);
            for (String file : new String[]{"V3__add_agent_memory_schema.sql","V4__memory_consistency.sql","V5__session_entity_context.sql"})
                for (String statement : Files.readString(Path.of("../food-user-app/src/main/resources/db/migration",file)).split(";"))
                    if (!statement.isBlank()) sql.execute(statement);
        }
        String url=base.replaceFirst("/[^/?]+(?=\\?|$)","/"+schema);
        dataSource=new PooledDataSource("com.mysql.cj.jdbc.Driver",url,user,password);
        Configuration config=new Configuration(new Environment("test",new JdbcTransactionFactory(),dataSource));
        config.setMapUnderscoreToCamelCase(true); config.addMapper(IAgentMemoryMapper.class);
        factory=new SqlSessionFactoryBuilder().build(config);
    }

    @AfterAll static void cleanup() throws Exception {
        if (dataSource != null) dataSource.forceCloseAll();
        if (schema != null && schema.matches("memory_test_[a-f0-9]{16}"))
            try (var db=DriverManager.getConnection(System.getenv("TEST_MYSQL_URL"),System.getenv("TEST_MYSQL_USER"),System.getenv("TEST_MYSQL_PASSWORD")); var sql=db.createStatement()) {
                sql.execute("DROP DATABASE " + schema);
            }
    }

    @Test void migrationsMapperAndProfileLifecycleWorkTogether() {
        try (SqlSession session=factory.openSession()) {
            IAgentMemoryMapper mapper=session.getMapper(IAgentMemoryMapper.class);
            AgentMemoryRepository repository=new AgentMemoryRepository(mapper,new ObjectMapper().findAndRegisterModules(),mock(AgentMemoryOutboxPublisher.class));
            repository.appendTurn(7L,"s","t1","我对花生过敏","已记录","recommend","r1",8);
            repository.upsertUserMemory(7L,new UserMemory("food_preference","allergy","{\"values\":[\"花生\"],\"operation\":\"add\"}","t1",-1));
            repository.upsertUserMemory(7L,new UserMemory("food_preference","allergy","{\"values\":[\"海鲜\"],\"operation\":\"add\"}","t2",-1));
            session.commit(); // InnoDB full-text changes become searchable after commit.
            assertTrue(repository.findUserMemories(7L).get(0).valueJson().contains("花生"));
            assertTrue(repository.findUserMemories(7L).get(0).valueJson().contains("海鲜"));
            var events=repository.searchSemanticEvents(7L,"海鲜",null,10);
            assertEquals(1,events.size()); assertTrue(events.get(0).version() > 1);
            assertTrue(repository.searchSemanticEvents(8L,"海鲜",null,10).isEmpty());
            repository.deleteUserMemory(7L,"food_preference","allergy");
            session.commit();
            assertTrue(repository.findUserMemories(7L).isEmpty());
            assertTrue(repository.searchSemanticEvents(7L,"海鲜",null,10).isEmpty());
            assertEquals("DELETED",repository.findSemanticEvent(7L,events.get(0).eventId()).status());
            assertEquals(1,repository.findPendingSummary(7L,"s").turns().size());
            assertTrue(repository.updateSummary(7L,"s","summary",2,1));
            assertFalse(repository.updateSummary(7L,"s","stale",2,1));
            session.rollback();
        }
    }
}
