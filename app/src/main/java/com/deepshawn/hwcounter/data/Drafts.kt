package com.deepshawn.hwcounter.data

import kotlinx.serialization.Serializable

/**
 * 作业草稿：只保存在本机（drafts.json），不进云端快照。
 * 上传时写进作业（只覆盖草稿里改过的学生），然后立即同步到云端；也可以定时上传。
 */
@Serializable
data class Draft(
    val id: String = newId(),
    val classId: String,
    /** 目标作业；新作业草稿上传时用这个 id 创建 */
    val assignmentId: String = newId(),
    val isNew: Boolean = true,
    val title: String = "",
    val subject: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** 定时上传时间（null = 不定时） */
    val scheduledAt: Long? = null,
    val statuses: Map<String, HwStatus> = emptyMap(),
    val comments: Map<String, String> = emptyMap(),
    /** 创建草稿时作业的样子，用来算“改了谁” */
    val baseTitle: String = "",
    val baseSubject: String = "",
    val baseStatuses: Map<String, HwStatus> = emptyMap(),
    val baseComments: Map<String, String> = emptyMap(),
)
