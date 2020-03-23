package com.turbo

import org.apache.commons.math3.fraction.BigFraction
import org.apache.commons.math3.fraction.Fraction
import org.joda.time.DateTimeUtils.getZone
import java.util.TimeZone
import org.joda.time.DateTimeZone
import java.math.BigDecimal


fun java.time.ZonedDateTime.toJodaDateTime(): org.joda.time.DateTime {
  return org.joda.time.DateTime(
      this.toInstant().toEpochMilli(), DateTimeZone.forTimeZone(TimeZone.getTimeZone(this.zone)))
}

fun org.joda.time.DateTime.toJSR310LocalDateTime(): java.time.ZonedDateTime {

  return java.time.ZonedDateTime.ofInstant(
      java.time.Instant.ofEpochMilli(this.millis), java.time.ZoneId.of(this.zone.id)
  )
}

fun BigFraction.multiply(decimal: BigDecimal, scale: Int = 8, roundingMode: Int = BigDecimal.ROUND_DOWN): BigDecimal {
  return this.bigDecimalValue(scale, roundingMode).multiply(decimal).setScale(scale, roundingMode)
}

fun Fraction.multiply(decimal: BigDecimal, scale: Int = 8, roundingMode: Int = BigDecimal.ROUND_DOWN): BigDecimal {
  return BigFraction(this.numerator, this.denominator).bigDecimalValue(scale, roundingMode).multiply(decimal).setScale(scale, roundingMode)
}
