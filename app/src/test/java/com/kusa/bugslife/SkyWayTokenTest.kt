package com.kusa.bugslife

import com.kusa.bugslife.util.SkyWayTokenUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyWayTokenTest {

    @Test
    fun testSkyWayTokenGeneration() {
        val appId = "dafe3d90-0a02-4682-9bba-50eb66e6854e"
        val secretKey = "f3JviJMS+8rTrgJ29fiE1GNv3NLqyLozp6PNm7pA2pk="

        val token = SkyWayTokenUtil.createAuthToken(appId, secretKey)
        assertNotNull(token)
        assertTrue(token.isNotBlank())

        val parts = token.split(".")
        assertEquals(3, parts.size) // header.payload.signature
    }
}
