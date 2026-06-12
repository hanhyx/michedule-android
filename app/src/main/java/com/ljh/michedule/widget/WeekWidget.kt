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
    val baseBg = Color(0xFF0F0F1A)
    val accent = Color(0xFFB794F6)
    val muted = Color(0xFF4B5563)
    val white = Color(0xFFFFFFFF)
    val sundayRed = Color(0xFFF87171)
    val saturdayBlue = Color(0xFF60A5FA)
    val todayRing = Color(0xFFB794F6)
    val emptyCellBg = Color(0xFF1A1A28)
    val dayNames = listOf("일", "월", "화", "수", "목", "금", "토")
    val hasPartner = partnerName.isNotBlank() || partnerShifts.isNotEmpty()

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(baseBg, baseBg))
            .cornerRadius(20.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(4.dp)
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {

            // ── 요일 헤더 ──
            Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 2.dp)) {
                for (i in 0..6) {
                    val date = weekStart.plusDays(i.toLong())
                    val isToday = date == today
                    val headerColor = when {
                        isToday -> accent
                        i == 0 -> sundayRed; i == 6 -> saturdayBlue
                        else -> muted
                    }
                    Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                        Text(
                            "${dayNames[i]} ${date.dayOfMonth}",
                            style = TextStyle(
                                fontSize = 8.sp,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = ColorProvider(headerColor, headerColor)
                            )
                        )
                    }
                }
            }

            // ── 메인 그리드: 7일 셀 ──
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                for (i in 0..6) {
                    val date = weekStart.plusDays(i.toLong())
                    val dateStr = date.toString()
                    val isToday = date == today

                    val myInfo = myShifts[dateStr]
                    val myBgColor = myInfo?.config?.color ?: myInfo?.type?.color
                    val myEmoji = myInfo?.config?.emoji ?: myInfo?.type?.emoji ?: ""
                    val myLabel = myInfo?.config?.shortLabel ?: myInfo?.type?.shortLabel ?: ""
                    val myFontColor = myInfo?.config?.fontColor ?: white

                    val myExtras = myInfo?.getExtraShiftList() ?: emptyList()
                    val myExtraConfig = if (myExtras.isNotEmpty()) myTypeMap[myExtras.first()] else null

                    val mood = weekMoods[dateStr]
                    val hasMemo = weekMemos[dateStr]?.isNotBlank() == true
                    val todos = weekTodos[dateStr] ?: emptyList()

                    val pInfo = partnerShifts[dateStr]
                    val pBgColor = pInfo?.config?.color ?: pInfo?.type?.color
                    val pEmoji = pInfo?.config?.emoji ?: pInfo?.type?.emoji ?: ""
                    val pLabel = pInfo?.config?.shortLabel ?: pInfo?.type?.shortLabel ?: ""

                    val cellBg = myBgColor?.copy(alpha = 0.25f) ?: emptyCellBg
                    val todayBorder = if (isToday) todayRing.copy(alpha = 0.5f) else Color.Transparent

                    Column(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxHeight()
                            .padding(horizontal = 1.dp)
                    ) {
                        // 오늘 하이라이트 테두리 효과
                        Column(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .cornerRadius(10.dp)
                                .background(ColorProvider(todayBorder, todayBorder))
                                .padding(if (isToday) 1.dp else 0.dp)
                        ) {
                            Column(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .cornerRadius(if (isToday) 9.dp else 10.dp)
                                    .background(ColorProvider(cellBg, cellBg))
                                    .padding(vertical = 3.dp, horizontal = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // 내 근무 이모지
                                Text(myEmoji.ifBlank { "·" }, style = TextStyle(fontSize = 14.sp))
                                // 내 근무 약자
                                Text(
                                    myLabel.ifBlank { "-" },
                                    style = TextStyle(
                                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        color = ColorProvider(
                                            if (myLabel.isNotBlank()) myFontColor else muted.copy(alpha = 0.3f),
                                            if (myLabel.isNotBlank()) myFontColor else muted.copy(alpha = 0.3f)
                                        )
                                    ),
                                    maxLines = 1
                                )
                                // 추가근무
                                if (myExtraConfig != null) {
                                    val exEmoji = myExtraConfig.emoji
                                    val exLabel = myExtraConfig.shortLabel
                                    Text(
                                        "+${exEmoji}${exLabel}",
                                        style = TextStyle(fontSize = 7.sp, color = ColorProvider(
                                            myExtraConfig.color.copy(alpha = 0.85f),
                                            myExtraConfig.color.copy(alpha = 0.85f)
                                        )),
                                        maxLines = 1
                                    )
                                }
                                // 아이콘 표시 (감정/메모/할일)
                                val icons = buildString {
                                    if (mood != null) append(mood)
                                    if (hasMemo) append("📝")
                                    if (todos.isNotEmpty()) {
                                        append(if (todos.all { it.startsWith("✅") }) "✅" else "⬜")
                                    }
                                }
                                if (icons.isNotBlank()) {
                                    Text(icons, style = TextStyle(fontSize = 7.sp), maxLines = 1)
                                }

                                Spacer(modifier = GlanceModifier.defaultWeight())

                                // 상대 근무 (셀 하단에 컬러 스트립)
                                if (hasPartner && (pEmoji.isNotBlank() || pLabel.isNotBlank())) {
                                    val pStripBg = pBgColor?.copy(alpha = 0.35f) ?: Color.Transparent
                                    val pFontColor = pBgColor?.copy(alpha = 0.9f) ?: muted
                                    Box(
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .cornerRadius(4.dp)
                                            .background(ColorProvider(pStripBg, pStripBg))
                                            .padding(vertical = 1.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "${pEmoji}${pLabel}",
                                            style = TextStyle(fontSize = 7.sp, fontWeight = FontWeight.Bold, color = ColorProvider(pFontColor, pFontColor)),
                                            maxLines = 1
                                        )
                                    }
                                } else if (hasPartner) {
                                    Box(
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .cornerRadius(4.dp)
                                            .background(ColorProvider(emptyCellBg, emptyCellBg))
                                            .padding(vertical = 1.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("·", style = TextStyle(fontSize = 7.sp, color = ColorProvider(muted.copy(alpha = 0.3f), muted.copy(alpha = 0.3f))))
                                    }
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
