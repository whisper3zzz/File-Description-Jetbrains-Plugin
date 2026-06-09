package com.whispersong.jetbrains.filedesc.config

import java.util.Date
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class HeaderPreviewGeneratorTest {
    @Test
    fun `generate renders preview with manual author and placeholders`() {
        val preview = HeaderPreviewGenerator.generate(
            headerJson = FileDescSettings.DEFAULT_HEADER,
            timeFormat = "yyyy-MM-dd HH:mm:ss",
            manualAuthor = "whisper3zzz",
            compactComment = false,
            now = Date(0)
        )

        assertTrue(preview.startsWith("/**"))
        assertContains(preview, "Copyright (c) 1970 whisper3zzz. All rights reserved.")
        assertContains(preview, "Author: whisper3zzz <you@example.com>")
        assertContains(preview, "FilePath: \\src\\main\\kotlin\\Example.kt")
    }

    @Test
    fun `generate uses compact block start when compact mode is enabled`() {
        val preview = HeaderPreviewGenerator.generate(
            headerJson = FileDescSettings.DEFAULT_HEADER,
            timeFormat = "yyyy-MM-dd HH:mm:ss",
            manualAuthor = "",
            compactComment = true,
            now = Date(0)
        )

        assertTrue(preview.startsWith("/*\n"))
    }

    @Test
    fun `generate reports invalid json`() {
        val preview = HeaderPreviewGenerator.generate(
            headerJson = """{"Author": """,
            timeFormat = "yyyy-MM-dd HH:mm:ss",
            manualAuthor = "",
            compactComment = false,
            now = Date(0)
        )

        assertContains(preview, "JSON format is invalid")
    }
}
