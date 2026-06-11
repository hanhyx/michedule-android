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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class WidgetShiftInfo(
    val type: ShiftType?,
    val config: ShiftTypeConfig? = null,
    val hasAlba: Boolean = false,
    val extraShifts: String = ""
) {
    fun getExtraShiftList(): List<String> =
        extraShifts.split(",").filter { it.isNotBlank() }
}

data class WidgetDayDetail(
    val shift: WidgetShiftInfo,
    val memo: String? = null,
    val mood: String? = null,
    val todoTexts: List<String> = emptyList(),
    val name: String = "",
    val typeConfigMap: Map<String, ShiftTypeConfig> = emptyMap()
)

class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val todayStr = today.toString()
        val db = AppDatabase.getInstance(context)

        val (myDetail, partnerDetail) = withContext(Dispatchers.IO) {
            val myTypeConfigs = try {
                db.shiftTypeConfigDao().getAll().associateBy { it.id }
            } catch (_: Exception) { emptyMap() }
            val partnerTypeConfigs = try {
                db.shiftTypeConfigDao().getPartner().associateBy { it.id }
            } catch (_: Exception) { emptyMap() }
            val typeConfigs = ShiftTypeConfig.DEFAULTS.associateBy { it.id } + myTypeConfigs

            val myEntity = db.shiftDao().getShift(todayStr)
            val myMood = try {
                db.moodDao().getMoodForDate(todayStr).firstOrNull()
            } catch (_: Exception) { null }
            val myTodos = try {
                db.todoDao().getTodosForDate(todayStr).firstOrNull() ?: emptyList()
            } catch (_: Exception) { emptyList() }

            val my = WidgetDayDetail(
                shift = WidgetShiftInfo(
                    type = myEntity?.let { ShiftType.fromString(it.type) },
                    config = myEntity?.type?.let { typeConfigs[it] },
                    hasAlba = myEntity?.hasAlba ?: false,
                    extraShifts = myEntity?.extraShifts ?: ""
                ),
                memo = myEntity?.memo,
                mood = myMood?.emoji,
                todoTexts = myTodos.take(3).map { (if (it.isDone) "✅ " else "☐ ") + it.title },
                typeConfigMap = typeConfigs
            )

            val pTypeMap = ShiftTypeConfig.DEFAULTS.associateBy { it.id } + partnerTypeConfigs
            val partner = try {
                val friends = db.friendShiftDao().getShiftsInRange(todayStr, todayStr).firstOrNull()
                val fe = friends?.firstOrNull()
                WidgetDayDetail(
                    shift = WidgetShiftInfo(
                        type = fe?.let { ShiftType.fromString(it.type) },
                        config = fe?.type?.let { pTypeMap[it] },
                        hasAlba = fe?.hasAlba ?: false,
                        extraShifts = fe?.extraShifts ?: ""
                    ),
                    memo = fe?.memo,
                    mood = fe?.mood,
                    name = fe?.friendName ?: "",
                    typeConfigMap = pTypeMap
                )
            } catch (_: Exception) { WidgetDayDetail(WidgetShiftInfo(null)) }

            my to partner
        }

        provideContent {
            TodayWidgetContent(today, myDetail, partnerDetail)
        }
    }
}

