package net.twinte.android.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest.Companion.MIN_BACKOFF_MILLIS
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import net.twinte.android.MainActivity
import net.twinte.android.R
import net.twinte.android.TWINTE_DEBUG
import net.twinte.android.repository.schedule.ScheduleRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * 一日一回、APIサーバーからウィジットに必要なデータを取得するJobの管理を行う
 */
@HiltWorker
class UpdateScheduleWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    @Inject
    lateinit var scheduleRepository: ScheduleRepository

    companion object {
        private const val TAG = "UPDATE_SCHEDULE"
        fun scheduleNextUpdate(context: Context) {
            val currentDate = Calendar.getInstance()

            // Set Execution around 18:00 ~ 18:30
            val dueDate = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 18)
                set(Calendar.MINUTE, (Math.random() * 30).toInt())
                set(Calendar.SECOND, (Math.random() * 59).toInt())
            }

            if (dueDate.before(currentDate)) {
                dueDate.add(Calendar.HOUR_OF_DAY, 24)
            }
            val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis

            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val updateScheduleWorkRequest = OneTimeWorkRequestBuilder<UpdateScheduleWorker>()
                .setConstraints(constraints)
                .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS,
                )
                .addTag(TAG).build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, updateScheduleWorkRequest)
            Log.d(
                "UpdateScheduleWorker",
                "work enqueued at ${SimpleDateFormat.getDateTimeInstance().format(dueDate.time)}",
            )
        }
    }

    override suspend fun doWork() = try {
        scheduleRepository.update()
        scheduleNextUpdate(applicationContext)
        Log.d("UpdateScheduleWorker", "work success")
        if (TWINTE_DEBUG) {
            debugNotification(applicationContext, "success")
        }
        Result.success()
    } catch (e: Throwable) {
        Log.d("UpdateScheduleWorker", "work failure $e")
        if (TWINTE_DEBUG) {
            debugNotification(applicationContext, "$e")
        }
        Result.retry()
    }

    private fun debugNotification(context: Context, msg: String) {
        val notificationManager = NotificationManagerCompat.from(context)

        val notification = NotificationCompat.Builder(context, context.getString(R.string.schedule_notify_channel_id))
            .setSmallIcon(R.drawable.ic_icon)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    1,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            ).setContentTitle("[Debug]APIアクセス終了")
            .setContentText(msg)
            .setChannelId(context.getString(R.string.schedule_notify_channel_id))
            .build()
        notificationManager.notify(2, notification)
    }
}
