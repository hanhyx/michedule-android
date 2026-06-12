package com.ljh.michedule.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.color.ColorProvider
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ljh.michedule.MainActivity
import com.ljh.michedule.data.db.AppDatabase
import com.ljh.michedule.data.db.ShiftTypeConfig
import com.ljh.michedule.model.ShiftType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class WeekWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val weekEnd = weekStart.plusDays(6)
        val weekStartStr = weekStart.toString()
        val weekEndStr = weekEnd.toString()
        val db = AppDatabase.getInstance(context)

        data class WeekData(
            val myShifts: Map<String, WidgetShiftInfo>,
            val partnerShifts: Map<String, WidgetShiftInfo>,
            val myTypeMap: Map<String, ShiftTypeConfig>,
            val partnerTypeMap: Map<String, ShiftTypeConfig>,
            val weekMemos: Map<String, String>,
            val weekTodos: Map<String, List<String>>,
            val weekMoods: Map<String, String>,
            val partnerName: String
        )

        val data = withContext(Dispatchers.IO) {
            val myTypeConfigs = try {
                db.shiftTypeConfigDao().getAll().associateBy { it.id }
            } catch (_: Exception) { emptyMap() }
            val partnerTypeConfigs = try {
                db.shiftTypeConfigDao().getPartner().associateBy { it.id }
            } catch (_: Exception) { emptyMap() }
            val typeConfigs = ShiftTypeConfig.DEFAULTS.associateBy { it.id } + myTypeConfigs
            val pTypeMap = ShiftTypeConfig.DEFAULTS.associateBy { it.id } + partnerTypeConfigs

            val allShifts = db.shiftDao().getAllShifts()
                .filter { it.date in weekStartStr..weekEndStr }

            val mine = allShifts.associate {
                it.date to WidgetShiftInfo(
                    ShiftType.fromString(it.type),
                    typeConfigs[it.type],
                    it.hasAlba,
                    it.extraShifts
                )
            }

            val memos = allShifts.filter { !it.memo.isNullOrBlank() }
                .associate { it.date to (it.memo ?: "") }

            val todos = mutableMapOf<String, List<String>>()
            val moods = mutableMapOf<String, String>()
            for (i in 0..6) {
                val dateStr = weekStart.plusDays(i.toLong()).toString()
                try {
                    val dayTodos = db.todoDao().getTodosForDate(dateStr).firstOrNull() ?: emptyList()
                    if (dayTodos.isNotEmpty()) {
                        todos[dateStr] = dayTodos.take(3).map { (if (it.isDone) "✅" else "⬜") + it.title }
                    }
                } catch (_: Exception) {}
                try {
                    val mood = db.moodDao().getMoodForDate(dateStr).firstOrNull()
                    if (mood != null) moods[dateStr] = mood.emoji
                } catch (_: Exception) {}
            }

            var pName = ""
            val partner = try {
                val friends = db.friendShiftDao()
                    .getShiftsInRange(weekStartStr, weekEndStr)
                    .firstOrNull() ?: emptyList()
                if (friends.isNotEmpty()) {
                    pName = friends.firstOrNull()?.friendName ?: ""
                }
                friends.associate {
                    it.date to WidgetShiftInfo(
                        ShiftType.fromString(it.type),
                        pTypeMap[it.type],
                        it.hasAlba,
                        it.extraShifts
                    )
                }
            } catch (_: Exception) {
                emptyMap<String, WidgetShiftInfo>()
            }

            WeekData(mine, partner, typeConfigs, pTypeMap, memos, todos, moods, pName)
        }

        provideContent {
            WeekWidgetContent(today, weekStart, data.myShifts, data.partnerShifts,
                data.myTypeMap, data.partnerTypeMap, data.weekMemos, data.weekTodos, data.weekMoods, data.partnerName)
        }
    }
}

