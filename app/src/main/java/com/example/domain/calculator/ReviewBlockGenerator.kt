package com.example.domain.calculator

import com.example.data.DateUtils
import com.example.domain.model.ReviewBlock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Generates fixed consecutive 3-day calendar blocks for the review system.
 * Tracking period starts on 16 September 2026.
 *
 * Blocks for September 2026:
 * 16–18 SEP
 * 19–21 SEP
 * 22–24 SEP
 * 25–27 SEP
 * 28–30 SEP
 *
 * Subsequent calendar months continue starting from the 1st:
 * 1–3 OCT, 4–6 OCT, 7–9 OCT, ..., 28–30 OCT
 */
object ReviewBlockGenerator {

    const val TRACKING_START_DATE = "2026-09-16"

    /**
     * Generates all fixed 3-day blocks from the tracking start date (2026-09-16)
     * through the month of the targetDate.
     */
    fun generateBlocksUpTo(targetDate: String): List<ReviewBlock> {
        val blocks = mutableListOf<ReviewBlock>()

        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.SEPTEMBER)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val targetCal = Calendar.getInstance().apply {
            val parsed = DateUtils.parseDate(targetDate) ?: Date()
            time = parsed
        }

        // Loop from September 2026 through the month of targetCal
        while (cal.get(Calendar.YEAR) < targetCal.get(Calendar.YEAR) ||
            (cal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) && cal.get(Calendar.MONTH) <= targetCal.get(Calendar.MONTH))
        ) {
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)
            val monthFormat = SimpleDateFormat("MMM", Locale.US)
            val monthStr = monthFormat.format(cal.time).uppercase()

            val startDays = if (year == 2026 && month == Calendar.SEPTEMBER) {
                listOf(16, 19, 22, 25, 28)
            } else {
                listOf(1, 4, 7, 10, 13, 16, 19, 22, 25, 28)
            }

            for (startDay in startDays) {
                val endDay = startDay + 2
                val startStr = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, startDay)
                val day2Str = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, startDay + 1)
                val endStr = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, endDay)
                val label = "$startDay–$endDay $monthStr"

                blocks.add(
                    ReviewBlock(
                        id = "${startStr}_$endStr",
                        startDate = startStr,
                        endDate = endStr,
                        dates = listOf(startStr, day2Str, endStr),
                        label = label
                    )
                )
            }
            cal.add(Calendar.MONTH, 1)
        }

        return blocks
    }

    /**
     * Returns only completed 3-day blocks for the given current date.
     * A block is complete only after day 3 has ended (currentDate > block.endDate).
     * Ordered chronologically ascending.
     */
    fun getCompletedBlocks(currentDate: String): List<ReviewBlock> {
        val allBlocks = generateBlocksUpTo(currentDate)
        return allBlocks.filter { it.isCompleted(currentDate) }
    }

    /**
     * Formats the Overall range label from completed blocks.
     * Example:
     * 16–18 SEP -> "16–18 SEP"
     * 16–18 SEP + 19–21 SEP -> "16–21 SEP"
     * 16–18 SEP ... 22–24 SEP -> "16–24 SEP"
     * 16–18 SEP ... 1–3 OCT -> "16 SEP – 3 OCT"
     */
    fun formatOverallLabel(completedBlocks: List<ReviewBlock>): String {
        if (completedBlocks.isEmpty()) return ""
        val firstBlock = completedBlocks.first()
        val lastBlock = completedBlocks.last()

        val firstStart = DateUtils.parseDate(firstBlock.startDate) ?: return ""
        val lastEnd = DateUtils.parseDate(lastBlock.endDate) ?: return ""

        val startCal = Calendar.getInstance().apply { time = firstStart }
        val endCal = Calendar.getInstance().apply { time = lastEnd }

        val startDay = startCal.get(Calendar.DAY_OF_MONTH)
        val endDay = endCal.get(Calendar.DAY_OF_MONTH)

        val monthFormat = SimpleDateFormat("MMM", Locale.US)
        val startMonth = monthFormat.format(firstStart).uppercase()
        val endMonth = monthFormat.format(lastEnd).uppercase()

        return if (startCal.get(Calendar.YEAR) == endCal.get(Calendar.YEAR) &&
            startCal.get(Calendar.MONTH) == endCal.get(Calendar.MONTH)
        ) {
            "$startDay–$endDay $startMonth"
        } else {
            "$startDay $startMonth – $endDay $endMonth"
        }
    }
}
