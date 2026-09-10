package com.erp.backend_service.configuration;

import com.erp.backend_service.service.NotificationRedisListener;
import com.erp.backend_service.util.RedisKeys;
import com.erp.core.dto.response.PageResponse;
import com.erp.core.dto.response.menu.ProductDetailResponse;
import com.erp.core.dto.response.menu.ProductSalesResponse;
import com.erp.core.dto.response.menu.ProductVariantResponse;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.data.redis.autoconfigure.LettuceClientOptionsBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Cấu hình Redis: template, cache manager cho @Cacheable, keepalive Lettuce.
 */
@Configuration
@EnableCaching
public class RedisConfiguration implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(RedisConfiguration.class);

    /** RedisTemplate chính dùng serializer JSON cho value, string cho key. */
    @Bean
    @Primary
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(RedisSerializer.json());
        template.setHashKeySerializer(RedisSerializer.string());
        template.setHashValueSerializer(RedisSerializer.json());
        template.afterPropertiesSet();
        return template;
    }

    /** StringRedisTemplate dùng cho các thao tác giá trị chuỗi (token, counter). */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Chống rớt kết nối idle giữa BE và Redis qua WAN: bật TCP keepalive để
     * firewall/NAT không lặng lẽ cắt kết nối Lettuce đang rảnh.
     */
    @Bean
    public LettuceClientOptionsBuilderCustomizer lettuceKeepAliveCustomizer() {
        return builder -> builder
                .socketOptions(SocketOptions.builder()
                        .keepAlive(true)
                        .connectTimeout(Duration.ofSeconds(8))
                        .build())
                .timeoutOptions(TimeoutOptions.enabled());
    }

    /**
     * CacheManager cho @Cacheable module product (menu đổi ít, đọc nhiều).
     * Mỗi cache khai báo kiểu value tường minh: serializer dùng chung
     * (GenericJackson/JDK) đều đã từng gây 500 — GenericJackson mất type-info
     * với collection rỗng, JDK đòi Serializable.
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Jackson 3 đã hỗ trợ sẵn Instant/record mà không cần module jsr310 rời.
        ObjectMapper objectMapper = new ObjectMapper();

        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues()
                .serializeKeysWith(SerializationPair.fromSerializer(RedisSerializer.string()))
                .serializeValuesWith(SerializationPair.fromSerializer(RedisSerializer.json()));

        Map<String, RedisCacheConfiguration> perCache = Map.of(
                "salesProducts", defaults.entryTtl(Duration.ofMinutes(5))
                        .serializeValuesWith(jsonValues(objectMapper,
                                objectMapper.getTypeFactory().constructParametricType(
                                        PageResponse.class, ProductSalesResponse.class))),
                "salesProductDetail", defaults.entryTtl(Duration.ofMinutes(2))
                        .serializeValuesWith(jsonValues(objectMapper, ProductDetailResponse.class)),
                "productDetail", defaults.entryTtl(Duration.ofMinutes(2))
                        .serializeValuesWith(jsonValues(objectMapper, ProductDetailResponse.class)),
                "productVariants", defaults.entryTtl(Duration.ofMinutes(2))
                        .serializeValuesWith(jsonValues(objectMapper,
                                objectMapper.getTypeFactory().constructCollectionType(
                                        List.class, ProductVariantResponse.class))));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(perCache)
                .transactionAware()
                .build();
    }

    private static <T> SerializationPair<T> jsonValues(ObjectMapper objectMapper, Class<T> type) {
        return SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(objectMapper, type));
    }

    private static <T> SerializationPair<T> jsonValues(ObjectMapper objectMapper, JavaType type) {
        JacksonJsonRedisSerializer<T> serializer = new JacksonJsonRedisSerializer<>(objectMapper, type);
        return SerializationPair.fromSerializer(serializer);
    }

    /**
     * Cache không bao giờ được làm sập API: lỗi Redis chỉ log warn rồi đọc
     * thẳng DB (coi như cache miss).
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Redis GET failed, fallback to DB. cache={} key={}: {}",
                        cache.getName(), key, e.toString());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Redis PUT failed, skip caching. cache={} key={}: {}",
                        cache.getName(), key, e.toString());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Redis EVICT failed, cache may be stale. cache={} key={}: {}",
                        cache.getName(), key, e.toString());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Redis CLEAR failed, cache may be stale. cache={}: {}",
                        cache.getName(), e.toString());
            }
        };
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
