package com.example.springpractice.cache.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCache
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
@EnableCaching
class CacheConfig {

    @Bean
    fun cacheManager(): CacheManager {
        val cacheManager = SimpleCacheManager()
        cacheManager.setCaches(
            listOf(
                buildCache("productById", 60, 10000, false), // Do not allow nulls here, handled by nullValues cache
                buildCache("productsByCategory", 60, 100, true),
                buildCache("ordersByUserId", 60, 5000, true),
                buildCache("allProducts", 60, 10000, true),
                buildCache("nullValues", 10, 10000, true) // Dedicated cache for null values with shorter TTL
            )
        )
        return cacheManager
    }

    private fun buildCache(name: String, ttlSeconds: Long, maxSize: Long, allowNullValues: Boolean): CaffeineCache {
        return CaffeineCache(
            name,
            Caffeine.newBuilder()
                .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                .maximumSize(maxSize)
                .build(),
            allowNullValues
        )
    }
}
