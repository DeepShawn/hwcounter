package com.deepshawn.hwcounter.data

import org.junit.Assert.*
import org.junit.Test

class GroupStockTest {
    private val S = HwStatus.SUBMITTED; private val M = HwStatus.MISSING
    private val day = 86_400_000L
    private val now = 400 * day + 12 * 3_600_000L

    /** 一组一直交齐；二组前面缺交、最近交齐（上涨） */
    private fun cls(): SchoolClass {
        val st = listOf("a1", "a2", "b1", "b2").map { Student(id = it, no = it, name = it, group = if (it[0] == 'a') "一组" else "二组") }
        val asg = (0 until 12).map { k ->
            val m = st.associate { it.id to S }.toMutableMap()
            if (k < 6) m["b2"] = M
            Assignment(id = "a$k", title = "作业$k", createdAt = now - (12 - k) * 2 * day, statuses = m)
        }
        return SchoolClass(name = "一班", students = st, assignments = asg)
    }

    @Test fun pricesAreCompositeScores() {
        val c = cls()
        val b = GroupStock.board(c, ScoreRules(), AttentionStock.Span.M1, now)!!
        val a = b.stocks.getValue("一组"); val g2 = b.stocks.getValue("二组")
        // 最近 10 次：一组全交、零扣分 → 100；二组还有 4 次缺交
        assertEquals((0.5 + 0.25) / 0.9 * 100, a.last, 1e-9)   // 全交、零扣分、没有优秀
        assertTrue(g2.last < a.last)
        assertTrue(g2.change > 0)                                      // 二组在变好（上涨）
        assertEquals((a.last + g2.last) / 2, b.market.last, 1e-9)       // 大盘 = 各组平均
        assertEquals(30, a.candles.size)
        a.candles.forEach { k -> assertTrue(k.high >= maxOf(k.open, k.close) && k.low <= minOf(k.open, k.close)) }
        assertNull(GroupStock.board(SchoolClass(name = "空"), ScoreRules(), AttentionStock.Span.M1, now))
    }
}
