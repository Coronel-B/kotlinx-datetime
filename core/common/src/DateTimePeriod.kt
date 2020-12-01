/*
 * Copyright 2019-2020 JetBrains s.r.o.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.datetime

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

object DateTimePeriodSerializer: KSerializer<DateTimePeriod> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Instant") {
            element<Int>("years")
            element<Int>("months")
            element<Int>("days")
            element<Int>("hours")
            element<Int>("minutes")
            element<Long>("seconds")
            element<Long>("nanoseconds")
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
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> years = decodeIntElement(descriptor, 0)
                    1 -> months = decodeIntElement(descriptor, 1)
                    2 -> days = decodeIntElement(descriptor, 2)
                    3 -> hours = decodeIntElement(descriptor, 3)
                    4 -> minutes = decodeIntElement(descriptor, 4)
                    5 -> seconds = decodeLongElement(descriptor, 5)
                    6 -> nanoseconds = decodeLongElement(descriptor, 6)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            DateTimePeriod(years, months, days, hours, minutes, seconds, nanoseconds)
        }

    override fun serialize(encoder: Encoder, value: DateTimePeriod) {
        encoder.encodeStructure(descriptor) {
            with (value) {
                encodeIntElement(descriptor, 0, value.years)
                encodeIntElement(descriptor, 1, value.months)
                encodeIntElement(descriptor, 2, value.days)
                if (hours != 0 || minutes != 0 || seconds != 0L || nanoseconds != 0L) {
                    encodeIntElement(descriptor, 3, value.hours)
                    encodeIntElement(descriptor, 4, value.minutes)
                    encodeLongElement(descriptor, 5, value.seconds)
                    encodeLongElement(descriptor, 6, value.nanoseconds)
                }
            }
        }
    }

}

@Serializable(with = DateTimePeriodSerializer::class)
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

    // TODO: parsing from iso string
}

object DatePeriodSerializer: KSerializer<DatePeriod> {

    override val descriptor: SerialDescriptor = DateTimePeriodSerializer.descriptor

    override fun deserialize(decoder: Decoder): DatePeriod =
        decoder.decodeStructure(descriptor) {
            var years = 0
            var months = 0
            var days = 0
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> years = decodeIntElement(descriptor, 0)
                    1 -> months = decodeIntElement(descriptor, 1)
                    2 -> days = decodeIntElement(descriptor, 2)
                    3 -> require(decodeIntElement(descriptor, 3) == 0)
                    4 -> require(decodeIntElement(descriptor, 4) == 0)
                    5 -> require(decodeLongElement(descriptor, 5) == 0L)
                    6 -> require(decodeLongElement(descriptor, 6) == 0L)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            DatePeriod(years, months, days)
        }

    override fun serialize(encoder: Encoder, value: DatePeriod) {
        encoder.encodeStructure(descriptor) {
            encodeIntElement(descriptor, 0, value.years)
            encodeIntElement(descriptor, 1, value.months)
            encodeIntElement(descriptor, 2, value.days)
        }
    }

}

@Serializable(with = DatePeriodSerializer::class)
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

