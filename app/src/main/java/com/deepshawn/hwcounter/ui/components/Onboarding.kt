package com.deepshawn.hwcounter.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 新手引导的一页 */
data class GuideStep(val emoji: String, val title: String, val body: String, val tips: List<String> = emptyList(), val colors: List<Color>)

val GuideSteps = listOf(
    GuideStep(
        "📚", "欢迎使用作业通", "收作业、记积分、看统计，一个 App 搞定。安卓、iPad、网页三端数据自动同步。",
        listOf("底栏五个页面：首页、名单、积分、统计、设置", "随时可以在 设置 → 新手引导 再看一遍"), listOf(Color(0xFF3482FF), Color(0xFF6E5BFF)),
    ),
    GuideStep(
        "✅", "登记作业", "首页点“新建作业”，进入作业页登记每位同学的情况。",
        listOf("点一下同学：已交 ↔ 未登记", "长按同学：优秀、迟交、未交、请假、复审不合格、加减分、备注", "“多选”可以一次登记一批；还能拍照识别、语音登记、实时扫描、数本数"),
        listOf(Color(0xFF12C2A0), Color(0xFF2F8FEA)),
    ),
    GuideStep(
        "🧮", "积分自动计算", "登记完积分自动算好，每一笔都能在“积分流水”里查到。",
        listOf("未交连续加倍扣分，复审不合格再翻倍", "连续按时交满 5 次后每次 +1", "考试按得分率加减，组长奖惩、小组排名按周期自动计入", "积分页可以手动加减分、积分重置、每轮结算"),
        listOf(Color(0xFFFFA62B), Color(0xFFFF5E62)),
    ),
    GuideStep(
        "👥", "小组、组长和课代表", "在名单里给同学填写小组；在小组里长按同学设为组长或课代表。",
        listOf("统计 → 小组：综合分对抗排名、每周对抗、两组对决", "小组排名按名次给整组每人加减分，组长按小组扣分奖惩", "这两项都可以在 设置 → 作业计分 里关闭，或改成仅加分 / 仅扣分"),
        listOf(Color(0xFF9B59B6), Color(0xFF3498DB)),
    ),
    GuideStep(
        "📈", "统计与报告", "统计页可以按年、季、月、周、日或自定义范围分析。",
        listOf("作业 / 考试 / 关注 / 信誉 / 股票 / 小组 / 抄袭 七个栏目", "股票 → 行情：关注度、信誉分、小组综合；股票 → 炒股：用模拟币买卖", "右上角分享：一键导出 PDF 分析报告、Excel"),
        listOf(Color(0xFF4C8C3F), Color(0xFF1F5E54)),
    ),
    GuideStep(
        "☁️", "同步与设置", "设置 → 账号与云同步：登录后改动自动上传，多台设备之间同步。",
        listOf("计分规则在 设置 → 作业计分 / 考试计分 / 结算规则 里调整", "网页版适合分享给同学和家长", "有新版本时会自动提示更新"),
        listOf(Color(0xFF37474F), Color(0xFF90A4AE)),
    ),
)

/** 新手引导：首次打开时显示，可左右滑动，随时“跳过” */
@Composable
fun Onboarding(onDone: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val pager = rememberPagerState { GuideSteps.size }
    val scope = rememberCoroutineScope()
    BackHandler { onDone() }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.statusBarsPadding().navigationBarsPadding().padding(20.dp).widthIn(max = 460.dp).fillMaxWidth()
                .clip(RoundedCornerShape(28.dp)).background(colors.surface),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("新手引导 ${pager.currentPage + 1}/${GuideSteps.size}", fontSize = 13.sp, color = colors.onSurfaceVariantSummary, modifier = Modifier.weight(1f))
                TextButton("跳过", onClick = onDone, minHeight = 36.dp)
            }
            HorizontalPager(pager, Modifier.fillMaxWidth().height(400.dp)) { i ->
                val st = GuideSteps[i]
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(st.colors)), contentAlignment = Alignment.Center) {
                        LineIcon(st.emoji, Color.White, size = 40.dp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(st.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(st.body, fontSize = 14.sp, color = colors.onSurfaceVariantSummary, textAlign = TextAlign.Center, lineHeight = 20.sp)
                    Spacer(Modifier.height(14.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.secondaryContainer.copy(alpha = 0.6f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        st.tips.forEach { t ->
                            Row {
                                Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(st.colors.first()))
                                Spacer(Modifier.width(10.dp))
                                Text(t, fontSize = 13.sp, lineHeight = 19.sp)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center) {
                GuideSteps.indices.forEach { i ->
                    Box(Modifier.padding(horizontal = 3.dp).size(width = if (i == pager.currentPage) 18.dp else 7.dp, height = 7.dp).clip(CircleShape)
                        .background(if (i == pager.currentPage) colors.primary else colors.onSurfaceVariantSummary.copy(alpha = 0.3f)))
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (pager.currentPage > 0) TextButton("上一步", onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, modifier = Modifier.weight(1f))
                val last = pager.currentPage == GuideSteps.lastIndex
                TextButton(
                    if (last) "开始使用" else "下一步",
                    onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}
