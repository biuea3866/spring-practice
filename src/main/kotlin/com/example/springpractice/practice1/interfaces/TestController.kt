package com.example.springpractice.practice1.interfaces

import com.fasterxml.jackson.annotation.JsonFormat
import org.apache.tomcat.util.threads.ThreadPoolExecutor
import org.slf4j.LoggerFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalDateTime
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import kotlin.system.measureTimeMillis
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@RestController
class TestController(
    private val repository: Repository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/test")
    fun test(
        @RequestBody body: TestBody,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") date: LocalDateTime,
    ): TestResponse {
        println(body)
        println(date)

        return TestResponse(name = body.name, age = body.age)
    }

    @OptIn(ExperimentalTime::class)
    @GetMapping("/test1")
    fun test1(): ResponseEntity<Unit> {
        val times = measureTimeMillis {
            Thread.sleep(4000)
        }

        logger.info("활성화 스레드 갯수: ${Thread.activeCount()}, 소요시간 : $times ms")

        return ResponseEntity(HttpStatus.OK)
    }

    @GetMapping("/test2")
    fun test2(): ResponseEntity<Unit> {
        logger.info("활성화 스레드 갯수: ${Thread.activeCount()}")
        Thread.sleep(2000)
        return ResponseEntity(HttpStatus.OK)
    }

    @GetMapping("/test3")
    fun test3(): ResponseEntity<Unit> {
        logger.info("활성화 스레드 갯수: ${Thread.activeCount()}")
        Thread.sleep(1000)
        return ResponseEntity(HttpStatus.OK)
    }

    @GetMapping("/test4")
    fun test4(): ResponseEntity<Unit> {
        logger.info("활성화 스레드 갯수: ${Thread.activeCount()}")
        Thread.sleep(300)
        return ResponseEntity(HttpStatus.OK)
    }

    @GetMapping("/test5")
    fun test5(): ResponseEntity<Unit> {
        logger.info("활성화 스레드 갯수: ${Thread.activeCount()}")
        Thread.sleep(100)
        return ResponseEntity(HttpStatus.OK)
    }
}

data class TestBody(
    val name: String,
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy년 MM월 dd일 HH시 mm분 ss초")
    val age: LocalDateTime,
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy년 MM월 dd일 HH시 mm분 ss초")
    val createdAt: LocalDateTime
)

data class TestResponse(
    val name: String,
    @JsonFormat(pattern = "yyyy-MM-dd")
    val age: LocalDateTime
)

@Entity
@Table(name = "test_entity")
class TestEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long
)

interface Repository: JpaRepository<TestEntity, Long> {}