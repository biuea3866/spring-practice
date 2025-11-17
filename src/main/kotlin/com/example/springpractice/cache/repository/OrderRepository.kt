package com.example.springpractice.cache.repository

import com.example.springpractice.cache.domain.Order
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface OrderRepository : JpaRepository<Order, Long> {
    fun findByUserId(userId: Long, pageable: Pageable): Page<Order>
}
