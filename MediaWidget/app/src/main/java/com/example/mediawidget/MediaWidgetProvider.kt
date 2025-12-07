package com.example.mediawidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

class MediaWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_media_control)
            // The service will update the widget with real data when it's running.
            // Here we just set the initial state.
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
