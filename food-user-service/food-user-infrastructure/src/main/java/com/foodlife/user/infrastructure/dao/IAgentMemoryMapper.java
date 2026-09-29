package com.foodlife.user.infrastructure.dao;

import com.foodlife.user.infrastructure.dao.po.AgentMemoryEventPO;
import com.foodlife.user.infrastructure.dao.po.AgentMessagePO;
import com.foodlife.user.infrastructure.dao.po.AgentSessionPO;
import com.foodlife.user.infrastructure.dao.po.AgentUserMemoryPO;
import com.foodlife.user.infrastructure.dao.po.UserMemoryLocalMessagePO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface IAgentMemoryMapper {
    @Insert("""
        INSERT INTO agent_session(session_id,user_id,summary_through_seq,version,status)
        VALUES(#{sessionId},#{userId},0,0,1)
        ON DUPLICATE KEY UPDATE update_time=update_time
        """)
    int ensureSession(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    @Select("SELECT * FROM agent_session WHERE user_id=#{userId} AND session_id=#{sessionId} AND status=1 FOR UPDATE")
    AgentSessionPO lockSession(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    @Select("SELECT * FROM agent_session WHERE user_id=#{userId} AND session_id=#{sessionId} AND status=1")
    AgentSessionPO findSession(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    @Select("SELECT COUNT(1) FROM agent_session_message WHERE user_id=#{userId} AND session_id=#{sessionId} AND turn_id=#{turnId}")
    int countTurn(@Param("userId") Long userId, @Param("sessionId") String sessionId, @Param("turnId") String turnId);

    @Insert("""
        INSERT INTO agent_session_message(session_id,user_id,turn_id,sequence_no,role,content,intent,run_id)
        VALUES(#{sessionId},#{userId},#{turnId},#{sequenceNo},#{role},#{content},#{intent},#{runId})
        """)
    int insertMessage(AgentMessagePO message);

    @Update("UPDATE agent_session SET version=version+1,update_time=NOW() WHERE user_id=#{userId} AND session_id=#{sessionId}")
    int bumpSessionVersion(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    @Select("""
        SELECT * FROM agent_session_message WHERE user_id=#{userId} AND session_id=#{sessionId}
        ORDER BY sequence_no DESC LIMIT #{messageLimit}
        """)
    List<AgentMessagePO> findRecentMessages(@Param("userId") Long userId,
                                            @Param("sessionId") String sessionId,
                                            @Param("messageLimit") int messageLimit);

    @Update("""
        UPDATE agent_session SET summary=#{summary},summary_through_seq=#{summaryThroughSeq},version=version+1
        WHERE user_id=#{userId} AND session_id=#{sessionId} AND version=#{expectedVersion}
          AND summary_through_seq<=#{summaryThroughSeq}
        """)
    int updateSummary(@Param("userId") Long userId, @Param("sessionId") String sessionId,
                      @Param("summary") String summary, @Param("summaryThroughSeq") long summaryThroughSeq,
                      @Param("expectedVersion") long expectedVersion);

    @Select("SELECT * FROM agent_user_memory WHERE user_id=#{userId} AND status=1 ORDER BY memory_type,memory_key")
    List<AgentUserMemoryPO> findUserMemories(@Param("userId") Long userId);

    @Insert("""
        INSERT INTO agent_user_memory(user_id,memory_type,memory_key,value_json,source_turn_id,version,status)
        VALUES(#{userId},#{memoryType},#{memoryKey},CAST(#{valueJson} AS JSON),#{sourceTurnId},0,1)
        ON DUPLICATE KEY UPDATE value_json=VALUES(value_json),source_turn_id=VALUES(source_turn_id),
          version=version+1,status=1,update_time=NOW()
        """)
    int upsertUserMemory(AgentUserMemoryPO memory);

    @Update("UPDATE agent_user_memory SET status=0,version=version+1 WHERE user_id=#{userId} AND memory_type=#{memoryType} AND memory_key=#{memoryKey}")
    int deleteUserMemory(@Param("userId") Long userId, @Param("memoryType") String memoryType,
                         @Param("memoryKey") String memoryKey);

    @Insert("""
        INSERT INTO agent_user_memory_event(event_id,user_id,event_type,content,entity_type,entity_id,
          source_turn_id,status,occurred_at,expires_at)
        VALUES(#{eventId},#{userId},#{eventType},#{content},#{entityType},#{entityId},
          #{sourceTurnId},#{status},#{occurredAt},#{expiresAt})
        ON DUPLICATE KEY UPDATE content=VALUES(content),entity_type=VALUES(entity_type),
          entity_id=VALUES(entity_id),source_turn_id=VALUES(source_turn_id),status='ACTIVE',
          occurred_at=VALUES(occurred_at),expires_at=VALUES(expires_at),update_time=NOW()
        """)
    int upsertEvent(AgentMemoryEventPO event);

    @Select("SELECT * FROM agent_user_memory_event WHERE event_id=#{eventId} AND user_id=#{userId}")
    AgentMemoryEventPO findEvent(@Param("userId") Long userId, @Param("eventId") String eventId);

    @Update("UPDATE agent_user_memory_event SET status='DELETED',update_time=NOW() WHERE user_id=#{userId} AND event_id=#{eventId}")
    int deleteEvent(@Param("userId") Long userId, @Param("eventId") String eventId);

    @Select("SELECT * FROM agent_user_memory_event WHERE status='ACTIVE' AND expires_at<=NOW() ORDER BY id LIMIT #{limit}")
    List<AgentMemoryEventPO> findExpiredEvents(@Param("limit") int limit);

    @Select("SELECT * FROM agent_user_memory_event WHERE status='ACTIVE' AND expires_at>NOW() AND id>#{afterId} ORDER BY id LIMIT #{limit}")
    List<AgentMemoryEventPO> findActiveEvents(@Param("afterId") long afterId, @Param("limit") int limit);

    @Update("UPDATE agent_user_memory_event SET status='EXPIRED',update_time=NOW() WHERE id=#{id} AND status='ACTIVE'")
    int expireEvent(@Param("id") Long id);

    @Insert("""
        INSERT INTO user_memory_local_message(message_id,event_type,event_id,content,message_status,
          retry_count,max_retry_count,next_retry_time)
        VALUES(#{messageId},#{eventType},#{eventId},CAST(#{content} AS JSON),'INIT',0,8,NOW())
        """)
    int insertOutbox(UserMemoryLocalMessagePO message);

    @Select("SELECT * FROM user_memory_local_message WHERE message_id=#{messageId}")
    UserMemoryLocalMessagePO findOutbox(@Param("messageId") String messageId);

    @Select("""
        SELECT * FROM user_memory_local_message WHERE message_status='INIT' AND next_retry_time<=NOW()
        ORDER BY id LIMIT #{limit}
        """)
    List<UserMemoryLocalMessagePO> findPendingOutbox(@Param("limit") int limit);

    @Update("UPDATE user_memory_local_message SET message_status='PROCESSING',update_time=NOW() WHERE id=#{id} AND message_status='INIT'")
    int markOutboxProcessing(@Param("id") Long id);

    @Update("UPDATE user_memory_local_message SET message_status='SUCCESS',fail_reason=NULL,update_time=NOW() WHERE id=#{id}")
    int markOutboxSuccess(@Param("id") Long id);

    @Update("""
        UPDATE user_memory_local_message SET message_status=#{status},retry_count=retry_count+1,
          next_retry_time=#{nextRetryTime},fail_reason=#{reason},update_time=NOW() WHERE id=#{id}
        """)
    int markOutboxFailed(@Param("id") Long id, @Param("status") String status,
                         @Param("nextRetryTime") LocalDateTime nextRetryTime, @Param("reason") String reason);

    @Update("""
        UPDATE user_memory_local_message SET message_status='INIT',next_retry_time=NOW(),update_time=NOW()
        WHERE message_status='PROCESSING' AND update_time<#{before}
        """)
    int recoverOutbox(@Param("before") LocalDateTime before);
}
