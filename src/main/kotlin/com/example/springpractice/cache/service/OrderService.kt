package com.example.springpractice.cache.service

import com.example.springpractice.cache.domain.Order
import com.example.springpractice.cache.repository.OrderRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class OrderService(
    private val orderRepository: OrderRepository
) {

    @Cacheable(value = ["ordersByUserId"], key = "#userId + #pageable.pageNumber + #pageable.pageSize")
    fun getOrdersByUserId(userId: Long, pageable: Pageable): Page<Order> {
        return orderRepository.findByUserId(userId, pageable)
    }

    @Transactional
    @CacheEvict(value = ["ordersByUserId"], key = "#order.userId", allEntries = true) // Invalidate user's order cache on new order
    fun createOrder(order: Order): Order {
        return orderRepository.save(order)
    }
}
