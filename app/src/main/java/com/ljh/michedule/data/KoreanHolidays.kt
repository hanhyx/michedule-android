package com.ljh.michedule.data

import java.time.LocalDate
import java.time.Month

data class HolidayInfo(val name: String, val isSubstitute: Boolean = false)

object KoreanHolidays {

    fun getHoliday(date: LocalDate): HolidayInfo? {
        return fixedHolidays(date)
            ?: lunarHolidays[date]
            ?: substituteHolidays[date]
    }

    fun isHoliday(date: LocalDate): Boolean = getHoliday(date) != null

    private fun fixedHolidays(date: LocalDate): HolidayInfo? = when {
        date.month == Month.JANUARY && date.dayOfMonth == 1 -> HolidayInfo("신정")
        date.month == Month.MARCH && date.dayOfMonth == 1 -> HolidayInfo("삼일절")
        date.month == Month.MAY && date.dayOfMonth == 1 && date.year >= 2026 -> HolidayInfo("근로자의날")
        date.month == Month.MAY && date.dayOfMonth == 5 -> HolidayInfo("어린이날")
        date.month == Month.JUNE && date.dayOfMonth == 6 -> HolidayInfo("현충일")
        date.month == Month.AUGUST && date.dayOfMonth == 15 -> HolidayInfo("광복절")
        date.month == Month.OCTOBER && date.dayOfMonth == 3 -> HolidayInfo("개천절")
        date.month == Month.OCTOBER && date.dayOfMonth == 9 -> HolidayInfo("한글날")
        date.month == Month.DECEMBER && date.dayOfMonth == 25 -> HolidayInfo("크리스마스")
        else -> null
    }

    private val lunarHolidays: Map<LocalDate, HolidayInfo> = buildMap {
        fun seollal(vararg dates: LocalDate) = dates.forEach { put(it, HolidayInfo("설날")) }
        fun chuseok(vararg dates: LocalDate) = dates.forEach { put(it, HolidayInfo("추석")) }
        fun buddha(date: LocalDate) = put(date, HolidayInfo("부처님오신날"))

        // 2025
        seollal(d(2025, 1, 28), d(2025, 1, 29), d(2025, 1, 30))
        buddha(d(2025, 5, 5)) // 어린이날과 겹침
        chuseok(d(2025, 10, 5), d(2025, 10, 6), d(2025, 10, 7))

        // 2026
        seollal(d(2026, 2, 16), d(2026, 2, 17), d(2026, 2, 18))
        buddha(d(2026, 5, 24))
        chuseok(d(2026, 9, 24), d(2026, 9, 25), d(2026, 9, 26))

        // 2027
        seollal(d(2027, 2, 6), d(2027, 2, 7), d(2027, 2, 8))
        buddha(d(2027, 5, 13))
        chuseok(d(2027, 9, 14), d(2027, 9, 15), d(2027, 9, 16))

        // 2028
        seollal(d(2028, 1, 25), d(2028, 1, 26), d(2028, 1, 27))
        buddha(d(2028, 5, 2))
        chuseok(d(2028, 10, 2), d(2028, 10, 3), d(2028, 10, 4))

        // 2029
        seollal(d(2029, 2, 12), d(2029, 2, 13), d(2029, 2, 14))
        buddha(d(2029, 5, 20))
        chuseok(d(2029, 9, 21), d(2029, 9, 22), d(2029, 9, 23))

        // 2030
        seollal(d(2030, 2, 2), d(2030, 2, 3), d(2030, 2, 4))
        buddha(d(2030, 5, 9))
        chuseok(d(2030, 9, 11), d(2030, 9, 12), d(2030, 9, 13))
    }

    private val substituteHolidays: Map<LocalDate, HolidayInfo> = buildMap {
        fun sub(date: LocalDate, name: String) = put(date, HolidayInfo("대체휴일($name)", isSubstitute = true))

        // 2025: 삼일절(토)→3/3, 어린이날+부처님오신날 겹침→5/6, 추석(일)→10/8
        sub(d(2025, 3, 3), "삼일절")
        sub(d(2025, 5, 6), "어린이날")
        sub(d(2025, 10, 8), "추석")

        // 2026: 삼일절(일)→3/2, 부처님오신날(일)→5/25, 광복절(토)→8/17, 개천절(토)→10/5
        sub(d(2026, 3, 2), "삼일절")
        sub(d(2026, 5, 25), "부처님오신날")
        sub(d(2026, 8, 17), "광복절")
        sub(d(2026, 10, 5), "개천절")

        // 2027: 설날(토일)→2/9, 현충일(일)→6/7, 광복절(일)→8/16, 개천절(일)→10/4, 한글날(토)→10/11, 크리스마스(토)→12/27
        sub(d(2027, 2, 9), "설날")
        sub(d(2027, 6, 7), "현충일")
        sub(d(2027, 8, 16), "광복절")
        sub(d(2027, 10, 4), "개천절")
        sub(d(2027, 10, 11), "한글날")
        sub(d(2027, 12, 27), "크리스마스")

        // 2028: 추석과 개천절 겹침(10/3)은 추석 우선
        // 특별한 대체휴일 없음

        // 2029: 어린이날(토)→5/7, 부처님오신날(일)→5/21, 추석(일)→9/24
        sub(d(2029, 5, 7), "어린이날")
        sub(d(2029, 5, 21), "부처님오신날")
        sub(d(2029, 9, 24), "추석")

        // 2030: 설날(토일)→2/5, 어린이날(일)→5/6
        sub(d(2030, 2, 5), "설날")
        sub(d(2030, 5, 6), "어린이날")
    }

    private fun d(year: Int, month: Int, day: Int) = LocalDate.of(year, month, day)
}
