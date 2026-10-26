package com.deepshawn.hwcounter.data
data class Student(val id: String = no0(), val no: String, val name: String, val group: String = "")
private var c = 0
fun no0() = "s" + (c++)
