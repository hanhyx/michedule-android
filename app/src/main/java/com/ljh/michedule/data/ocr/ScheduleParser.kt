package com.ljh.michedule.data.ocr

import android.util.Log
import com.ljh.michedule.data.db.ShiftTypeConfig
import java.time.LocalDate
import java.time.YearMonth

private const val TAG = "ScheduleParser"

data class OcrScheduleResult(
    val yearMonth: YearMonth,
    val shifts: Map<LocalDate, String>
)

object ScheduleParser {

    private val BUILTIN_ALIASES = mapOf(
        "주" to "day", "주간" to "day", "D" to "day", "d" to "day", "day" to "day", "DAY" to "day",
        "야" to "night", "야간" to "night", "N" to "night", "n" to "night", "night" to "night", "NIGHT" to "night",
        "비" to "off", "비번" to "off", "OFF" to "off", "off" to "off", "O" to "off", "휴" to "off", "휴무" to "off",
        "조" to "nightEarly", "조야" to "nightEarly", "조기야간" to "nightEarly", "E" to "nightEarly"
    )

    fun parse(
        fullText: String,
        blocks: List<OcrTextBlock>,
        userName: String,
        shiftTypes: List<ShiftTypeConfig>,
        fallbackYearMonth: YearMonth = YearMonth.now()
    ): OcrScheduleResult? {
        Log.d(TAG, "Parsing OCR text (${fullText.length} chars, ${blocks.size} blocks, user=$userName)")

        val yearMonth = detectYearMonth(fullText) ?: fallbackYearMonth
        Log.d(TAG, "Detected yearMonth: $yearMonth")

        val aliasMap = buildAliasMap(shiftTypes)

        val result = tryParseTable(blocks, userName, yearMonth, aliasMap)
            ?: tryParseInlineList(fullText, userName, yearMonth, aliasMap)
            ?: tryParseFreeForm(fullText, yearMonth, aliasMap)

        if (result != null) {
            Log.d(TAG, "Parsed ${result.shifts.size} shifts for $yearMonth")
        } else {
            Log.w(TAG, "Failed to parse any shifts")
        }
        return result
    }

    private fun buildAliasMap(shiftTypes: List<ShiftTypeConfig>): Map<String, String> {
        val map = BUILTIN_ALIASES.toMutableMap()
        for (config in shiftTypes) {
            map[config.label] = config.id
            map[config.shortLabel] = config.id
        }
        return map
    }

