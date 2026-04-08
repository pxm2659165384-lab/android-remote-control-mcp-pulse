@file:Suppress("TooManyFunctions")

package com.danielealbano.androidremotecontrolmcp.services.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.danielealbano.androidremotecontrolmcp.data.model.NotificationChangeEvent
import com.danielealbano.androidremotecontrolmcp.data.model.NotificationChangeType
import com.danielealbano.androidremotecontrolmcp.utils.Logger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class McpNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Logger.i(TAG, "Notification listener connected")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        Logger.i(TAG, "Notification listener disconnected")
    }

    override fun onDestroy() {
        Logger.i(TAG, "Notification listener service destroying")
        instance = null
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Logger.w(TAG, "Low memory condition reported")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val data = NotificationDataExtractor.extract(sbn, applicationContext)
        if (isEmptyNotification(data)) return
        _notificationChangeEvents.tryEmit(
            NotificationChangeEvent(NotificationChangeType.POSTED, data),
        )
    }

    private fun isEmptyNotification(data: NotificationData): Boolean =
        data.title == null && data.text == null && data.bigText == null && data.actions.isEmpty()

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val data = NotificationDataExtractor.extract(sbn, applicationContext)
        _notificationChangeEvents.tryEmit(
            NotificationChangeEvent(NotificationChangeType.REMOVED, data),
        )
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Logger.d(TAG, "onTrimMemory level=$level")
    }

    fun getNotifications(): Array<StatusBarNotification> = activeNotifications ?: emptyArray()

    fun dismissNotification(key: String) {
        cancelNotification(key)
    }

    fun snoozeNotificationByKey(
        key: String,
        durationMs: Long,
    ) {
        snoozeNotification(key, durationMs)
    }

    companion object {
        private const val TAG = "MCP:NotificationListener"

        @Volatile
        var instance: McpNotificationListenerService? = null
            private set

        private val _notificationChangeEvents =
            MutableSharedFlow<NotificationChangeEvent>(
                extraBufferCapacity = 64,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val notificationChangeEvents: SharedFlow<NotificationChangeEvent> =
            _notificationChangeEvents.asSharedFlow()
    }
}
