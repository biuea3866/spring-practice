package com.example.springpractice.`object`.ticket

class TicketOffice(
    private var _amount: Long,
    private val _tickets: MutableList<Ticket>
) {
    fun getTicket(): Ticket {
        return this._tickets.removeFirst()
    }

    fun minusAmount(amount: Long) {
        this._amount -= amount
    }

    fun plusAmount(amount: Long) {
        this._amount += amount
    }
}