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

class BLEConnectionService : Service() {

    private lateinit var notificationManager: NotificationManager
    private lateinit var notificationBuilder: NotificationCompat.Builder

    private val gattUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val data = intent?.getByteArrayExtra(BLEClientGattCallback.EXTRA_DATA)
            if (data != null) {
                updateNotification(String(data))
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val deviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME) ?: "Unknown Device"

        createNotificationChannel()

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Connected to BLE Device")
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

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(gattUpdateReceiver)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    companion object {
        const val EXTRA_DEVICE_NAME = "EXTRA_DEVICE_NAME"
        const val CHANNEL_ID = "BLEConnectionServiceChannel"
        const val NOTIFICATION_ID = 1

        fun newIntent(context: Context, deviceName: String): Intent {
            return Intent(context, BLEConnectionService::class.java).apply {
                putExtra(EXTRA_DEVICE_NAME, deviceName)
            }
        }
    }
}
