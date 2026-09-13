package com.kusa.bugslife

import com.kusa.bugslife.util.SkyWayTokenUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyWayTokenTest {

    @Test
    fun testSkyWayTokenGeneration() {
        val appId = "dummy-app-id-0000-0000-000000000000"
        val secretKey = "dummy_secret_key_base64_for_testing_only="

        val token = SkyWayTokenUtil.createAuthToken(appId, secretKey)
        assertNotNull(token)
        assertTrue(token.isNotBlank())

        val parts = token.split(".")
        assertEquals(3, parts.size) // header.payload.signature
    }
}
