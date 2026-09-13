package com.kusa.bugslife.util

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object SkyWayTokenUtil {
    /**
     * SkyWay AppID と SecretKey から クライアント用 Auth Token (JWT) を生成
     * @skyway-sdk/token の仕様に完全準拠
     */
    fun createAuthToken(
        appId: String,
        secretKey: String,
        durationSeconds: Long = 6 * 60 * 60L // 6時間有効 (SkyWay上限: 24h)
    ): String {
        val now = System.currentTimeMillis() / 1000
        val exp = now + durationSeconds

        val headerObj = JSONObject().apply {
            put("alg", "HS256")
            put("typ", "JWT")
        }

        val writeAction = JSONArray(listOf("write"))

        val memberRule = JSONObject().apply {
            put("id", "*")
            put("name", "*")
            put("actions", writeAction)
            put("publication", JSONObject().apply { put("actions", writeAction) })
            put("subscription", JSONObject().apply { put("actions", writeAction) })
        }

        val channelRule = JSONObject().apply {
            put("id", "*")
            put("name", "*")
            put("actions", writeAction)
            put("members", JSONArray().apply { put(memberRule) })
        }

        val appScope = JSONObject().apply {
            put("id", appId)
            put("actions", JSONArray(listOf("read")))
            put("channels", JSONArray().apply { put(channelRule) })
            put("turn", true)
        }

        val payloadObj = JSONObject().apply {
            put("jti", UUID.randomUUID().toString())
            put("iat", now)
            put("exp", exp)
            put("scope", JSONObject().apply { put("app", appScope) })
        }

        val headerBase64 = base64UrlEncode(headerObj.toString().toByteArray(StandardCharsets.UTF_8))
        val payloadBase64 = base64UrlEncode(payloadObj.toString().toByteArray(StandardCharsets.UTF_8))

        val signatureTarget = "$headerBase64.$payloadBase64"
        val signature = hmacSha256(signatureTarget, secretKey)
        val signatureBase64 = base64UrlEncode(signature)

        return "$headerBase64.$payloadBase64.$signatureBase64"
    }

    private fun hmacSha256(data: String, key: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        mac.init(secretKeySpec)
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }

    private fun base64UrlEncode(bytes: ByteArray): String {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        } else {
            try {
                android.util.Base64.encodeToString(
                    bytes,
                    android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
                ).trim()
            } catch (e: Throwable) {
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            }
        }
    }
}
