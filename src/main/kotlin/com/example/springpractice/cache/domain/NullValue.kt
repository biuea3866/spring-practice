package com.example.springpractice.cache.domain

import java.io.Serializable

/**
 * Represents a null value for caching purposes, allowing explicit caching of nulls.
 * This is used to prevent cache penetration by storing a placeholder for non-existent data.
 */
object NullValue : Serializable {
    private const val serialVersionUID: Long = 1L
}
