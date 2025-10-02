package com.example.springpractice.practice1.interfaces

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

@RestController
@Controller
class TestController2 {
    @GetMapping("/index")
    fun getPage(): String {
        return "index"
    }

    @GetMapping("/test/object")
    @ResponseBody
    fun getObject(): Person {
        return Person(name = "asdasdasd", age = 2)
    }
}

data class Person(val name: String, val age: Int)