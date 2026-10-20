package com.deepshawn.hwcounter.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * 灵活的名单导入：
 *  1. [{"学号":"202401","姓名":"张三","小组":"一组"}, ...]
 *  2. {"className":"高一3班","students":[{"no":"202401","name":"张三"}]}
 *  3. {"202401":"张三","202402":"李四"}
 *  4. CSV/TXT：每行 “学号,姓名[,小组]”
 */
object RosterImport {
    data class Result(val className: String?, val students: List<Student>)

    private val noKeys = listOf("no", "id", "studentNo", "student_no", "studentId", "student_id", "number", "num", "code", "学号", "编号", "考号")
    private val nameKeys = listOf("name", "studentName", "student_name", "姓名", "名字")
    private val groupKeys = listOf("group", "team", "小组", "组", "组别")
    private val genderKeys = listOf("gender", "sex", "性别")
    private val noteKeys = listOf("note", "remark", "备注")
    private val listKeys = listOf("students", "list", "data", "members", "学生", "名单")
    private val classKeys = listOf("className", "class", "name", "班级", "班级名称")

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(text: String): Result {
        val t = text.trim().removePrefix("﻿")
        if (t.startsWith("{") || t.startsWith("[")) {
            return parseJson(json.parseToJsonElement(t))
        }
        return parseCsv(t)
    }

    private fun JsonElement.str(): String? = (this as? JsonPrimitive)?.contentOrNull?.trim()

    private fun JsonObject.pick(keys: List<String>): String? {
        for (k in keys) {
            val hit = entries.firstOrNull { it.key.equals(k, ignoreCase = true) }
            val v = hit?.value?.str()
            if (!v.isNullOrBlank()) return v
        }
        return null
    }

    private fun parseJson(root: JsonElement): Result {
        var className: String? = null
        val arr: JsonArray? = when (root) {
            is JsonArray -> root
            is JsonObject -> {
                val listEntry = root.entries.firstOrNull { e -> listKeys.any { it.equals(e.key, true) } && e.value is JsonArray }
                if (listEntry != null) {
                    className = root.pick(classKeys)
                    listEntry.value as JsonArray
                } else null
            }
            else -> null
        }
        if (arr != null) {
            val list = arr.mapNotNull { el ->
                when (el) {
                    is JsonObject -> {
                        val no = el.pick(noKeys)
                        val name = el.pick(nameKeys)
                        if (no == null && name == null) null
                        else Student(
                            no = no ?: "",
                            name = name ?: no ?: "",
                            group = el.pick(groupKeys) ?: "",
                            gender = el.pick(genderKeys) ?: "",
                            note = el.pick(noteKeys) ?: "",
                        )
                    }
                    is JsonPrimitive -> el.str()?.let { s -> lineToStudent(s) }
                    is JsonArray -> {
                        val parts = el.mapNotNull { it.str() }
                        if (parts.isEmpty()) null else partsToStudent(parts)
                    }
                }
            }
            return Result(className, list.withFallbackNumbers())
        }
        // 形式 3：学号 -> 姓名 映射
        if (root is JsonObject) {
            val list = root.entries.mapNotNull { (k, v) -> v.str()?.let { Student(no = k.trim(), name = it) } }
            return Result(null, list)
        }
        return Result(null, emptyList())
    }

    private fun parseCsv(text: String): Result {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val list = lines.mapNotNull { lineToStudent(it) }
            .filterNot { it.no == "学号" || it.no.equals("no", true) || it.name == "姓名" }
        return Result(null, list.withFallbackNumbers())
    }

    private fun lineToStudent(line: String): Student? {
        val parts = line.split(',', '，', '\t', ';', '；', ' ').map { it.trim().trim('"') }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        return partsToStudent(parts)
    }

    private fun partsToStudent(parts: List<String>): Student {
        if (parts.size == 1) return Student(no = "", name = parts[0])
        // 自动判断哪一列是学号（含数字的那列）
        val a = parts[0]
        val b = parts[1]
        val aIsNo = a.any { it.isDigit() }
        val no = if (aIsNo) a else b
        val name = if (aIsNo) b else a
        return Student(no = no, name = name, group = parts.getOrNull(2) ?: "")
    }

    private fun List<Student>.withFallbackNumbers(): List<Student> =
        mapIndexed { i, s -> if (s.no.isBlank()) s.copy(no = (i + 1).toString().padStart(2, '0')) else s }

    const val TEMPLATE = """{
  "className": "高一(3)班",
  "students": [
    { "no": "202401", "name": "张三", "group": "第1组", "gender": "男" },
    { "no": "202402", "name": "李四", "group": "第1组", "gender": "女" },
    { "no": "202403", "name": "王五", "group": "第2组" }
  ]
}"""
}
