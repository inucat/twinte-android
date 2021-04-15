package net.twinte.android.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Debug
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import kotlinx.coroutines.runBlocking
import net.twinte.android.R
import net.twinte.android.model.Timetable
import net.twinte.android.repository.ScheduleRepository

/**
 * Largeウィジットの管理を担う
 */
class V3LargeWidgetProvider : AppWidgetProvider() {

    /**
     * 設置されたLargeウィジットの数が 0 -> 1 になると呼び出される
     */
    override fun onEnabled(context: Context) {
        WidgetUpdater.schedule(context, this::class.java)
    }

    /**
     * Largeウィジットが全て無くなると呼び出される
     */
    override fun onDisabled(context: Context) {
        WidgetUpdater.cancel(context, this::class.java)
    }

    /**
     * ウィジットの更新依頼が来たら呼び出される
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) = runBlocking {
        Log.d("V3LargeWidgetProvider", "OnUpdate received")
        val (current, period) = WidgetUpdater.getShouldShowCurrentDate()
        val schedule = ScheduleRepository(context).getSchedule(current.time)

        appWidgetIds.forEach { appWidgetId ->
            val views: RemoteViews = RemoteViews(
                context.packageName,
                R.layout.widget_v3_large
            )

            views.setTextViewText(R.id.date_textView, schedule.dateLabel(current))
            views.setTextViewText(R.id.event_textView, schedule.eventLabel())
            views.setTextViewText(R.id.course_count_textView, schedule.courseCountLabel())

            views.setRemoteAdapter(
                R.id.course_listView,
                Intent(context, V3LargeWidgetRemoteViewService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                })

            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.course_listView)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

/**
 * ウィジット右側のリストを生成するサービス
 */
class V3LargeWidgetRemoteViewService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent?) = Factory(applicationContext, intent)

    class Factory(val context: Context, val intent: Intent?) : RemoteViewsService.RemoteViewsFactory {
        var schedule: Timetable? = null

        override fun onCreate() {}

        override fun onDataSetChanged() = runBlocking {
            val (current, _) = WidgetUpdater.getShouldShowCurrentDate()
            schedule = ScheduleRepository(context).getSchedule(current.time)
        }

        override fun onDestroy() {}

        override fun getCount() = 6

        override fun getViewAt(position: Int): RemoteViews {
            val views: RemoteViews = RemoteViews(
                context.packageName,
                R.layout.widget_v3_period_item
            )

            views.setTextViewText(R.id.period_number_textView, "${position + 1}")
            views.applyCourseItem(context, schedule?.courseViewModel(position + 1))

            return views
        }

        override fun getLoadingView() = null

        override fun getViewTypeCount() = 1

        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false

    }
}
