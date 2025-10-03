package com.example.springpractice.`object`.ticket

class Audience(
    private var _bag: Bag
) {
    val bag: Bag get() = this._bag
}