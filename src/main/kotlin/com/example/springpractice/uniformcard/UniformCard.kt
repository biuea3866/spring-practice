package com.example.springpractice.uniformcard

class UniformCard(
    val id: Long,
    private var owner: User
) {
}

class User {
    // 트레이스 시장으로부터 유니폼 카드를 검색한다.
    fun searchUniformCardBy(
        keyword: String,
        tradeSystem: TradeSystem
    ): UniformCard {

    }

    // 구매자에게 트레이드시장에서 유니폼 카드를 판다.
    fun sellTo(
        uniformCard: UniformCard,
        tradeSystem: TradeSystem
    ) {

    }

    // 판매자로부터 트레이드시장에서 유니폼 카드를 산다.
    fun buy(
        uniformCard: UniformCard,
        tradeSystem: TradeSystem
    ) {

    }
}

class TradeSystem {

}

fun main() {
    // 유저는 트레이드 시장 시스템에서 유니폼 카드를 산다.
    // 유저는 트레이드 시장 시스템에서 유니폼 카드를 판다.
}