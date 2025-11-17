package com.example.springpractice.cache.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
data class Product(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
    val name: String,
    val price: Long,
    val stock: Int,
    @Enumerated(EnumType.STRING)
    val category: ProductCategory,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class ProductCategory {
    ELECTRONICS, FASHION, FOOD, BOOK
}
