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
     */
    fun createAuthToken(
        appId: String,
        secretKey: String,
        durationSeconds: Long = 30 * 24 * 60 * 60L // 30日間有効
    ): String {
        val now = System.currentTimeMillis() / 1000
        val exp = now + durationSeconds

        val headerObj = JSONObject().apply {
            put("alg", "HS256")
            put("typ", "JWT")
        }

        val allActions = JSONArray(listOf("write", "create", "delete"))

        val memberRule = JSONObject().apply {
            put("id", "*")
            put("name", "*")
            put("actions", allActions)
            put("publication", JSONObject().apply { put("actions", allActions) })
            put("subscription", JSONObject().apply { put("actions", allActions) })
        }

        val channelRule = JSONObject().apply {
            put("id", "*")
            put("name", "*")
            put("actions", allActions)
            put("members", JSONArray().apply { put(memberRule) })
            put("sfuBots", JSONArray().apply {
                put(JSONObject().apply {
                    put("actions", allActions)
                    put("forwardings", JSONObject().apply { put("actions", allActions) })
                })
            })
        }

        val appScope = JSONObject().apply {
            put("id", appId)
            put("turn", true)
            put("actions", JSONArray(listOf("read")))
            put("channels", JSONArray().apply { put(channelRule) })
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
        val keyBytes = try {
            base64Decode(key)
        } catch (e: Exception) {
            key.toByteArray(StandardCharsets.UTF_8)
        }
        val secretKeySpec = SecretKeySpec(keyBytes, "HmacSHA256")
        mac.init(secretKeySpec)
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }

    private fun base64UrlEncode(bytes: ByteArray): String {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        } else {
            // Android 6.0 / 7.0 互換フォールバック
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

    private fun base64Decode(str: String): ByteArray {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            java.util.Base64.getDecoder().decode(str.trim())
        } else {
            try {
                android.util.Base64.decode(str.trim(), android.util.Base64.DEFAULT)
            } catch (e: Throwable) {
                java.util.Base64.getDecoder().decode(str.trim())
            }
        }
    }
}
