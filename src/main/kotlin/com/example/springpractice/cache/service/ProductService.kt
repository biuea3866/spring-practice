package com.example.springpractice.cache.service

import com.example.springpractice.cache.domain.NullValue
import com.example.springpractice.cache.domain.Product
import com.example.springpractice.cache.domain.ProductCategory
import com.example.springpractice.cache.metrics.CacheMetrics
import com.example.springpractice.cache.repository.ProductRepository
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit

@Service
@Transactional(readOnly = true)
class ProductService(
    private val productRepository: ProductRepository,
    private val cacheMetrics: CacheMetrics,
    private val cacheManager: CacheManager // Inject CacheManager
) {

    // Use a separate cache for null values with a shorter TTL
    private val nullValueCache = cacheManager.getCache("nullValues")

    @Cacheable(value = ["productById"], key = "#id")
    fun getProductById(id: Long): Product? {
        // Check null value cache first
        val cachedNull = nullValueCache?.get(id, NullValue::class.java)
        if (cachedNull != null) {
            cacheMetrics.incrementNullValueHit()
            return null // Return null if NullValue is cached
        }

        val product = productRepository.findById(id).orElse(null)
        if (product == null) {
            cacheMetrics.incrementDbQueryForNonExistentData()
            // Store NullValue in a dedicated cache with shorter TTL
            nullValueCache?.put(id, NullValue)
            cacheMetrics.incrementNullValueStored()
        }
        return product
    }

    @Cacheable(value = ["productsByCategory"], key = "#category.name + #pageable.pageNumber + #pageable.pageSize")
    fun getProductsByCategory(category: ProductCategory, pageable: Pageable): Page<Product> {
        return productRepository.findByCategory(category, pageable)
    }

    @Cacheable(value = ["allProducts"], key = "#pageable.pageNumber + #pageable.pageSize")
    fun getAllProducts(pageable: Pageable): Page<Product> {
        return productRepository.findAll(pageable)
    }

    @Transactional
    @CacheEvict(value = ["productById", "productsByCategory", "allProducts"], allEntries = true)
    fun createProduct(product: Product): Product {
        // Also evict from null value cache if a product is created with an ID that was previously null
        nullValueCache?.evict(product.id)
        return productRepository.save(product)
    }

    @Transactional
    @CacheEvict(value = ["productById", "productsByCategory", "allProducts"], key = "#id")
    fun updateProduct(id: Long, updatedProduct: Product): Product {
        val product = productRepository.findById(id).orElseThrow { NoSuchElementException("Product not found") }
        val newProduct = product.copy(
            name = updatedProduct.name,
            price = updatedProduct.price,
            stock = updatedProduct.stock,
            category = updatedProduct.category
        )
        return productRepository.save(newProduct)
    }
}
