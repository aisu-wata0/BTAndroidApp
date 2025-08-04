package com.eva.bluetoothterminalapp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WebSocketMessage(
    val deviceName: String,
    val deviceAddress: String,
    val serviceUUID: String,
    val characteristicUUID: String,
    val raw_value: String,
    val parsed_value: Int? = null
)
