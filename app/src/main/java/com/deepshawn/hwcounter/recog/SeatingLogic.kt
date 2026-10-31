package com.deepshawn.hwcounter.recog

import com.deepshawn.hwcounter.data.Seating
import com.deepshawn.hwcounter.data.Student
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 座位表：导入（文字 / 图片识别结果）、自动排列、交换、旋转。与网页版 seating.js、iPad 版同一套规则。
 */
object SeatingLogic {
    /** 图片识别出的一段文字（图片像素坐标）；charX 为每个字的横向中心（可选） */
    data class Piece(val text: String, val x: Float, val y: Float, val w: Float, val h: Float, val charX: List<Float>? = null)

    data class Result(val seating: Seating, val unmatched: List<String>, val unseated: List<String>)

    private val EMPTY = setOf("", "空", "空位", "-", "—", "－", "–", "x", "X", "×", "/", "无", "0")
    private val FRONT = Regex("讲台|黑板|讲桌|前门|講台")
    private fun canon(s: String) = s.trim().uppercase().replace(Regex("\\s+"), "")

    internal class Hit(val s: Student, val at: Int, val len: Int)

    internal class SeatMatcher(students: List<Student>) {
        val byNo = students.filter { it.no.isNotBlank() }.associateBy { canon(it.no) }
        val byName = LinkedHashMap<String, Student>().apply { students.forEach { if (it.name.isNotEmpty() && it.name !in this) put(it.name, it) } }
        val names = byName.keys.filter { it.length >= 2 }.sortedByDescending { it.length }

        fun one(tok: String): Student? {
            val t = canon(tok)
            if (t.isEmpty()) return null
            byNo[t]?.let { return it }
            byName[t]?.let { return it }
            names.firstOrNull { t.contains(it) }?.let { return byName[it] }
            for (d in Regex("\\d+").findAll(t).map { it.value }) {
                byNo[d]?.let { return it }
                val v = d.trimStart('0').ifEmpty { "0" }
                byNo.entries.firstOrNull { (k, _) -> k.all { it.isDigit() } && k.trimStart('0').ifEmpty { "0" } == v }?.let { return it.value }
            }
            return null
        }

        fun near(z: String): Student? {
            if (z.length !in 2..4) return null
            val c = names.filter { n -> n.length == z.length && n[0] == z[0] && n.indices.count { n[it] != z[it] } == 1 }
            return if (c.size == 1) byName[c[0]] else null
        }

        /** 一段文字里依次出现的所有学生（OCR 把同一排几个人读成一行时用） */
        fun all(t: String): List<Hit> {
            val used = BooleanArray(t.length)
            val out = ArrayList<Hit>()
            for (n in names) {
                var from = 0
                while (true) {
                    val i = t.indexOf(n, from)
                    if (i < 0) break
                    if ((i until i + n.length).none { used[it] }) {
                        for (k in i until i + n.length) used[k] = true
                        out += Hit(byName.getValue(n), i, n.length)
                    }
                    from = i + 1
                }
            }
            for (m in Regex("\\d+").findAll(t)) {
                val i = m.range.first
                if (used[i]) continue
                // “高一(3)班”“第3排”这类不是学号
                val prev = t.getOrNull(i - 1)
                val next = t.getOrNull(m.range.last + 1)
                if ((prev != null && prev in "(（第") || (next != null && next in ")）班级年排列组")) continue
                val s = one(m.value) ?: continue
                if (out.none { it.s.id == s.id }) out += Hit(s, i, m.value.length)
            }
            if (out.isEmpty()) {
                val z = t.filter { it in '一'..'鿿' }
                near(z)?.let { out += Hit(it, max(0, t.indexOf(z.firstOrNull() ?: ' ')), z.length) }
            }
            return out.sortedBy { it.at }
        }
    }

    fun gridToSeating(grid: List<List<String?>>, frontAtTop: Boolean): Seating {
        val cols = max(1, grid.maxOfOrNull { it.size } ?: 1)
        val seats = HashMap<String, Int>()
        grid.forEachIndexed { i, r -> r.forEachIndexed { j, id -> if (id != null) seats[id] = i * cols + j } }
        return Seating(max(1, grid.size), cols, seats, frontAtTop)
    }

