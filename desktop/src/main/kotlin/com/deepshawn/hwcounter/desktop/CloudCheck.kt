package com.deepshawn.hwcounter.desktop

/** 自检：在打包后的运行时里读取一次公开数据（检查 HTTPS 与 JSON 解析），不需要界面 */
fun main() {
    val d = kotlinx.coroutines.runBlocking { Cloud.pullPublic() }
    println(if (d == null) "公开数据：老师还没有开放" else "公开数据：${d.classes.size} 个班级，${d.classes.sumOf { it.students.size }} 名学生，${d.classes.sumOf { it.assignments.size }} 次作业")
}
