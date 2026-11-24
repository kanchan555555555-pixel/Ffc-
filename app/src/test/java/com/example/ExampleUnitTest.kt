package com.example

import com.example.data.FormatUtils
import com.example.localization.AppLanguage
import com.example.localization.stringsFor
import com.example.network.QrCodeGenerator
import com.example.network.TvWebPortalHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun formatUtils_formatsBytesSpeedAndDuration() {
        assertEquals("0 B", FormatUtils.formatBytes(0))
        assertEquals("1.50 MB", FormatUtils.formatBytes(1536L * 1024L))
        assertEquals("25.0 MB/s", FormatUtils.formatSpeed(25L * 1024L * 1024L))
        assertEquals("02:05", FormatUtils.formatDuration(125_000L))
    }

    @Test
    fun tvWebPortal_rendersOfflineReceiverAndPinGate() {
        val openHtml = TvWebPortalHtml.render("Pixel Phone", pinRequired = false)
        assertTrue(openHtml.contains("TV Beam Receiver"))
        assertTrue(openHtml.contains("Pixel Phone"))
        assertTrue(openHtml.contains("Save All to TV Storage"))
        assertTrue(openHtml.contains("Auto-Save Incoming: ON"))
        assertTrue(openHtml.contains("triggerAutoReceive"))
        assertTrue(openHtml.contains("let needPin = false;"))

        val pinHtml = TvWebPortalHtml.render("MyPhone", pinRequired = true)
        assertTrue(pinHtml.contains("let needPin = true;"))
        assertTrue(pinHtml.contains("Enter 4-Digit TV PIN"))
    }

    @Test
    fun wifiQrPayload_escapesSpecialChars() {
        val payload = QrCodeGenerator.buildWifiQrPayload("TV;Beam", "pass:123")
        assertEquals("WIFI:T:WPA;S:TV\\;Beam;P:pass\\:123;;", payload)
    }

    @Test
    fun bilingualStrings_provideBengaliAndEnglish() {
        val bn = stringsFor(AppLanguage.BENGALI)
        val en = stringsFor(AppLanguage.ENGLISH)
        assertEquals("TV Beam", bn.appTitle)
        assertEquals("TV Beam", en.appTitle)
        assertTrue(bn.pickVideosPhotos.isNotBlank())
        assertTrue(en.pickVideosPhotos.isNotBlank())
    }
}
