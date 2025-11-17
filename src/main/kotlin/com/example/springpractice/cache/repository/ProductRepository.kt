package com.example.springpractice.cache.repository

import com.example.springpractice.cache.domain.Product
import com.example.springpractice.cache.domain.ProductCategory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ProductRepository : JpaRepository<Product, Long> {
    fun findByCategory(category: ProductCategory, pageable: Pageable): Page<Product>
}
