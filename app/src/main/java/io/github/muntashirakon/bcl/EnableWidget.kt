package io.github.muntashirakon.bcl

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import android.widget.Toast
import com.topjohnwu.superuser.Shell
import io.github.muntashirakon.bcl.Constants.CHARGE_LIMIT_ENABLED
import io.github.muntashirakon.bcl.Constants.INTENT_TOGGLE_ACTION

class EnableWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_button)
        val settings = Utils.getSettings(context)
        val isEnabled = settings.getBoolean(CHARGE_LIMIT_ENABLED, false)
        remoteViews.setImageViewResource(R.id.enable, getImage(isEnabled))
        remoteViews.setOnClickPendingIntent(R.id.enable, buildButtonPendingIntent(context))

        pushWidgetUpdate(context, remoteViews)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == INTENT_TOGGLE_ACTION) {
            val settings = Utils.getSettings(context)
            if (Shell.getShell().isRoot) {
                val enable = !settings.getBoolean(CHARGE_LIMIT_ENABLED, false)
                settings.edit().putBoolean(CHARGE_LIMIT_ENABLED, enable).apply()
                if (enable) {
                    Utils.startServiceIfLimitEnabled(context)
                } else {
                    Utils.stopService(context)
                }
                updateWidget(context, enable)
            } else {
                Toast.makeText(context, R.string.root_denied, Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {

        fun updateWidget(context: Context, enable: Boolean) {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_button)

            remoteViews.setImageViewResource(R.id.enable, getImage(enable))
            remoteViews.setOnClickPendingIntent(R.id.enable, buildButtonPendingIntent(context))

            pushWidgetUpdate(context, remoteViews)
        }

        fun getImage(enabled: Boolean): Int {
            return if (enabled) {
                R.drawable.widget_enabled
            } else {
                R.drawable.widget_disabled
            }
        }

        fun buildButtonPendingIntent(context: Context): PendingIntent {
            val flagImmutable: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            return PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, EnableWidget::class.java).setAction(INTENT_TOGGLE_ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT or flagImmutable
            )
        }

        fun pushWidgetUpdate(context: Context, remoteViews: RemoteViews) {
            val myWidget = ComponentName(context, EnableWidget::class.java)
            val manager = AppWidgetManager.getInstance(context)
            manager.updateAppWidget(myWidget, remoteViews)
        }
    }
}
