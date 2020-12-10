/*
 * Copyright 2019-2020 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.datetime

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlin.time.*

object DateTimePeriodComponentSerializer: KSerializer<DateTimePeriod> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("DateTimePeriod") {
            element<Int>("years", isOptional = true)
            element<Int>("months", isOptional = true)
            element<Int>("days", isOptional = true)
            element<Int>("hours", isOptional = true)
            element<Int>("minutes", isOptional = true)
            element<Long>("seconds", isOptional = true)
            element<Long>("nanoseconds", isOptional = true)
        }

    override fun deserialize(decoder: Decoder): DateTimePeriod =
        decoder.decodeStructure(descriptor) {
            var years = 0
            var months = 0
            var days = 0
            var hours = 0
            var minutes = 0
            var seconds = 0L
            var nanoseconds = 0L
            loop@while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> years = decodeIntElement(descriptor, 0)
                    1 -> months = decodeIntElement(descriptor, 1)
                    2 -> days = decodeIntElement(descriptor, 2)
                    3 -> hours = decodeIntElement(descriptor, 3)
                    4 -> minutes = decodeIntElement(descriptor, 4)
                    5 -> seconds = decodeLongElement(descriptor, 5)
                    6 -> nanoseconds = decodeLongElement(descriptor, 6)
                    CompositeDecoder.DECODE_DONE -> break@loop // https://youtrack.jetbrains.com/issue/KT-42262
                    else -> error("Unexpected index: $index")
                }
            }
            DateTimePeriod(years, months, days, hours, minutes, seconds, nanoseconds)
        }

    override fun serialize(encoder: Encoder, value: DateTimePeriod) {
        encoder.encodeStructure(descriptor) {
            with(value) {
                if (years != 0) encodeIntElement(descriptor, 0, years)
                if (months != 0) encodeIntElement(descriptor, 1, months)
                if (days != 0) encodeIntElement(descriptor, 2, days)
                if (hours != 0) encodeIntElement(descriptor, 3, hours)
                if (minutes != 0) encodeIntElement(descriptor, 4, minutes)
                if (seconds != 0L) encodeLongElement(descriptor, 5, seconds)
                if (nanoseconds != 0L) encodeLongElement(descriptor, 6, value.nanoseconds)
            }
        }
    }

}

object DateTimePeriodISO8601Serializer: KSerializer<DateTimePeriod> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("DateTimePeriod", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): DateTimePeriod =
        DateTimePeriod.parse(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: DateTimePeriod) {
        encoder.encodeString(value.toString())
    }

}

@Serializable(with = DateTimePeriodISO8601Serializer::class)
// TODO: could be error-prone without explicitly named params
sealed class DateTimePeriod {
    abstract val years: Int
    abstract val months: Int
    abstract val days: Int
    abstract val hours: Int
    abstract val minutes: Int
    abstract val seconds: Long
    abstract val nanoseconds: Long

    private fun allNotPositive() =
            years <= 0 && months <= 0 && days <= 0 && hours <= 0 && minutes <= 0 && seconds <= 0 && nanoseconds <= 0 &&
            (years or months or days or hours or minutes != 0 || seconds or nanoseconds != 0L)

    override fun toString(): String = buildString {
        val sign = if (allNotPositive()) { append('-'); -1 } else 1
        append('P')
        if (years != 0) append(years * sign).append('Y')
        if (months != 0) append(months * sign).append('M')
        if (days != 0) append(days * sign).append('D')
        var t = "T"
        if (hours != 0) append(t).append(hours * sign).append('H').also { t = "" }
        if (minutes != 0) append(t).append(minutes * sign).append('M').also { t = "" }
        if (seconds != 0L || nanoseconds != 0L) {
            append(t).append(seconds * sign)
            if (nanoseconds != 0L) append('.').append((nanoseconds * sign).toString().padStart(9, '0'))
            append('S')
        }

        if (length == 1) append("0D")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DateTimePeriod) return false

        if (years != other.years) return false
        if (months != other.months) return false
        if (days != other.days) return false
        if (hours != other.hours) return false
        if (minutes != other.minutes) return false
        if (seconds != other.seconds) return false
        if (nanoseconds != other.nanoseconds) return false

        return true
    }

    override fun hashCode(): Int {
        var result = years
        result = 31 * result + months
        result = 31 * result + days
        result = 31 * result + hours
        result = 31 * result + minutes
        result = 31 * result + seconds.hashCode()
        result = 31 * result + nanoseconds.hashCode()
        return result
    }

    companion object {
        fun parse(text: String): DateTimePeriod {
            fun parseException(message: String, position: Int): Nothing =
                throw DateTimeFormatException("Parse error at char $position: $message")
            val START = 0
            val AFTER_P = 1
            val AFTER_YEAR = 2
            val AFTER_MONTH = 3
            val AFTER_WEEK = 4
            val AFTER_DAY = 5
            val AFTER_T = 6
            val AFTER_HOUR = 7
            val AFTER_MINUTE = 8
            val AFTER_SECOND_AND_NANO = 9

            var state = START
            // next unread character
            var i = 0
            var sign = 1
            var years = 0
            var months = 0
            var weeks = 0
            var days = 0
            var hours = 0
            var minutes = 0
            var seconds = 0L
            var nanoseconds = 0L
            while (true) {
                if (i >= text.length) {
                    if (state == START)
                        parseException("Unexpected end of input; 'P' designator is required", i)
                    if (state == AFTER_T)
                        parseException("Unexpected end of input; at least one time component is required after 'T'", i)
                    val daysTotal = try {
                        safeAdd(days, safeMultiply(weeks, 7))
                    } catch (e: ArithmeticException) {
                        parseException("The total number of days under 'D' and 'W' designators should fit into an Int", 0)
                    }
                    return DateTimePeriod(years, months, daysTotal, hours, minutes, seconds, nanoseconds)
                }
                if (state == START) {
                    if (i + 1 >= text.length && (text[i] == '+' || text[i] == '-'))
                        parseException("Unexpected end of string; 'P' designator is required", i)
                    when (text[i]) {
                        '+', '-' -> {
                            if (text[i] == '-')
                                sign = -1
                            if (text[i + 1] != 'P')
                                parseException("Expected 'P', got '${text[i + 1]}'", i + 1)
                            i += 2
                        }
                        'P' -> { i += 1 }
                        else -> parseException("Expected '+', '-', 'P', got '${text[i]}'", i)
                    }
                    state = AFTER_P
                    continue
                }
                var localSign = sign
                val iStart = i
                when (text[i]) {
                    '+', '-' -> {
                        if (text[i] == '-') localSign *= -1
                        i += 1
                        if (i >= text.length || text[i] !in '0'..'9')
                            parseException("A number expected after '${text[i]}'", i)
                    }
                    in '0'..'9' -> { }
                    'T' -> {
                        if (state >= AFTER_T)
                            parseException("Only one 'T' designator is allowed", i)
                        state = AFTER_T
                        i += 1
                        continue
                    }
                }
                var number = 0L
                while (i < text.length && text[i] in '0'..'9') {
                    try {
                        number = safeAdd(safeMultiply(number, 10), (text[i] - '0').toLong())
                    } catch (e: ArithmeticException) {
                        parseException("The number is too large", iStart)
                    }
                    i += 1
                }
                number *= localSign
                if (i == text.length)
                    parseException("Expected a designator after the numerical value", i)
                val wrongOrder = "Wrong component order: should be 'Y', 'M', 'W', 'D', then designator 'T', then 'H', 'M', 'S'"
                fun Long.toIntThrowing(component: Char): Int {
                    if (this < Int.MIN_VALUE || this > Int.MAX_VALUE)
                        parseException("Value $this does not fit into an Int, which is required for component '$component'", iStart)
                    return toInt()
                }
                when (text[i].toUpperCase()) {
                    'Y' -> {
                        if (state >= AFTER_YEAR)
                            parseException(wrongOrder, i)
                        state = AFTER_YEAR
                        years = number.toIntThrowing('Y')
                    }
                    'M' -> {
                        if (state >= AFTER_T) {
                            // Minutes
                            if (state >= AFTER_MINUTE)
                                parseException(wrongOrder, i)
                            state = AFTER_MINUTE
                            minutes = number.toIntThrowing('M')
                        } else {
                            // Months
                            if (state >= AFTER_MONTH)
                                parseException(wrongOrder, i)
                            state = AFTER_MONTH
                            months = number.toIntThrowing('M')
                        }
                    }
                    'W' -> {
                        if (state >= AFTER_WEEK)
                            parseException(wrongOrder, i)
                        state = AFTER_WEEK
                        weeks = number.toIntThrowing('W')
                    }
                    'D' -> {
                        if (state >= AFTER_DAY)
                            parseException(wrongOrder, i)
                        state = AFTER_DAY
                        days = number.toIntThrowing('D')
                    }
                    'H' -> {
                        if (state >= AFTER_HOUR || state < AFTER_T)
                            parseException(wrongOrder, i)
                        state = AFTER_HOUR
                        hours = number.toIntThrowing('H')
                    }
                    'S' -> {
                        if (state >= AFTER_SECOND_AND_NANO || state < AFTER_T)
                            parseException(wrongOrder, i)
                        state = AFTER_SECOND_AND_NANO
                        seconds = number
                    }
                    '.', ',' -> {
                        i += 1
                        if (i >= text.length)
                            parseException("Expected designator 'S' after ${text[i - 1]}", i)
                        val iStartFraction = i
                        while (i < text.length && text[i] in '0'..'9')
                            i += 1
                        val fractionalPart = text.substring(iStartFraction, i) + "0".repeat(9 - (i - iStartFraction))
                        nanoseconds = fractionalPart.toLong(10)
                        if (text[i] != 'S')
                            parseException("Expected the 'S' designator after a fraction", i)
                        if (state >= AFTER_SECOND_AND_NANO || state < AFTER_T)
                            parseException(wrongOrder, i)
                        state = AFTER_SECOND_AND_NANO
                        seconds = number
                    }
                    else -> parseException("Expected a designator after the numerical value", i)
                }
                i += 1
           }
        }
    }
}

object DatePeriodComponentSerializer: KSerializer<DatePeriod> {

    private fun unexpectedNonzero(fieldName: String, value: Long) {
        if (value != 0L) {
            throw SerializationException("expected field '$fieldName' to be zero, but was $value")
        }
    }

    private fun unexpectedNonzero(fieldName: String, value: Int) = unexpectedNonzero(fieldName, value.toLong())

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("DatePeriod") {
            element<Int>("years", isOptional = true)
            element<Int>("months", isOptional = true)
            element<Int>("days", isOptional = true)
            element<Int>("hours", isOptional = true)
            element<Int>("minutes", isOptional = true)
            element<Long>("seconds", isOptional = true)
            element<Long>("nanoseconds", isOptional = true)
        }

    override fun deserialize(decoder: Decoder): DatePeriod =
        decoder.decodeStructure(descriptor) {
            var years = 0
            var months = 0
            var days = 0
            loop@while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> years = decodeIntElement(descriptor, 0)
                    1 -> months = decodeIntElement(descriptor, 1)
                    2 -> days = decodeIntElement(descriptor, 2)
                    3 -> unexpectedNonzero("hours", decodeIntElement(descriptor, 3))
                    4 -> unexpectedNonzero("minutes", decodeIntElement(descriptor, 4))
                    5 -> unexpectedNonzero("seconds", decodeLongElement(descriptor, 5))
                    6 -> unexpectedNonzero("nanoseconds", decodeLongElement(descriptor, 6))
                    CompositeDecoder.DECODE_DONE -> break@loop // https://youtrack.jetbrains.com/issue/KT-42262
                    else -> error("Unexpected index: $index")
                }
            }
            DatePeriod(years, months, days)
        }

    override fun serialize(encoder: Encoder, value: DatePeriod) {
        encoder.encodeStructure(descriptor) {
            with(value) {
                if (years != 0) encodeIntElement(DateTimePeriodComponentSerializer.descriptor, 0, years)
                if (months != 0) encodeIntElement(DateTimePeriodComponentSerializer.descriptor, 1, months)
                if (days != 0) encodeIntElement(DateTimePeriodComponentSerializer.descriptor, 2, days)
            }
        }
    }

}

object DatePeriodISO8601Serializer: KSerializer<DatePeriod> {

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("DatePeriod", PrimitiveKind.STRING)

    // TODO: consider whether should fail when parsing "P1YT0H0M0.0S"
    override fun deserialize(decoder: Decoder): DatePeriod =
        when (val period = DateTimePeriod.parse(decoder.decodeString())) {
            is DatePeriod -> period
            else -> throw IllegalArgumentException("$period is not a date-based period")
        }

    override fun serialize(encoder: Encoder, value: DatePeriod) {
        encoder.encodeString(value.toString())
    }

}

@Serializable(with = DatePeriodISO8601Serializer::class)
class DatePeriod(
        override val years: Int = 0,
        override val months: Int = 0,
        override val days: Int = 0
) : DateTimePeriod() {
    override val hours: Int get() = 0
    override val minutes: Int get() = 0
    override val seconds: Long get() = 0
    override val nanoseconds: Long get() = 0
}

private class DateTimePeriodImpl(
        override val years: Int = 0,
        override val months: Int = 0,
        override val days: Int = 0,
        override val hours: Int = 0,
        override val minutes: Int = 0,
        override val seconds: Long = 0,
        override val nanoseconds: Long = 0
) : DateTimePeriod()

fun DateTimePeriod(
    years: Int = 0,
    months: Int = 0,
    days: Int = 0,
    hours: Int = 0,
    minutes: Int = 0,
    seconds: Long = 0,
    nanoseconds: Long = 0
): DateTimePeriod = if (hours or minutes != 0 || seconds or nanoseconds != 0L)
    DateTimePeriodImpl(years, months, days, hours, minutes, seconds, nanoseconds)
else
    DatePeriod(years, months, days)

@OptIn(ExperimentalTime::class)
fun Duration.toDateTimePeriod(): DateTimePeriod = toComponents { hours, minutes, seconds, nanoseconds ->
    DateTimePeriod(hours = hours, minutes = minutes, seconds = seconds.toLong(), nanoseconds = nanoseconds.toLong())
}

operator fun DateTimePeriod.plus(other: DateTimePeriod): DateTimePeriod = DateTimePeriod(
        safeAdd(this.years, other.years),
        safeAdd(this.months, other.months),
        safeAdd(this.days, other.days),
        safeAdd(this.hours, other.hours),
        safeAdd(this.minutes, other.minutes),
        safeAdd(this.seconds, other.seconds),
        safeAdd(this.nanoseconds, other.nanoseconds)
)

operator fun DatePeriod.plus(other: DatePeriod): DatePeriod = DatePeriod(
        safeAdd(this.years, other.years),
        safeAdd(this.months, other.months),
        safeAdd(this.days, other.days)
)