@Composable
private fun TodayWidgetContent(
    today: LocalDate,
    myDetail: WidgetDayDetail,
    partnerDetail: WidgetDayDetail
) {
    val darkBg = Color(0xFF13131E)
    val cardBg = Color(0xFF1C1C2E)
    val accent = Color(0xFFB794F6)
    val muted = Color(0xFF6B7280)
    val albaColor = Color(0xFFFB923C)
    val divider = Color(0xFF2E2E42)
    val memoColor = Color(0xFF7DD3FC)
    val todoColor = Color(0xFF6EE7B7)

    val myConfig = myDetail.shift.config
    val myShift = myDetail.shift.type
    val myColor = myConfig?.color ?: myShift?.color ?: muted
    val myLabel = myConfig?.label ?: myShift?.label ?: "미설정"
    val myEmoji = myConfig?.emoji ?: myShift?.emoji ?: "📋"
    val myTime = myConfig?.defaultTimeRange ?: myShift?.defaultTimeRange ?: ""

    val hasPartner = partnerDetail.name.isNotBlank()

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(darkBg, darkBg))
            .cornerRadius(20.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(10.dp)
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // ── 내 영역 (7) ──
            Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                // 날짜 + 감정
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = today.format(DateTimeFormatter.ofPattern("M/d (E)")),
                        style = TextStyle(color = ColorProvider(accent, accent), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    )
                    if (myDetail.mood != null) {
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Text(myDetail.mood, style = TextStyle(fontSize = 12.sp))
                    }
                }

                Spacer(modifier = GlanceModifier.height(6.dp))

                // 내 근무 카드
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .cornerRadius(12.dp)
                        .background(ColorProvider(cardBg, cardBg))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(myEmoji, style = TextStyle(fontSize = 18.sp))
                            Spacer(modifier = GlanceModifier.width(6.dp))
                            Column {
                                Text(
                                    text = myLabel,
                                    style = TextStyle(
                                        color = ColorProvider(myColor, myColor),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                if (myTime.isNotBlank()) {
                                    Text(
                                        myTime,
                                        style = TextStyle(
                                            color = ColorProvider(muted, muted),
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                            }
                        }

                        val myExtras = myDetail.shift.getExtraShiftList()
                        val myDisplayExtras = myExtras.ifEmpty { if (myDetail.shift.hasAlba) listOf("alba") else emptyList() }
                        myDisplayExtras.take(2).forEach { extraId ->
                            val ec = myDetail.typeConfigMap[extraId]
                            val ecEmoji = ec?.emoji ?: "💼"
                            val ecLabel = ec?.label ?: extraId
                            val ecColor = ec?.color ?: albaColor
                            Spacer(modifier = GlanceModifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(ecEmoji, style = TextStyle(fontSize = 10.sp))
                                Spacer(modifier = GlanceModifier.width(3.dp))
                                Text(
                                    ecLabel,
                                    style = TextStyle(
                                        color = ColorProvider(ecColor, ecColor),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = GlanceModifier.height(4.dp))

                // 메모
                if (!myDetail.memo.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📝", style = TextStyle(fontSize = 9.sp))
                        Spacer(modifier = GlanceModifier.width(3.dp))
                        Text(
                            myDetail.memo.take(20),
                            style = TextStyle(color = ColorProvider(memoColor, memoColor), fontSize = 9.sp)
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(2.dp))
                }

                // 할일
                myDetail.todoTexts.forEach { todo ->
                    Text(
                        todo.take(18),
                        style = TextStyle(color = ColorProvider(todoColor, todoColor), fontSize = 9.sp)
                    )
                }
            }

            // ── 상대 영역 (3) ──
            if (hasPartner) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(ColorProvider(divider, divider))
                ) {}
                Spacer(modifier = GlanceModifier.height(4.dp))

                val pConfig = partnerDetail.shift.config
                val pShift = partnerDetail.shift.type
                val pColor = pConfig?.color ?: pShift?.color ?: muted
                val pLabel = pConfig?.label ?: pShift?.label ?: "미설정"
                val pEmoji = pConfig?.emoji ?: pShift?.emoji ?: "📋"
                val pTime = pConfig?.defaultTimeRange ?: pShift?.defaultTimeRange ?: ""

                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .cornerRadius(12.dp)
                        .background(ColorProvider(cardBg, cardBg))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "💕",
                            style = TextStyle(fontSize = 10.sp)
                        )
                        Spacer(modifier = GlanceModifier.width(4.dp))
                        Text(
                            partnerDetail.name,
                            style = TextStyle(color = ColorProvider(accent, accent), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        )
                        if (partnerDetail.mood != null) {
                            Spacer(modifier = GlanceModifier.width(4.dp))
                            Text(partnerDetail.mood, style = TextStyle(fontSize = 10.sp))
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(4.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(pEmoji, style = TextStyle(fontSize = 16.sp))
                        Spacer(modifier = GlanceModifier.width(6.dp))
                        Column {
                            Text(
                                pLabel,
                                style = TextStyle(
                                    color = ColorProvider(pColor, pColor),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            if (pTime.isNotBlank()) {
                                Text(
                                    pTime,
                                    style = TextStyle(
                                        color = ColorProvider(muted, muted),
                                        fontSize = 8.sp
                                    )
                                )
                            }
                        }
                    }

                    val pExtras = partnerDetail.shift.getExtraShiftList()
                    val pDisplayExtras = pExtras.ifEmpty { if (partnerDetail.shift.hasAlba) listOf("alba") else emptyList() }
                    pDisplayExtras.take(2).forEach { extraId ->
                        val ec = partnerDetail.typeConfigMap[extraId]
                        val ecEmoji = ec?.emoji ?: "💼"
                        val ecLabel = ec?.label ?: extraId
                        val ecColor = ec?.color ?: albaColor
                        Spacer(modifier = GlanceModifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(ecEmoji, style = TextStyle(fontSize = 9.sp))
                            Spacer(modifier = GlanceModifier.width(3.dp))
                            Text(
                                ecLabel,
                                style = TextStyle(
                                    color = ColorProvider(ecColor, ecColor),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    // 상대 메모
                    if (!partnerDetail.memo.isNullOrBlank()) {
                        Spacer(modifier = GlanceModifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📝", style = TextStyle(fontSize = 8.sp))
                            Spacer(modifier = GlanceModifier.width(3.dp))
                            Text(
                                partnerDetail.memo.take(18),
                                style = TextStyle(color = ColorProvider(memoColor, memoColor), fontSize = 8.sp)
                            )
                        }
                    }
                }
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
