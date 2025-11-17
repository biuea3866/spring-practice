package com.example.springpractice.cache.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

@Component
class CacheMetrics(meterRegistry: MeterRegistry) {

    val cacheNullValueHitTotal: Counter = Counter.builder("cache_null_value_hit_total")
        .description("Total number of times a null value was hit in cache (Cache Penetration prevented)")
        .register(meterRegistry)

    val cacheNullValueStoredTotal: Counter = Counter.builder("cache_null_value_stored_total")
        .description("Total number of times a null value was stored in cache")
        .register(meterRegistry)

    val dbQueryForNonExistentDataTotal: Counter = Counter.builder("db_query_for_nonexistent_data_total")
        .description("Total number of DB queries for non-existent data")
        .register(meterRegistry)

    fun incrementNullValueHit() {
        cacheNullValueHitTotal.increment()
    }

    fun incrementNullValueStored() {
        cacheNullValueStoredTotal.increment()
    }

    fun incrementDbQueryForNonExistentData() {
        dbQueryForNonExistentDataTotal.increment()
    }
}
