/*
 * Copyright (C) 2025 Răzvan Albu
 * Copyright (C) 2026 Adriano Pontes
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 */

package adrianogba.stario.launcher.services

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationService : NotificationListenerService() {

    override fun onNotificationPosted(notification: StatusBarNotification) {
        try {
            sendBroadcastForNotification(notification)
        } catch (exception: Exception) {
            Log.e(TAG, "onNotificationPosted: $exception")
        }

        super.onNotificationPosted(notification)
    }

    override fun onNotificationRemoved(notification: StatusBarNotification) {
        try {
            sendBroadcastForNotification(notification)
        } catch (exception: Exception) {
            Log.e(TAG, "onNotificationRemoved: $exception")
        }

        super.onNotificationRemoved(notification)
    }

    override fun onListenerConnected() {
        instance = this

        try {
            val intent = Intent()
            intent.setAction(NOTIFICATIONS_EVENT)

            intent.putExtra(TARGET_NOTIFICATION, convertToNotificationMap(activeNotifications))
            sendBroadcast(intent)
        } catch (exception: Exception) {
            Log.e(TAG, "onListenerConnected: " + exception.message)
        }

        super.onListenerConnected()
    }

    @SuppressLint("UnsafeImplicitIntentLaunch")
    private fun sendBroadcastForNotification(notification: StatusBarNotification) {
        val intent = Intent()
        intent.setAction(UPDATE_NOTIFICATIONS)

        var count = 0

        for (statusBarNotification in activeNotifications) {
            if (statusBarNotification.packageName == notification.packageName &&
                (statusBarNotification.notification.flags and Notification.FLAG_GROUP_SUMMARY) !=
                Notification.FLAG_GROUP_SUMMARY
            ) {
                count++
            }
        }

        intent.putExtra(TARGET_NOTIFICATION, notification.packageName)
        intent.putExtra(NOTIFICATION_COUNT, count)

        sendBroadcast(intent)
    }

    companion object {
        private const val TAG = "NotificationService"

        const val NOTIFICATION_DOTS: String = "com.stario.NOTIFICATION_DOTS"
        const val NOTIFICATIONS_EVENT: String =
            "adrianogba.stario.launcher.NOTIFICATIONS_LISTENER_EVENT"
        const val UPDATE_NOTIFICATIONS: String =
            "adrianogba.stario.launcher.UPDATE_NOTIFICATIONS"
        const val TARGET_NOTIFICATION: String =
            "adrianogba.stario.launcher.TARGET_NOTIFICATION"
        const val NOTIFICATION_COUNT: String =
            "adrianogba.stario.launcher.NOTIFICATION_COUNT"

        private var instance: NotificationService? = null

        @JvmStatic
        fun getInstance(): NotificationService? = instance

        @JvmStatic
        fun convertToNotificationMap(
            notifications: Array<StatusBarNotification>?
        ): HashMap<String, Int> {
            val notificationMap = HashMap<String, Int>()

            if (notifications != null) {
                for (notification in notifications) {
                    val packageName = notification.packageName

                    if ((notification.notification.flags and Notification.FLAG_GROUP_SUMMARY) !=
                        Notification.FLAG_GROUP_SUMMARY
                    ) {
                        notificationMap[packageName] =
                            (notificationMap[packageName] ?: 0) + 1
                    }
                }
            }

            return notificationMap
        }
    }
}
