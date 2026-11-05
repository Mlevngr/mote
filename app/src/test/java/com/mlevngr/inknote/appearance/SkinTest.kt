package com.mlevngr.inknote.appearance

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class SkinTest {
    @Test fun defaultPalettesHaveSixIndependentHeadingColors() {
        listOf(Skin.Default.light, Skin.Default.dark).forEach { palette ->
            assertEquals(6, palette.headings.distinct().size)
            assertEquals(13, palette.values.size)
            (1..6).forEach { assertEquals(palette.headings[it - 1], palette.heading(it)) }
        }
        assertEquals(Skin.Default.light, Skin.Default.palette(false))
        assertEquals(Skin.Default.dark, Skin.Default.palette(true))
        assertThrows(IllegalArgumentException::class.java) { Skin.Default.light.heading(0) }
        assertThrows(IllegalArgumentException::class.java) { Skin.Default.light.heading(7) }
    }

    @Test fun colorParsingIsOpaqueAndLocaleIndependent() {
        assertEquals(0xFFABCDEF.toInt(), SkinColor.parse("#abcdef"))
        assertEquals("#ABCDEF", SkinColor.hex(SkinColor.parse("#abcdef")))
        listOf("#123", "red", "#00112233", "#GG2233", "#123456 ").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { SkinColor.parse(value) }
        }
        assertEquals(0xFFFFFFFF.toInt(), SkinColor.onColor(0xFF000000.toInt()))
        assertEquals(0xFF000000.toInt(), SkinColor.onColor(0xFFFFFFFF.toInt()))
    }

    @Test fun rejectsIncompletePalettesAndInvalidNames() {
        assertThrows(IllegalArgumentException::class.java) { Skin.Default.light.copy(headings = listOf(1)) }
        assertThrows(IllegalArgumentException::class.java) { Skin.Default.light.copy(background = 0) }
        assertThrows(IllegalArgumentException::class.java) { SkinPalette.fromHex(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { Skin.Default.copy(name = "  ") }
        assertThrows(IllegalArgumentException::class.java) { Skin.Default.copy(name = "a".repeat(65)) }
    }

    @Test fun jsonRoundTripsNamesAndEveryColorInBothModes() {
        val skin = Skin.Default.copy(name = "中文 \"皮肤\" \\ 😀\n测试")
        assertEquals(skin, SkinJson.decode(SkinJson.encode(skin)))
        assertEquals(Skin.Default, SkinJson.read(SkinJson.encode(Skin.Default).byteInputStream()))
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        assertEquals(Skin.Default, SkinJson.read(ByteArrayInputStream(bom + SkinJson.encode(Skin.Default).toByteArray())))
    }

    @Test fun acceptsUnicodeEscapesAndJsonWhitespace() {
        val json = SkinJson.encode(Skin.Default).replace(Skin.Default.name, "\\u6d4b\\u8bd5")
        assertEquals("测试", SkinJson.decode("\r\n\t$json \n").name)
    }

    @Test fun rejectsUnknownSchemaMissingColorsAndWrongTypes() {
        val valid = SkinJson.encode(Skin.Default)
        listOf(
            valid.replace("\"version\": 1", "\"version\": 2"),
            valid.replace("mote-skin", "other-skin"),
            valid.replace("\"version\": 1", "\"version\": \"1\""),
            valid.replace("\"name\":", "\"unknown\":"),
            valid.replace("\"background\": \"#FFFFFF\",", ""),
            valid.replace("#FFFFFF", "#FFF"),
            valid.replace("\"version\": 1", "\"version\": 1, \"extra\": 5")
        ).forEach { value -> assertThrows(RuntimeException::class.java) { SkinJson.decode(value) } }
    }

    @Test fun rejectsMalformedAndAmbiguousJson() {
        val valid = SkinJson.encode(Skin.Default)
        listOf(
            "", "[]", "{}", valid + "{}", valid.dropLast(3),
            valid.replace("\"version\": 1", "\"version\": 1, \"version\": 1"),
            valid.replace("\"version\": 1", "\"version\": 01"),
            valid.replace("\"version\": 1", "\"version\": 1.0"),
            valid.replace("\"version\": 1", "\"version\": -1"),
            valid.replace(Skin.Default.name, "bad\\x"),
            valid.replace(Skin.Default.name, "bad\nname"),
            valid.replace("\"version\": 1", "\"version\": [[[[[[1]]]]]]")
        ).forEach { value -> assertThrows(RuntimeException::class.java) { SkinJson.decode(value) } }
    }

    @Test fun boundsFileSizeAndRejectsInvalidUtf8() {
        assertThrows(IllegalArgumentException::class.java) { SkinJson.decode(" ".repeat(SkinJson.MAX_BYTES + 1)) }
        assertThrows(IllegalArgumentException::class.java) {
            SkinJson.read(ByteArrayInputStream(ByteArray(SkinJson.MAX_BYTES + 1)))
        }
        assertThrows(java.nio.charset.CharacterCodingException::class.java) {
            SkinJson.read(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28)))
        }
    }
}