    fun toGrid(seating: Seating, students: List<Student>): List<List<String?>> {
        val ok = students.map { it.id }.toSet()
        val grid = List(seating.rows) { arrayOfNulls<String>(seating.cols) }
        for ((id, k) in seating.seats) {
            if (id !in ok || seating.cols <= 0) continue
            val r = k / seating.cols
            val c = k % seating.cols
            if (r in 0 until seating.rows && grid[r][c] == null) grid[r][c] = id
        }
        return grid.map { it.toList() }
    }

    private fun splitLine(line: String): List<String> =
        if (Regex("[\t,，;；|]").containsMatchIn(line)) line.split(Regex("[\t,，;；|]")) else line.trim().split(Regex("\\s+"))

    /**
     * 文字座位表：每行一排，单元格用 Tab/逗号/空格分隔，空位写“空”或留空；
     * 单元格写姓名或学号都行；有“讲台”那一行时据此判断讲台在上还是在下。
     */
    fun parseText(text: String, students: List<Student>): Result {
        val rows = ArrayList<List<String>>()
        var frontAtTop = true
        for (raw in text.removePrefix("﻿").trim().lines()) {
            val line = raw.trimEnd()
            if (line.isBlank()) continue
            val compact = line.replace(Regex("\\s"), "")
            if (FRONT.containsMatchIn(compact) && line.replace(Regex("[\\s,，;；|\\-—_=*·【】\\[\\]()（）]"), "").length <= 4) {
                frontAtTop = rows.isEmpty(); continue
            }
            rows += splitLine(line).map { it.trim().trim('"', '\'') }
        }
        return parseRows(rows, frontAtTop, students)
    }

    /** 二维单元格（如 JSON 解析得到）→ 座位表 */
    fun parseRows(rows: List<List<String>>, frontAtTop: Boolean, students: List<Student>): Result {
        val m = SeatMatcher(students)
        val placed = HashSet<String>()
        val unmatched = ArrayList<String>()
        val grid = rows.map { r ->
            r.map { cell ->
                if (cell.trim() in EMPTY) null
                else {
                    val s = m.one(cell)
                    if (s == null || s.id in placed) { unmatched += cell; null } else { placed += s.id; s.id }
                }
            }
        }
        return finish(grid, frontAtTop, students, unmatched)
    }

    private fun finish(g0: List<List<String?>>, frontAtTop: Boolean, students: List<Student>, unmatched: List<String>): Result {
        val g = g0.toMutableList()
        while (g.isNotEmpty() && g.last().all { it == null }) g.removeAt(g.lastIndex)
        while (g.isNotEmpty() && g.first().all { it == null }) g.removeAt(0)
        val cols = g.maxOfOrNull { it.size } ?: 0
        val grid = g.map { it + List(cols - it.size) { null } }
        val seating = gridToSeating(grid, frontAtTop)
        return Result(seating, unmatched, students.filter { it.id !in seating.seats }.map { it.id })
    }