@Composable
private fun WeekWidgetContent(
    today: LocalDate,
    weekStart: LocalDate,
    myShifts: Map<String, WidgetShiftInfo>,
    partnerShifts: Map<String, WidgetShiftInfo>,
    myTypeMap: Map<String, ShiftTypeConfig> = emptyMap(),
    partnerTypeMap: Map<String, ShiftTypeConfig> = emptyMap(),
    weekMemos: Map<String, String> = emptyMap(),
    weekTodos: Map<String, List<String>> = emptyMap(),
    weekMoods: Map<String, String> = emptyMap(),
    partnerName: String = ""
) {
    val bg = Color(0xFF111118)
    val headerBg = Color(0xFF1A1A24)
    val accent = Color(0xFFB794F6)
    val muted = Color(0xFF555566)
    val dimText = Color(0xFF8888AA)
    val white = Color(0xFFFFFFFF)
    val sundayRed = Color(0xFFF87171)
    val saturdayBlue = Color(0xFF60A5FA)
    val emptyBadge = Color(0xFF252535)
    val divider = Color(0xFF2A2A3A)
    val dayNames = listOf("일", "월", "화", "수", "목", "금", "토")
    val hasPartner = partnerName.isNotBlank() || partnerShifts.isNotEmpty()

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(bg, bg))
            .cornerRadius(20.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(6.dp)
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {

            // ── 날짜 헤더 Row ──
            Row(
                modifier = GlanceModifier.fillMaxWidth()
                    .cornerRadius(8.dp)
                    .background(ColorProvider(headerBg, headerBg))
                    .padding(vertical = 3.dp)
            ) {
                for (i in 0..6) {
                    val date = weekStart.plusDays(i.toLong())
                    val isToday = date == today
                    val color = when {
                        isToday -> accent
                        i == 0 -> sundayRed; i == 6 -> saturdayBlue
                        else -> dimText
                    }
                    Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(dayNames[i], style = TextStyle(fontSize = 8.sp, color = ColorProvider(color, color)))
                            Text(
                                "${date.dayOfMonth}",
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = ColorProvider(if (isToday) white else color, if (isToday) white else color)
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(3.dp))

            // ── 내 근무 배지 Row ──
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                for (i in 0..6) {
                    val date = weekStart.plusDays(i.toLong())
                    val dateStr = date.toString()
                    val isToday = date == today
                    val myInfo = myShifts[dateStr]
                    val badgeColor = myInfo?.config?.color ?: myInfo?.type?.color ?: emptyBadge
                    val myEmoji = myInfo?.config?.emoji ?: myInfo?.type?.emoji ?: ""
                    val myLabel = myInfo?.config?.shortLabel ?: myInfo?.type?.shortLabel ?: ""
                    val myFontColor = myInfo?.config?.fontColor ?: white
                    val myExtras = myInfo?.getExtraShiftList() ?: emptyList()
                    val myExtraConfig = if (myExtras.isNotEmpty()) myTypeMap[myExtras.first()] else null
                    val mood = weekMoods[dateStr]
                    val hasMemo = weekMemos[dateStr]?.isNotBlank() == true
                    val todos = weekTodos[dateStr] ?: emptyList()

                    Column(
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(horizontal = 1.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .cornerRadius(10.dp)
                                .background(ColorProvider(
                                    if (isToday) accent else badgeColor,
                                    if (isToday) accent else badgeColor
                                ))
                                .padding(vertical = 2.dp, horizontal = 1.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(myEmoji.ifBlank { "·" }, style = TextStyle(fontSize = 16.sp))
                                Text(
                                    myLabel.ifBlank { "-" },
                                    style = TextStyle(
                                        fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                        color = ColorProvider(
                                            if (isToday) white else myFontColor,
                                            if (isToday) white else myFontColor
                                        )
                                    ),
                                    maxLines = 1
                                )
                                if (myExtraConfig != null) {
                                    Text(
                                        "+${myExtraConfig.shortLabel}",
                                        style = TextStyle(fontSize = 7.sp, color = ColorProvider(white.copy(alpha = 0.8f), white.copy(alpha = 0.8f))),
                                        maxLines = 1
                                    )
                                }
                                val icons = buildString {
                                    if (mood != null) append(mood)
                                    if (hasMemo) append("📝")
                                    if (todos.isNotEmpty()) append(if (todos.all { it.startsWith("✅") }) "✅" else "⬜")
                                }
                                if (icons.isNotBlank()) {
                                    Text(icons, style = TextStyle(fontSize = 7.sp), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            // ── 상대 영역 ──
            if (hasPartner) {
                Spacer(modifier = GlanceModifier.height(3.dp))
                Box(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(ColorProvider(divider, divider))) {}
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("💕", style = TextStyle(fontSize = 8.sp))
                    Spacer(modifier = GlanceModifier.width(2.dp))
                    Text(
                        partnerName.ifBlank { "상대" },
                        style = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Bold, color = ColorProvider(accent, accent))
                    )
                }

                // ── 상대 근무 배지 Row ──
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    for (i in 0..6) {
                        val dateStr = weekStart.plusDays(i.toLong()).toString()
                        val pInfo = partnerShifts[dateStr]
                        val pBadgeColor = pInfo?.config?.color ?: pInfo?.type?.color ?: emptyBadge
                        val pEmoji = pInfo?.config?.emoji ?: pInfo?.type?.emoji ?: ""
                        val pLabel = pInfo?.config?.shortLabel ?: pInfo?.type?.shortLabel ?: ""
                        val pFontColor = pInfo?.config?.fontColor ?: white

                        Column(
                            modifier = GlanceModifier.defaultWeight().padding(horizontal = 1.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .cornerRadius(8.dp)
                                    .background(ColorProvider(pBadgeColor, pBadgeColor))
                                    .padding(vertical = 2.dp, horizontal = 1.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(pEmoji.ifBlank { "·" }, style = TextStyle(fontSize = 11.sp))
                                    Text(
                                        pLabel.ifBlank { "-" },
                                        style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = ColorProvider(pFontColor, pFontColor)),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}
