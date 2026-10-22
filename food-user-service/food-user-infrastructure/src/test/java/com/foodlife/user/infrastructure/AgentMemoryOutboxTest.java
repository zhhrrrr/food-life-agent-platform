package com.foodlife.user.infrastructure;

import com.foodlife.user.infrastructure.dao.IAgentMemoryMapper;
import com.foodlife.user.infrastructure.dao.po.UserMemoryLocalMessagePO;
import com.foodlife.user.infrastructure.mq.AgentMemoryMqProperties;
import com.foodlife.user.infrastructure.mq.AgentMemoryOutboxPublisher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentMemoryOutboxTest {
    @ParameterizedTest @ValueSource(booleans={true,false})
    void marksSuccessOnlyAfterBrokerAcknowledges(boolean acknowledged) {
        IAgentMemoryMapper mapper=mock(IAgentMemoryMapper.class); RabbitTemplate rabbit=mock(RabbitTemplate.class);
        UserMemoryLocalMessagePO message=new UserMemoryLocalMessagePO(); message.setId(1L); message.setMessageId("m");
        message.setEventType("UPSERT"); message.setContent("{}"); message.setMessageStatus("INIT");
        when(mapper.findOutbox("m")).thenReturn(message); when(mapper.markOutboxProcessing(1L)).thenReturn(1);
        doAnswer(a -> { CorrelationData data=a.getArgument(3); data.getFuture().complete(new CorrelationData.Confirm(acknowledged,"test")); return null; })
                .when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
        new AgentMemoryOutboxPublisher(mapper,new AgentMemoryMqProperties(),rabbit,
                mock(org.springframework.transaction.PlatformTransactionManager.class)).publishAfterCommit("m");
        if (acknowledged) verify(mapper).markOutboxSuccess(1L);
        else { verify(mapper,never()).markOutboxSuccess(anyLong()); verify(mapper).markOutboxFailed(eq(1L),eq("INIT"),any(),anyString()); }
    }
}