    private fun detectYearMonth(text: String): YearMonth? {
        val patterns = listOf(
            Regex("""(\d{4})\s*[년./-]\s*(\d{1,2})\s*월?"""),
            Regex("""(\d{1,2})\s*월\s*근무"""),
            Regex("""(\d{4})[./](\d{1,2})""")
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return try {
                    if (match.groupValues.size >= 3 && match.groupValues[1].length == 4) {
                        YearMonth.of(match.groupValues[1].toInt(), match.groupValues[2].toInt())
                    } else {
                        val month = match.groupValues[1].toInt()
                        if (month in 1..12) {
                            YearMonth.of(YearMonth.now().year, month)
                        } else null
                    }
                } catch (_: Exception) { null }
            }
        }
        return null
    }

    /**
     * 표 형태 파싱: TextBlock 좌표 기반으로 행/열 그룹핑
     * 사용자 이름이 포함된 행을 찾고, 날짜 헤더 열과 교차하여 매핑
     */
    private fun tryParseTable(
        blocks: List<OcrTextBlock>,
        userName: String,
        yearMonth: YearMonth,
        aliasMap: Map<String, String>
    ): OcrScheduleResult? {
        if (blocks.size < 10) return null

        val yTolerance = blocks.map { it.height }.average().toFloat() * 0.5f

        val rows = groupByY(blocks, yTolerance)
        if (rows.size < 3) return null

        val dateRow = findDateRow(rows, yearMonth)
        if (dateRow == null) {
            Log.d(TAG, "Table parse: no date row found")
            return null
        }

        val userRow = if (userName.isNotBlank()) {
            findUserRow(rows, userName)
        } else null

        val targetRow = userRow ?: findBestShiftRow(rows, dateRow, aliasMap)
        if (targetRow == null) {
            Log.d(TAG, "Table parse: no user/shift row found")
            return null
        }

        val dateColumns = dateRow.sortedBy { it.left }
            .mapNotNull { block ->
                val day = block.text.replace(Regex("[^0-9]"), "").toIntOrNull()
                if (day != null && day in 1..yearMonth.lengthOfMonth()) {
                    day to block.centerX
                } else null
            }

        val shifts = mutableMapOf<LocalDate, String>()
        val sortedTarget = targetRow.sortedBy { it.left }

        for ((day, dateX) in dateColumns) {
            val closest = sortedTarget.minByOrNull { kotlin.math.abs(it.centerX - dateX) }
            if (closest != null && kotlin.math.abs(closest.centerX - dateX) < closest.width * 2) {
                val shiftId = aliasMap[closest.text]
                if (shiftId != null) {
                    val date = yearMonth.atDay(day)
                    shifts[date] = shiftId
                }
            }
        }

        return if (shifts.isNotEmpty()) OcrScheduleResult(yearMonth, shifts) else null
    }

    /**
     * 나열형 파싱: "홍길동: 주주야야비비..." 또는 "주 야 비 주 야 비" 패턴
     */
    private fun tryParseInlineList(
        text: String,
        userName: String,
        yearMonth: YearMonth,
        aliasMap: Map<String, String>
    ): OcrScheduleResult? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        val targetLine = if (userName.isNotBlank()) {
            lines.firstOrNull { it.contains(userName) }
        } else null

        val lineToParse = targetLine ?: lines.firstOrNull { line ->
            val tokens = tokenizeLine(line)
            val matchCount = tokens.count { aliasMap.containsKey(it) }
            matchCount >= 5
        }

        if (lineToParse == null) return null

        val cleaned = if (userName.isNotBlank()) {
            lineToParse.substringAfter(userName).trimStart(':', ' ', '\t', '：')
        } else lineToParse

        val tokens = tokenizeLine(cleaned)
        val shifts = mutableMapOf<LocalDate, String>()
        var day = 1
        val maxDay = yearMonth.lengthOfMonth()

        for (token in tokens) {
            if (day > maxDay) break
            val shiftId = aliasMap[token]
            if (shiftId != null) {
                shifts[yearMonth.atDay(day)] = shiftId
                day++
            }
        }

        return if (shifts.size >= 3) OcrScheduleResult(yearMonth, shifts) else null
    }

    /**
     * 자유형 파싱: 텍스트 전체에서 근무 패턴 시퀀스 탐색
     */
    private fun tryParseFreeForm(
        text: String,
        yearMonth: YearMonth,
        aliasMap: Map<String, String>
    ): OcrScheduleResult? {
        val allTokens = text.lines()
            .flatMap { tokenizeLine(it) }
            .filter { aliasMap.containsKey(it) }

        if (allTokens.size < 5) return null

        val maxDay = yearMonth.lengthOfMonth()
        val shifts = mutableMapOf<LocalDate, String>()
        val tokensToUse = allTokens.take(maxDay)

        for ((idx, token) in tokensToUse.withIndex()) {
            val day = idx + 1
            if (day > maxDay) break
            val shiftId = aliasMap[token] ?: continue
            shifts[yearMonth.atDay(day)] = shiftId
        }

        return if (shifts.size >= 3) OcrScheduleResult(yearMonth, shifts) else null
    }

    private fun tokenizeLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val cleaned = line.replace(Regex("[,./|\\-\\s]+"), " ").trim()

        val words = cleaned.split(" ").filter { it.isNotBlank() }
        for (word in words) {
            if (word.length <= 3) {
                tokens.add(word)
            } else {
                word.forEach { ch ->
                    tokens.add(ch.toString())
                }
            }
        }
        return tokens
    }

    private fun groupByY(blocks: List<OcrTextBlock>, tolerance: Float): List<List<OcrTextBlock>> {
        val sorted = blocks.sortedBy { it.centerY }
        val rows = mutableListOf<MutableList<OcrTextBlock>>()

        for (block in sorted) {
            val existingRow = rows.lastOrNull()
            if (existingRow != null && kotlin.math.abs(existingRow.first().centerY - block.centerY) < tolerance) {
                existingRow.add(block)
            } else {
                rows.add(mutableListOf(block))
            }
        }
        return rows
    }

    private fun findDateRow(rows: List<List<OcrTextBlock>>, yearMonth: YearMonth): List<OcrTextBlock>? {
        val maxDay = yearMonth.lengthOfMonth()
        return rows.maxByOrNull { row ->
            row.count { block ->
                val num = block.text.replace(Regex("[^0-9]"), "").toIntOrNull()
                num != null && num in 1..maxDay
            }
        }?.takeIf { row ->
            val dateCount = row.count { block ->
                val num = block.text.replace(Regex("[^0-9]"), "").toIntOrNull()
                num != null && num in 1..maxDay
            }
            dateCount >= 5
        }
    }

    private fun findUserRow(rows: List<List<OcrTextBlock>>, userName: String): List<OcrTextBlock>? {
        return rows.firstOrNull { row ->
            row.any { it.text.contains(userName) }
        }
    }

    private fun findBestShiftRow(
        rows: List<List<OcrTextBlock>>,
        dateRow: List<OcrTextBlock>,
        aliasMap: Map<String, String>
    ): List<OcrTextBlock>? {
        val dateY = dateRow.first().centerY
        return rows
            .filter { it !== dateRow && it.first().centerY > dateY }
            .maxByOrNull { row ->
                row.count { aliasMap.containsKey(it.text) }
            }?.takeIf { row ->
                row.count { aliasMap.containsKey(it.text) } >= 3
            }
    }
}
