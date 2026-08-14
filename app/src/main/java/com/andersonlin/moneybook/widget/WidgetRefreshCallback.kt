package com.andersonlin.moneybook.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

/** 小组件「刷新」按钮回调：立即重新渲染该小组件 */
class WidgetRefreshCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        runCatching { MoneyBookWidget().update(context, glanceId) }
    }
}
