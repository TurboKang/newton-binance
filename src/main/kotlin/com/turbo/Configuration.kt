package com.turbo

import org.joda.time.DateTimeUtils.getZone
import java.util.TimeZone
import org.joda.time.DateTimeZone


fun java.time.ZonedDateTime.toJodaDateTime(): org.joda.time.DateTime {
  return org.joda.time.DateTime(
      this.toInstant().toEpochMilli(), DateTimeZone.forTimeZone(TimeZone.getTimeZone(this.zone)))
}

fun org.joda.time.DateTime.toJSR310LocalDateTime(): java.time.ZonedDateTime {

  return java.time.ZonedDateTime.ofInstant(
      java.time.Instant.ofEpochMilli(this.millis), java.time.ZoneId.of(this.zone.id)
  )
}
