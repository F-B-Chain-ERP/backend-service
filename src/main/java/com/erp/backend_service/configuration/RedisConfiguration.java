package com.erp.backend_service.configuration;

import com.erp.backend_service.service.NotificationRedisListener;
import com.erp.backend_service.util.RedisKeys;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Cấu hình các bean truy cập Redis: RedisTemplate (JSON) và StringRedisTemplate.
 */
@Configuration
@EnableCaching
public class RedisConfiguration {

    /** RedisTemplate chính dùng serializer JSON cho value, string cho key. */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        RedisSerializer<String> stringSerializer = RedisSerializer.string();
        RedisSerializer<Object> jsonSerializer = RedisSerializer.json();

        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();

        return template;
    }

    /** StringRedisTemplate dùng cho các thao tác giá trị chuỗi (token, counter). */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /** Container lắng nghe các kênh Redis Pub/Sub thông báo realtime (pattern "notification:*"). */
    @Bean
    public RedisMessageListenerContainer notificationListenerContainer(
            RedisConnectionFactory connectionFactory,
            NotificationRedisListener notificationRedisListener
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                notificationRedisListener,
                new PatternTopic(RedisKeys.NOTIFICATION_CHANNEL_PREFIX + "*")
        );
        return container;
    }
}
