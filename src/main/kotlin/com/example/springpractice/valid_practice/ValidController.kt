package com.example.springpractice.valid_practice

import com.example.springpractice.practice1.interfaces.TestResponse
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import javax.validation.GroupSequence
import javax.validation.Valid
import javax.validation.constraints.Email
import javax.validation.constraints.Size

@RestController
class ValidController {
    @PostMapping("/valid/test")
    fun test(@Validated(ValidationSequence::class) @RequestBody request: TestRequest): Boolean {
        return true
    }
}

data class TestRequest(
    @field:Email(
        groups = [EmailGroups.EmailPatternGroup::class],
        message = "이메일이 유효하지 않습니다."
    )
    @field:Size(
        max = 20,
        groups = [EmailGroups.EmailSizeGroup::class],
        message = "이메일 글자수가 초과했습니다."
    )
    val email: String
)

@GroupSequence(
    EmailGroups.EmailPatternGroup::class,
    EmailGroups.EmailSizeGroup::class
)
interface ValidationSequence

interface EmailGroups {
    interface EmailPatternGroup
    interface EmailSizeGroup
}