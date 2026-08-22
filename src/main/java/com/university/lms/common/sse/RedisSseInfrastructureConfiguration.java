package com.university.lms.common.sse;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Enables Redis only when {@code lms.notifications.sse.provider=redis}. Base {@code
 * application.yml} excludes Redis auto-configuration so local single-node dev does not require a
 * running Redis instance.
 */
@Configuration
@ConditionalOnProperty(name = "lms.notifications.sse.provider", havingValue = "redis")
@ImportAutoConfiguration({RedisAutoConfiguration.class, RedisReactiveAutoConfiguration.class})
public class RedisSseInfrastructureConfiguration {

    @Bean
    RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }
}
