package com.deepshawn.hwcounter.data

import kotlin.random.Random

object DemoData {
    private val surnames = "王李张刘陈杨黄赵吴周徐孙马朱胡郭何林罗高郑梁谢宋唐许韩冯邓曹彭曾萧田董潘袁蔡蒋余于杜叶程魏苏吕丁任沈姚卢姜崔钟谭陆汪范金石廖贾夏韦付方白邹孟熊秦邱江尹薛闫段雷侯龙史陶黎贺顾毛郝龚邵万钱严覃武戴莫孔向汤".toList()
    private val given = listOf(
        "子涵", "欣怡", "梓萱", "浩然", "宇轩", "雨桐", "思远", "一诺", "沐阳", "若曦",
        "俊杰", "诗琪", "博文", "佳怡", "天佑", "可馨", "嘉懿", "皓轩", "梦瑶", "泽宇",
        "语嫣", "明哲", "晨曦", "子墨", "安琪", "睿", "辰", "悦", "航", "瑾",
        "思彤", "家豪", "雅琳", "宇航", "雨萱", "铭泽", "芷若", "景行", "知夏", "云帆",
    )

    fun students(count: Int = 42, seed: Int = 2024): List<Student> {
        val r = Random(seed)
        val used = HashSet<String>()
        return (1..count).map { i ->
            var name: String
            do {
                name = surnames[r.nextInt(surnames.size)] + given[r.nextInt(given.size)]
            } while (!used.add(name))
            Student(
                no = "2024%02d".format(i),
                name = name,
                group = "第${(i - 1) / 7 + 1}组",
                gender = if (r.nextBoolean()) "男" else "女",
            )
        }
    }

    fun demoClass(): SchoolClass {
        val st = students()
        val r = Random(7)
        val day = 24L * 3600 * 1000
        val now = System.currentTimeMillis()
        val subjects = listOf("数学", "语文", "英语", "物理", "数学", "英语", "化学")
        val titles = listOf("练习册 P32-35", "作文《我的老师》", "Unit 3 单词抄写", "第二章课后习题", "周末试卷", "阅读理解专练", "实验报告")
        val assignments = subjects.indices.map { k ->
            val statuses = HashMap<String, HwStatus>()
            st.forEachIndexed { idx, s ->
                val x = r.nextInt(100)
                statuses[s.id] = when {
                    idx == 5 && k < 3 -> HwStatus.LEAVE
                    x < 8 -> HwStatus.MISSING
                    x < 14 -> HwStatus.LATE
                    x < 24 -> HwStatus.EXCELLENT
                    else -> HwStatus.SUBMITTED
                }
            }
            Assignment(
                title = titles[k], subject = subjects[k],
                createdAt = now - (subjects.size - k) * day,
                statuses = statuses,
            )
        } + Assignment(title = "今日作业：课时练 第5课", subject = "数学", createdAt = now)
        val scores = listOf(
            ScoreEntry(studentId = st[0].id, delta = 2.0, reason = "课堂表现好"),
            ScoreEntry(studentId = st[3].id, delta = 1.0, reason = "主动回答"),
            ScoreEntry(studentId = st[9].id, delta = -1.0, reason = "迟到"),
        )
        return SchoolClass(name = "高一(3)班 · 示例", students = st, assignments = assignments, scores = scores, emoji = "🚀")
    }
}
