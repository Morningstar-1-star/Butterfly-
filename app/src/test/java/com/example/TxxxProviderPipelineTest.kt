package com.example

import com.example.extractor.TxxxProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TxxxProviderPipelineTest {

    @Test
    fun testBase164Decode_RealToken() {
        // Real encoded token from Txxx videofile API
        val sample = "L2dldF9maWxlLzIyLzgw\u041czZk\u041cmY3NDQ4\u041cD\u04101ZmZhODdi\u041cGQxNjZhYmNm\u041cD\u0415yNGU4YW\u041c2NTQy\u041ci8y\u041cTc4\u041cT\u0410w\u041c\u04218y\u041cTc4\u041cTk2\u041cy8y\u041cTc4\u041cTk2\u041c19ocS5tcDQvP2Q9\u041cTI5N\u0421Zicj0yODYmdGk9\u041cTc4OTYy\u041czU3\u041cQ~~"
        val decoded = TxxxProvider.base164Decode(sample)
        
        assertTrue("Decoded path should start with /get_file/", decoded.startsWith("/get_file/"))
        assertTrue("Decoded path should contain .mp4", decoded.contains(".mp4"))
        assertTrue("Decoded path should contain video id 21781963", decoded.contains("21781963"))
    }

    @Test
    fun testProviderConstants() {
        assertEquals("txxx", TxxxProvider.PROVIDER_ID)
    }
}
