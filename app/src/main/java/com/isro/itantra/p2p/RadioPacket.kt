package com.isro.itantra.p2p

import com.google.gson.Gson
import java.io.Serializable

data class RadioPacket(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val language: String = "hi",
    val isSos: Boolean = false,
    val senderId: String = "NODE-ALPHA",
    val tokensHex: String = "5401008A3F1B4C829D31FA0788932CE14F92",
    val timestamp: Long = System.currentTimeMillis(),
    val isSentByMe: Boolean = true
) : Serializable {

    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun fromJson(json: String): RadioPacket? {
            return try {
                Gson().fromJson(json, RadioPacket::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}
