package com.whispersong.jetbrains.filedesc.config

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class SettingsConfigIOTest {
    @Test
    fun `exportProjectConfig writes project file format`() {
        val content = SettingsConfigIO.exportProjectConfig(
            customHeaderComment = FileDescSettings.DEFAULT_HEADER,
            ignorePaths = ".git/*\nbuild/*",
            timeFormat = "yyyy/MM/dd",
            compactComment = true
        )

        val root = Json.decodeFromString<FileDescriptionRoot>(content)

        assertEquals("Copyright (c) \${now_year} \${git_name}. All rights reserved.", root.fileheader.copyright)
        assertEquals("auto:vcs", root.fileheader.customMade["Author"])
        assertEquals(listOf(".git/*", "build/*"), root.fileheader.ignorePaths)
        assertEquals("yyyy/MM/dd", root.fileheader.timeFormat)
        assertEquals(true, root.fileheader.compactComment)
    }

    @Test
    fun `importProjectConfig maps project file into settings fields`() {
        val imported = SettingsConfigIO.importProjectConfig(
            """
            {
              "fileheader": {
                "customMade": {
                  "Author": "Alice",
                  "Description": "Demo"
                },
                "copyright": "Copyright 2026 Alice",
                "ignorePaths": ["dist/*", "generated/*"],
                "timeFormat": "yyyy/MM/dd",
                "compactComment": true
              }
            }
            """.trimIndent()
        )

        assertContains(imported.customHeaderComment, "Copyright 2026 Alice")
        assertContains(imported.customHeaderComment, "\"Author\": \"Alice\"")
        assertContains(imported.customHeaderComment, "\"Description\": \"Demo\"")
        assertEquals("dist/*\ngenerated/*", imported.ignorePaths)
        assertEquals("yyyy/MM/dd", imported.timeFormat)
        assertEquals(true, imported.compactComment)
    }
}
