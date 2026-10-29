package com.deepshawn.hwcounter.data
data class Student(val id: String, val no: String, val name: String)
data class Seating(val rows: Int = 1, val cols: Int = 1, val seats: Map<String, Int> = emptyMap(), val frontAtTop: Boolean = true)