    /**
     * 图片识别结果 → 座位表：先把每段文字里的姓名/学号定位，再按纵向间隙分排（纠正轻微倾斜）、按横向位置聚成列。
     */
    fun fromBoxes(pieces: List<Piece>, students: List<Student>): Result {
        val m = SeatMatcher(students)
        class P(val id: String, val cx: Float, val cy: Float, val w: Float, val h: Float) { var ry = 0f }
        val pts = ArrayList<P>()
        var front: Float? = null
        val unmatched = ArrayList<String>()
        for (p in pieces) {
            val compact = p.text.replace(Regex("\\s"), "")
            if (FRONT.containsMatchIn(compact) && compact.length <= 6) { front = p.y + p.h / 2; continue }
            if (Regex("座位表|座次表|座位图").containsMatchIn(p.text)) continue
            val hits = m.all(p.text)
            if (hits.isEmpty()) {
                if (Regex("[一-鿿]{2,}|\\d{2,}").containsMatchIn(p.text)) unmatched += p.text.trim()
                continue
            }
            val len = max(1, p.text.length)
            for (h in hits) {
                val cx = p.charX?.takeIf { it.size >= h.at + h.len }?.subList(h.at, h.at + h.len)?.average()?.toFloat()
                    ?: (p.x + p.w * ((h.at + h.len / 2f) / len))
                pts += P(h.s.id, cx, p.y + p.h / 2, p.w * h.len / len, p.h)
            }
        }
        val seen = HashSet<String>()
        val pp = pts.filter { seen.add(it.id) }
        if (pp.isEmpty()) return Result(Seating(1, 1, emptyMap(), true), unmatched, students.map { it.id })
        fun med(a: List<Float>) = a.sorted()[a.size / 2]
        val mh = med(pp.map { it.h }).takeIf { it > 0 } ?: 10f
        val mw = med(pp.map { it.w }).takeIf { it > 0 } ?: 10f
        val slope = estimateSlope(pp.map { it.cx to it.cy }, mh)
        pp.forEach { it.ry = it.cy - slope * it.cx }
        val rowsOf = cluster(pp.map { it.ry }, mh * 0.9f)
        val colsOf = cluster(pp.map { it.cx }, max(mw * 0.75f, mh))
        val nr = rowsOf.max() + 1
        val nc = colsOf.max() + 1
        val grid = List(nr) { MutableList<String?>(nc) { null } }
        pp.forEachIndexed { i, q ->
            val r = rowsOf[i]
            var c = colsOf[i]
            if (grid[r][c] != null) {
                var best = -1
                for (k in grid[r].indices) if (grid[r][k] == null && (best < 0 || abs(k - c) < abs(best - c))) best = k
                if (best < 0) { grid[r].add(null); best = grid[r].lastIndex }
                c = best
            }
            grid[r][c] = q.id
        }
        var frontAtTop = true
        front?.let { f -> val ys = pp.map { it.cy }; frontAtTop = f <= (ys.min() + ys.max()) / 2 }
        return finish(grid, frontAtTop, students, unmatched)
    }

    private fun cluster(vals: List<Float>, gap: Float): IntArray {
        val idx = vals.indices.sortedBy { vals[it] }
        val out = IntArray(vals.size)
        var g = 0
        var prev = vals[idx[0]]
        idx.forEachIndexed { k, i ->
            if (k > 0 && vals[i] - prev > gap) g++
            out[i] = g
            prev = vals[i]
        }
        return out
    }

    private fun estimateSlope(p: List<Pair<Float, Float>>, mh: Float): Float {
        val s = ArrayList<Float>()
        val byX = p.sortedBy { it.first }
        for (i in byX.indices) for (j in i + 1 until min(byX.size, i + 12)) {
            val dx = byX[j].first - byX[i].first
            val dy = byX[j].second - byX[i].second
            if (dx > mh && abs(dy) < mh * 0.8f) s += dy / dx
        }
        if (s.size < 3) return 0f
        s.sort()
        val v = s[s.size / 2]
        return if (abs(v) < 0.2f) v else 0f
    }

    /** 按学号顺序自动排列（cols 列，一排排坐满） */
    fun autoArrange(students: List<Student>, cols: Int, frontAtTop: Boolean = true): Seating {
        val list = students.sortedWith(compareBy({ it.no.length }, { it.no }))
        val c = max(1, cols)
        return Seating(max(1, (list.size + c - 1) / c), c, list.withIndex().associate { it.value.id to it.index }, frontAtTop)
    }

    /** 讲台在上/下互换：整张表旋转 180° */
    fun rotate(s: Seating): Seating {
        val n = s.rows * s.cols
        return s.copy(seats = s.seats.mapValues { n - 1 - it.value }, frontAtTop = !s.frontAtTop)
    }

    fun swap(s: Seating, a: Int, b: Int): Seating =
        s.copy(seats = s.seats.mapValues { (_, k) -> if (k == a) b else if (k == b) a else k })

    /** 把某位同学放到座位 k（原来坐在 k 的人变成未入座） */
    fun place(s: Seating, id: String, k: Int): Seating = s.copy(seats = s.seats.filterValues { it != k } + (id to k))

    fun remove(s: Seating, id: String): Seating = s.copy(seats = s.seats - id)

    fun resize(s: Seating, rows: Int, cols: Int): Seating {
        val r1 = max(1, rows)
        val c1 = max(1, cols)
        val seats = HashMap<String, Int>()
        for ((id, k) in s.seats) {
            val r = k / s.cols
            val c = k % s.cols
            if (r < r1 && c < c1) seats[id] = r * c1 + c
        }
        return s.copy(rows = r1, cols = c1, seats = seats)
    }
}
