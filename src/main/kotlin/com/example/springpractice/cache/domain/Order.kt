package com.example.springpractice.cache.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "orders") // "Order" is a reserved keyword in some databases
data class Order(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
    val userId: Long,
    val productId: Long,
    val quantity: Int,
    val totalAmount: Long,
    @Enumerated(EnumType.STRING)
    val status: OrderStatus,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class OrderStatus {
    PENDING, PAID, CANCELLED
}
