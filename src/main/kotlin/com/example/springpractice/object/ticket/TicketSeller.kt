package com.example.springpractice.`object`.ticket

class TicketSeller(
    private var _ticketOffice: TicketOffice
) {
    val ticketOffice get() = this._ticketOffice
}