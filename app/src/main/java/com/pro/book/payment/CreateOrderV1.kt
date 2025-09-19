package com.pro.book.payment

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ZpV1Demo {
    const val APP_ID = 553
    const val KEY1 = "9phuAOYhan4urywHTh0ndEXiV3pKHr5Q"
    const val CREATE_URL = "https://sandbox.zalopay.com.vn/v001/tpe/createorder"
}

class CreateOrderV1 {
    private fun hmacSha256(key: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(), "HmacSHA256"))
        return mac.doFinal(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun create(amountVnd: Int): JSONObject {
        val appId = ZpV1Demo.APP_ID
        val appUser = "user123"
        val appTime = System.currentTimeMillis()
        val datePart = DateTimeFormatter.ofPattern("yyMMdd")
            .format(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")))
        val appTransId = "${datePart}_${(100000..999999).random()}"
        val embedData = "{}"
        val item = "[]"

        val dataToSign = "$appId|$appTransId|$appUser|$amountVnd|$appTime|$embedData|$item"
        val mac = hmacSha256(ZpV1Demo.KEY1, dataToSign)

        val form = listOf(
            "appid" to appId.toString(),
            "appuser" to appUser,
            "apptime" to appTime.toString(),
            "amount" to amountVnd.toString(),
            "apptransid" to appTransId,
            "embeddata" to embedData,
            "item" to item,
            "mac" to mac
        ).joinToString("&") { it.first + "=" + URLEncoder.encode(it.second, "UTF-8") }

        val conn = URL(ZpV1Demo.CREATE_URL).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.doOutput = true
        conn.outputStream.use { it.write(form.toByteArray()) }

        val body = conn.inputStream.bufferedReader().use { it.readText() }
        return JSONObject(body)
    }
}
