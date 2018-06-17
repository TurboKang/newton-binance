package com.turbo.binance.model

import java.math.BigDecimal
import java.time.ZonedDateTime

data class AccountInfo(
        val makerCommission: Int,
        val takerCommission: Int,
        val buyerCommission: Int,
        val sellerCommission: Int,
        val canTrade: Boolean,
        val canWithdraw: Boolean,
        val canDeposit: Boolean,
        val updateTime: ZonedDateTime,
        val balances: List<AssetBalance>
) {
    data class AssetBalance(
            val asset: String,
            val free: BigDecimal,
            val locked: BigDecimal
    )
}