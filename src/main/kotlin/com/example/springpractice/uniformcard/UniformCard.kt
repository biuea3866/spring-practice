package com.example.springpractice.uniformcard

abstract class UniformCard(
    val id: Long,
    private val grade: String,
    private val playerName: String,
    private val club: String,
    private val season: String,
    private val overall: Int,
    private val attackStat: Int,
    private val passingStat: Int,
    private val defenseStat: Int,
    private val goalKeepingStat: Int,
    private val speciality: String
) {
    fun isContains(keyword: String): Boolean {
        return this.playerName.contains(keyword, ignoreCase = true)
    }

    fun match(season: String): Boolean {
        return this.season.equals(season, ignoreCase = true)
    }
}

class UniformCardOwner(
    private var owner: User,
    private val uniformCard: UniformCard
) {
    fun change(buyer: User) {
        this.owner = buyer
    }

    fun match(owner: User, uniformCard: UniformCard): Boolean {
        return this.owner == owner && this.uniformCard == uniformCard
    }
}

class UniformCardDashboard(
    private val uniformCard: UniformCard,
    private val rows: MutableList<UniformCardDashboardRow>
) {
    // 특정 키워드를 만족하는 선수 카드가 있는지 확인한다.
    // 내부 uniformCard.playerName은 포인터의 포인터이므로 디미터 법칙 위반
//    fun isContains(keyword: String): Boolean {
//        return this.uniformCard.playerName.contains(keyword, ignoreCase = true)
//    }

    fun isContains(keyword: String): Boolean {
        return this.uniformCard.isContains(keyword)
    }
}

class UniformCardDashboardRow(
    private val grade: String,
    private val unformCardOwnerForSelling: MutableList<UniformCardOwner>,
    private val currentSellAmount: Long,
    private val uniformCardBuyers: List<User>,
    private val currentBuyAmount: Long,
) {
    fun calculate(): Long {
        return this.currentSellAmount
    }

    fun decreaseCard(uniformCardOwner: UniformCardOwner) {
        this.unformCardOwnerForSelling.remove(uniformCardOwner)
    }
}

class User(
    private val id: Long,
    private var bp: Long,
    private val myUniformCards: List<UniformCardOwner>
) {
    // 구매자에게 트레이드시장에서 유니폼 카드를 판다.
    fun sellTo(
        user: User,
        uniformCard: UniformCard,
        tradeSystem: TradeSystem
    ) {
        val uniformCardForSelling = this.myUniformCards.find { it.match(this, uniformCard) }
            ?: throw IllegalArgumentException()
        uniformCardForSelling.change(user)
        user.decreaseBp()
    }

    // 판매자로부터 트레이드시장에서 유니폼 카드를 산다.
    fun buy(
        uniformCard: UniformCard,
        tradeSystem: TradeSystem
    ) {

    }

    fun increaseBp(bp: Long) {
        this.bp += bp
    }

    fun decreaseBp(bp: Long) {
        this.bp -= bp
    }
}

class TradeSystem(
    private val dashboards: List<UniformCardDashboard>
) {
    fun searchBy(keyword: String): List<UniformCardDashboard> {
        return this.dashboards.filter { it.isContains(keyword) }
    }

    fun searchSpecificBy(season: String, keyword: String): UniformCardDashboard {
        return this.searchBy(keyword).find {  }
    }
}

fun main() {
    // 유저는 트레이드 시장 시스템에서 유니폼 카드를 산다.
    // 유저는 트레이드 시장 시스템에서 유니폼 카드를 판다.
}