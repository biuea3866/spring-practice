package com.example.springpractice.cache.controller

import com.example.springpractice.cache.domain.Order
import com.example.springpractice.cache.service.OrderService
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService
) {

    @GetMapping("/user/{userId}")
    fun getOrdersByUserId(
        @PathVariable userId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<Page<Order>> {
        val pageable = PageRequest.of(page, size)
        val orders = orderService.getOrdersByUserId(userId, pageable)
        return ResponseEntity.ok(orders)
    }

    @PostMapping
    fun createOrder(@RequestBody order: Order): ResponseEntity<Order> {
        val createdOrder = orderService.createOrder(order)
        return ResponseEntity.ok(createdOrder)
    }
}
