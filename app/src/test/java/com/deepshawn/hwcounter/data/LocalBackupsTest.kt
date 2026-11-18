package com.deepshawn.hwcounter.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class LocalBackupsTest {
    private fun data(n: Int) = AppData(classes = listOf(SchoolClass(id = "c", name = "一班", students = (1..n).map { Student(id = "s$it", no = "$it", name = "同学$it") })))

    @Test fun keepsOnlyNewestFifty() {
        val dir = Files.createTempDirectory("bk").toFile()
        val b = LocalBackups(dir)
        repeat(55) { i -> b.save("i=$i", data(i + 1), LocalBackups.Reason.AUTO, now = 1_000_000L + i * 1000) }
        val l = b.list()
        assertEquals(50, l.size)
        assertEquals(1_000_000L + 54 * 1000, l.first().time) // 最新在前
        assertEquals(1_000_000L + 5 * 1000, l.last().time)   // 最旧的 5 份被删掉
        assertEquals(55, l.first().students)
        assertEquals("i=54", b.read(l.first()))
        assertEquals(50, dir.listFiles()!!.size)
    }

    @Test fun metadataAndRoundTrip() {
        val dir = Files.createTempDirectory("bk").toFile()
        val b = LocalBackups(dir)
        val text = "中文".repeat(5000)
        val e = b.save(text, data(3), LocalBackups.Reason.BEFORE_CLOUD, now = 42L)!!
        assertEquals(LocalBackups.Reason.BEFORE_CLOUD, e.reason)
        assertEquals(1, e.classes); assertEquals(3, e.students); assertEquals(0, e.assignments)
        assertEquals(text, b.read(e))
        assertTrue("应压缩", e.size < text.toByteArray().size / 5)
        // 同一毫秒两份不会互相覆盖
        b.save("x", data(1), LocalBackups.Reason.MANUAL, now = 42L)
        assertEquals(2, b.list().size)
        b.delete(b.list().first()); assertEquals(1, b.list().size)
    }

    @Test fun dueOnlyWhenChangedAndIntervalPassed() {
        val dir = Files.createTempDirectory("bk").toFile()
        val b = LocalBackups(dir, interval = 600_000L)
        assertTrue(b.due(1, null, now = 5_000_000L))
        b.save("a", data(1), LocalBackups.Reason.AUTO, now = 5_000_000L)
        assertFalse("没改动不备份", b.due(1, 1, now = 9_000_000L))
        assertFalse("不到 10 分钟不备份", b.due(2, 1, now = 5_000_000L + 599_000L))
        assertTrue(b.due(2, 1, now = 5_000_000L + 600_000L))
    }

    @Test fun ignoresForeignFiles() {
        val dir = Files.createTempDirectory("bk").toFile()
        java.io.File(dir, "readme.txt").writeText("x")
        assertEquals(0, LocalBackups(dir).list().size)
    }
}
