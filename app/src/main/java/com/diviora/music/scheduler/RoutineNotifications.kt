package com.diviora.music.scheduler

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.diviora.music.MainActivity
import com.diviora.music.R

object RoutineNotifications {
    const val CHANNEL = "routines"
    const val ID_STARTING = 4101
    private const val ID_TAP_BASE = 5000

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.channel_routines), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = context.getString(R.string.channel_routines_desc) },
            )
        }
    }

    fun starting(context: Context, text: String): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_diviora)
            .setContentTitle("DIVIORA routine")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
    }

    fun showTapToStart(context: Context, slotId: Long, occurrence: Long) {
        post(context, ID_TAP_BASE + (slotId % 1000).toInt(), "Routine is due", "Tap to start playback", runIntent(context, slotId, occurrence))
    }

    fun showVideoReady(context: Context, title: String) {
        val open = PendingIntent.getActivity(context, 7, Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN, "player"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        post(context, 4102, "Scheduled video started", "Tap to watch: $title", open)
    }

    fun showFailure(context: Context, text: String) {
        val open = PendingIntent.getActivity(context, 8, Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN, "history"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        post(context, 4103, "Routine could not play", text, open)
    }

    private fun runIntent(context: Context, slotId: Long, occurrence: Long) = PendingIntent.getActivity(
        context, slotId.toInt(),
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_RUN_SLOT, slotId)
            .putExtra(AlarmScheduler.EXTRA_OCCURRENCE, occurrence),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun post(context: Context, id: Int, title: String, text: String, pi: PendingIntent) {
        ensureChannel(context)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33) return
        NotificationManagerCompat.from(context).notify(
            id,
            NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_diviora)
                .setContentTitle(title).setContentText(text).setContentIntent(pi).setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_ALARM).build(),
        )
    }
}
