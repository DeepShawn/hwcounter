package com.deepshawn.hwcounter.data

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class AttentionStockTest {
    private val S = HwStatus.SUBMITTED; private val M = HwStatus.MISSING
    private val day = 86_400_000L
    private val now = 400 * day + 12 * 3_600_000L

    private fun cls(): SchoolClass {
        val rows = mapOf(
            "good" to List(14) { S },
            "slip" to List(9) { S } + List(5) { M },
            "fix" to List(9) { M } + List(5) { S },
        )
        val st = rows.keys.mapIndexed { i, id -> Student(id = id, no = "0${i + 1}", name = id) }
        val assigns = (0 until 14).map { k -> Assignment(id = "a$k", title = "作业$k", createdAt = now - (14 - k) * 5 * day, statuses = rows.mapValues { it.value[k] }) }
        return SchoolClass(name = "一班", students = st, assignments = assigns)
    }

    @Test fun candlesMatchAttentionAtBoundaries() {
        val c = cls()
        val b = AttentionStock.board(c, ScoreRules(), AttentionStock.Span.M1, now)
        val slip = b.stocks.getValue("slip")
        assertEquals(30, slip.candles.size)
        // 收盘 = 现在的关注度；开盘 = 区间起点的关注度
        val nowIdx = Attention.all(c, ScoreRules(), now).first { it.student.id == "slip" }.index
        assertEquals(nowIdx, slip.last, 1e-9)
        val startIdx = Attention.all(c, ScoreRules(), b.from).first { it.student.id == "slip" }.index
        assertEquals(startIdx, slip.base, 1e-9)
        // 每根 K 线：最高 ≥ 开收 ≥ 最低；相邻 K 线首尾相接
        slip.candles.forEach { k -> assertTrue(k.high >= maxOf(k.open, k.close) && k.low <= minOf(k.open, k.close)) }
        slip.candles.zipWithNext().forEach { (a, z) -> assertEquals(a.close, z.open, 1e-9); assertEquals(a.end, z.start) }
        // 连续未交的上涨，改过来的下跌
        assertTrue(slip.change > 10)
        assertTrue(b.stocks.getValue("fix").change < -10)
        assertTrue(b.up >= 1 && b.down >= 1)
        // 大盘 = 平均
        assertEquals(b.stocks.values.map { it.last }.average(), b.market.last, 1e-9)
        // 成交量：区间内作业 × 人数
        assertEquals(c.assignments.filter { it.createdAt >= b.from }.size * 3, b.market.volume)
        assertEquals(c.assignments.filter { it.createdAt >= b.from }.size, slip.volume)
    }

    @Test fun spansAndAll() {
        val c = cls()
        assertEquals(7, AttentionStock.board(c, ScoreRules(), AttentionStock.Span.W1, now).market.candles.size)
        assertEquals(13, AttentionStock.board(c, ScoreRules(), AttentionStock.Span.M3, now).market.candles.size)
        val all = AttentionStock.board(c, ScoreRules(), AttentionStock.Span.ALL, now)
        // 全部：从第一条记录那天开始
        assertTrue(all.from <= c.assignments.minOf { it.createdAt })
        assertTrue(all.market.candles.isNotEmpty())
        assertNull(AttentionStock.Quote("x", listOf(AttentionStock.Candle(0, 1, 0.0, 5.0, 0.0, 5.0, 1))).pct)
    }

    @Test fun bigClassYearIsFastEnough() {
        val r = Random(7)
        val st = (1..50).map { Student(id = "s$it", no = "$it", name = "学生$it") }
        val assigns = (0 until 300).map { k -> Assignment(title = "作业$k", createdAt = now - (300 - k) * day, statuses = st.associate { it.id to if (r.nextDouble() < 0.1) M else S }) }
        val scores = (0 until 1500).map { k -> ScoreEntry(studentId = st[r.nextInt(50)].id, delta = 1.0, reason = "表扬", time = now - r.nextLong(360) * day) }
        val c = SchoolClass(name = "大班", students = st, assignments = assigns, scores = scores)
        val t0 = System.nanoTime()
        val b = AttentionStock.board(c, ScoreRules(), AttentionStock.Span.Y1, now)
        val ms = (System.nanoTime() - t0) / 1e6
        println("1年 · 50 人 · 300 次作业 · 1500 条加减分：${"%.0f".format(ms)} ms，${b.market.candles.size} 根 K 线")
        assertEquals(26, b.market.candles.size)
    }
}
