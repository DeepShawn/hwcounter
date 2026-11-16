package com.deepshawn.hwcounter

import com.deepshawn.hwcounter.update.Updater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test fun versionCompare() {
        assertTrue(Updater.isNewer("v2.8.0", "2.7.0"))
        assertTrue(Updater.isNewer("2.10.0", "2.9.9"))
        assertTrue(Updater.isNewer("3.0", "2.99.1"))
        assertFalse(Updater.isNewer("v2.7.0", "2.7.0"))
        assertFalse(Updater.isNewer("2.6.9", "2.7.0"))
        assertFalse(Updater.isNewer("2.7", "2.7.0"))
    }

    @Test fun parseRelease() {
        val json = """
            {"tag_name":"v2.8.0","html_url":"https://github.com/x/y/releases/tag/v2.8.0",
             "body":"### v2.8.0\n- 自动更新\n- 自动下载\n\n### v2.7.0\n- 旧的",
             "assets":[{"name":"HwCounter.ipa","browser_download_url":"https://e/a.ipa","size":5},
                       {"name":"HwCounter-arm64-v8a-abc1234.apk","browser_download_url":"https://e/b.apk","size":123456}]}
        """.trimIndent()
        val r = Updater.parse(json)
        assertEquals("2.8.0", r.version)
        assertEquals("https://e/b.apk", r.apkUrl)
        assertEquals("HwCounter-arm64-v8a-abc1234.apk", r.apkName)
        assertEquals(123456L, r.size)
        assertEquals("### v2.8.0\n- 自动更新\n- 自动下载", r.notes)
    }

    @Test fun noApk() {
        val r = Updater.parse("""{"tag_name":"v9.0.0","body":"","assets":[]}""")
        assertEquals(null, r.apkUrl)
    }
}
