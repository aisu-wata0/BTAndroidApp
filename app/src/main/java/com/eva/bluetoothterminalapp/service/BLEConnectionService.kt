package com.eva.bluetoothterminalapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.eva.bluetoothterminalapp.MainActivity
import com.eva.bluetoothterminalapp.R
import com.eva.bluetoothterminalapp.data.bluetooth_le.BLEClientGattCallback
import com.eva.bluetoothterminalapp.data.model.WebSocketMessage
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.util.Collections

class BLEConnectionService : Service() {

    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val server by lazy {
        embeddedServer(CIO, port = 8080, module = { webSocketModule() })
    }
    private val connectedClients = Collections.synchronizedSet<DefaultWebSocketSession>(LinkedHashSet())

    private var deviceName: String = "Unknown Device"
    private var deviceAddress: String = "Unknown Address"


    private val gattUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val uuid = intent?.getStringExtra(BLEClientGattCallback.EXTRA_UUID)
            val data = intent?.getByteArrayExtra(BLEClientGattCallback.EXTRA_DATA)

            if (uuid != null && data != null) {
                var parsedValue: Int? = null
                val message = if (uuid == "00002a37-0000-1000-8000-00805f9b34fb") {
                    val heartRate = parseHeartRate(data)
                    parsedValue = heartRate
                    "Heart Rate: $parsedValue"
                } else {
                    String(data)
                }
                updateNotification(message)

                val wsMessage = WebSocketMessage(
                    deviceName = deviceName,
                    deviceAddress = deviceAddress,
                    serviceUUID = "0000180d-0000-1000-8000-00805f9b34fb", // Assuming Heart Rate Service for now
                    characteristicUUID = uuid,
                    raw_value = data.joinToString(separator = "") { "%02x".format(it) },
                    parsed_value = parsedValue
                )

                serviceScope.launch {
                    val jsonMessage = Json.encodeToString(WebSocketMessage.serializer(), wsMessage)
                    connectedClients.forEach { client ->
                        client.send(Frame.Text(jsonMessage))
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        registerReceiver(gattUpdateReceiver, IntentFilter(BLEClientGattCallback.ACTION_GATT_MESSAGE_RECEIVED))
        server.start(wait = false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        deviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME) ?: "Unknown Device"
        deviceAddress = intent?.getStringExtra(EXTRA_DEVICE_ADDRESS) ?: "Unknown Address"


        createNotificationChannel()

        val notificationIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("$deviceName Connected (BLE)")
            .setContentText("Device: $deviceName")
            .setSmallIcon(R.drawable.bluetooth_le)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                startForeground(NOTIFICATION_ID, notificationBuilder.build())
            }
        } else {
            startForeground(NOTIFICATION_ID, notificationBuilder.build())
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "BLE Connection Status",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(serviceChannel)
        }
    }

    private fun updateNotification(message: String) {
        notificationBuilder.setContentText(message)
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun parseHeartRate(data: ByteArray): Int {
        val flag = data[0].toInt()
        val format = if ((flag and 0x01) != 0) HEART_RATE_FORMAT_UINT16 else HEART_RATE_FORMAT_UINT8
        return if (format == HEART_RATE_FORMAT_UINT16) {
            (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
        } else {
            data[1].toInt() and 0xFF
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(gattUpdateReceiver)
        server.stop(1_000, 2_000)
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun Application.webSocketModule() {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(Json)
        }
        routing {
            webSocket("/ws") {
                try {
                    connectedClients.add(this)
                    for (frame in incoming) {
                        // Handle incoming messages if needed
                    }
                } finally {
                    connectedClients.remove(this)
                }
            }
        }
    }

    companion object {
        const val EXTRA_DEVICE_NAME = "EXTRA_DEVICE_NAME"
        const val EXTRA_DEVICE_ADDRESS = "EXTRA_DEVICE_ADDRESS"
        const val CHANNEL_ID = "BLEConnectionServiceChannel"
        const val NOTIFICATION_ID = 1

        private const val HEART_RATE_FORMAT_UINT8 = 0
        private const val HEART_RATE_FORMAT_UINT16 = 1


        fun newIntent(context: Context, deviceName: String, deviceAddress: String): Intent {
            return Intent(context, BLEConnectionService::class.java).apply {
                putExtra(EXTRA_DEVICE_NAME, deviceName)
                putExtra(EXTRA_DEVICE_ADDRESS, deviceAddress)
            }
        }
    }
}
