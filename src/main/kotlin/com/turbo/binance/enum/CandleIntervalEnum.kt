package com.turbo.binance.enum

enum class CandleIntervalEnum {
    MINUTE_1,
    MINUTE_3,
    MINUTE_5,
    MINUTE_15,
    MINUTE_30,
    HOUR_1,
    HOUR_2,
    HOUR_4,
    HOUR_8,
    HOUR_12,
    DAY_1,
    DAY_3,
    WEEK_1,
    MONTH_1;

    override fun toString(): String {
        when {
            this == MINUTE_1 -> return "1m"
            this == MINUTE_3 -> return "3m"
            this == MINUTE_5 -> return "5m"
            this == MINUTE_15 -> return "15m"
            this == MINUTE_30 -> return "30m"
            this == HOUR_1 -> return "1h"
            this == HOUR_2 -> return "2h"
            this == HOUR_4 -> return "4h"
            this == HOUR_8 -> return "8h"
            this == HOUR_12 -> return "12h"
            this == DAY_1 -> return "1d"
            this == DAY_3 -> return "3d"
            this == WEEK_1 -> return "1w"
            this == MONTH_1 -> return "1M"
            else -> {
                return ""
            }
        }
    }
}
