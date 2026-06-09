package com.whispersong.jetbrains.filedesc.config

import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProjectConfigTest {
    @Test
    fun `parseHeaderJson accepts line comments`() {
        val config = ProjectConfig.parseHeaderJson(
            """
            {
              // author comes from VCS
              "Author": "auto:vcs",
              "Description": "demo"
            }
            """.trimIndent()
        )

        assertNotNull(config)
        assertEquals("auto:vcs", config["Author"]?.jsonPrimitive?.content)
        assertEquals("demo", config["Description"]?.jsonPrimitive?.content)
    }

    @Test
    fun `parseHeaderJson rejects invalid json`() {
        val config = ProjectConfig.parseHeaderJson("""{"Author": """)

        assertNull(config)
    }
}
