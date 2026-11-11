package com.codeinc.flipcash.wire

import com.codeinc.flipcash.wire.messaging.v1.Content
import com.codeinc.flipcash.wire.messaging.v1.TextContent
import com.codeinc.flipcash.wire.messaging.v1.validate
import dev.bmcreations.protovalidate.ValidationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// commonTest, so it runs on every target with a test task: jvm and js (node) here.
class ValidateTest {
    @Test
    fun textContentPasses() {
        assertIs<ValidationResult.Valid>(Content(text = TextContent(text = "gm")).validate())
    }

    @Test
    fun emptyTextFails() {
        val result = assertIs<ValidationResult.Invalid>(Content(text = TextContent(text = "")).validate())
        assertEquals(listOf("text.text"), result.violations.map { it.field })
    }

    @Test
    fun emptyOneofFails() {
        val result = assertIs<ValidationResult.Invalid>(Content().validate())
        assertEquals(listOf("type"), result.violations.map { it.field })
    }
}
