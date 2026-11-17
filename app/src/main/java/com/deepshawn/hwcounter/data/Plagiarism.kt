package com.deepshawn.hwcounter.data

/**
 * 抄袭关系（三端同一算法，见网页 analytics.js、iPad Models.swift）。
 * 每条记录 = 某次作业里“抄袭者抄了提供者的”；只统计双方状态仍是“未交”的记录（改成其他状态即视为取消）。
 * 关系图：提供者 → 抄袭者的有向边，边上是次数；连在一起的同学组成一个“抄袭圈”。
 */
object PlagiarismGraph {
    data class Case(val assignment: Assignment, val receiver: Student, val providers: List<Student>)
    data class Edge(val provider: Student, val receiver: Student, val assignments: List<Assignment>) {
        val count: Int get() = assignments.size
    }
    data class Node(
        val student: Student,
        /** 抄别人几次 / 提供给别人几次（按作业计） */
        val copied: Int, val provided: Int,
        /** 涉及抄袭的作业共扣了多少分（含翻倍、累犯加重，按当前规则） */
        val points: Double,
        /** 本轮（最近一次重置后）涉及抄袭的次数 */
        val roundTimes: Int,
    ) {
        val total: Int get() = copied + provided
        val role: String get() = when { copied > 0 && provided > 0 -> "既抄又提供"; copied > 0 -> "抄袭者"; else -> "提供者" }
    }
    data class Report(val cases: List<Case>, val edges: List<Edge>, val nodes: List<Node>, val clusters: List<List<Student>>) {
        val copiers: Int get() = nodes.count { it.copied > 0 }
        val providers: Int get() = nodes.count { it.provided > 0 }
        val points: Double get() = nodes.sumOf { it.points }
    }

    fun report(c: SchoolClass, rules: ScoreRules): Report {
        val byId = c.students.associateBy { it.id }
        val sorted = c.assignments.sortedWith(compareBy<Assignment> { it.createdAt }.thenBy { it.id })
        val cases = ArrayList<Case>()
        val edges = LinkedHashMap<Pair<String, String>, MutableList<Assignment>>()
        for (a in sorted) for ((rid, _) in a.plagiarism) {
            val r = byId[rid] ?: continue
            val ps = a.copiedFrom(rid).filter { (a.statuses[it] ?: HwStatus.NONE) == HwStatus.MISSING }.mapNotNull { byId[it] }
            if (ps.isEmpty()) continue
            cases += Case(a, r, ps)
            for (p in ps) edges.getOrPut(p.id to r.id) { ArrayList() } += a
        }
        val involved = (edges.keys.flatMap { listOf(it.first, it.second) }).distinct()
        val last = c.latestRecharges()
        val nodes = involved.mapNotNull { id ->
            val s = byId[id] ?: return@mapNotNull null
            val copied = sorted.count { it.copiedFrom(id).any { p -> (it.statuses[p] ?: HwStatus.NONE) == HwStatus.MISSING } }
            val provided = sorted.count { a -> a.copiedBy(id).isNotEmpty() }
            val det = c.autoPointDetails(rules, id)
            val list = sorted.filter { it.plagiarized(id) }
            Node(s, copied, provided, list.sumOf { det[it.id]?.points ?: 0.0 }, list.count { a -> last[id]?.counts(a.createdAt) ?: true })
        }.sortedWith(compareByDescending<Node> { it.total }.thenBy { it.points }.thenBy { it.student.no })
        // 抄袭圈：无向连通
        val adj = HashMap<String, MutableSet<String>>()
        for ((k, _) in edges) { adj.getOrPut(k.first) { HashSet() } += k.second; adj.getOrPut(k.second) { HashSet() } += k.first }
        val seen = HashSet<String>(); val clusters = ArrayList<List<Student>>()
        for (id in involved) {
            if (!seen.add(id)) continue
            val comp = ArrayList<String>(); val stack = ArrayDeque(listOf(id))
            while (stack.isNotEmpty()) { val x = stack.removeLast(); comp += x; adj[x].orEmpty().forEach { if (seen.add(it)) stack += it } }
            clusters += comp.mapNotNull { byId[it] }.sortedWith { a, b -> compareStudentNumbers(a.no, b.no) }
        }
        return Report(
            cases.asReversed(),
            edges.map { (k, l) -> Edge(byId.getValue(k.first), byId.getValue(k.second), l) }.sortedWith(compareByDescending<Edge> { it.count }.thenByDescending { it.assignments.last().createdAt }),
            nodes, clusters.sortedByDescending { it.size },
        )
    }
}
