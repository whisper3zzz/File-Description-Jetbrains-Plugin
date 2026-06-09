package com.whispersong.jetbrains.filedesc.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class FileUtilTest {
    @Test
    fun `getCommentStyle returns normal block style by default`() {
        val style = FileUtil.getCommentStyle("Example.kt", "kt", compactComment = false)

        assertNotNull(style)
        assertEquals("/**\n", style.start)
    }

    @Test
    fun `getCommentStyle returns compact block style when enabled`() {
        val style = FileUtil.getCommentStyle("Example.kt", "kt", compactComment = true)

        assertNotNull(style)
        assertEquals("/*\n", style.start)
    }

    @Test
    fun `env file names are treated as env extension`() {
        assertEquals("env", FileUtil.getFileExtension(".env.local", null))
    }
}
