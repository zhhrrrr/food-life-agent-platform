package com.foodlife.trade.domain.order.sequence;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TimestampOrderNoGeneratorTest {

    @Test
    void generateUniqueOrderNoUnderConcurrentRequestsFromSameUser() {
        TimestampOrderNoGenerator generator = new TimestampOrderNoGenerator();
        int requestCount = 10_000;

        Set<String> orderNos = ConcurrentHashMap.newKeySet();
        IntStream.range(0, requestCount)
                .parallel()
                .forEach(ignored -> orderNos.add(generator.generate(10001L)));

        assertThat(orderNos).hasSize(requestCount);
    }
}
