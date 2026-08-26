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

package adrianogba.stario.launcher.ui.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

class HomeWatcher(private val context: Context) {
    private val filter = IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)

    private var receiver: InnerReceiver? = null
    private var listener: OnHomePressedListener? = null

    fun setOnHomePressedListener(listener: OnHomePressedListener?) {
        this.listener = listener
        receiver = InnerReceiver()
    }

    fun startWatch() {
        val receiver = this.receiver ?: return

        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    fun stopWatch() {
        receiver?.let { context.unregisterReceiver(it) }
    }

    inner class InnerReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_CLOSE_SYSTEM_DIALOGS) {
                return
            }

            val reason = intent.getStringExtra("reason") ?: return

            if (reason == SYSTEM_DIALOG_REASON_HOME_KEY ||
                reason == SYSTEM_DIALOG_REASON_RECENT_APPS
            ) {
                listener?.onHomePressed()
            }
        }
    }

    fun interface OnHomePressedListener {
        fun onHomePressed()
    }

    private companion object {
        private const val SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps"
        private const val SYSTEM_DIALOG_REASON_HOME_KEY = "homekey"
    }
}
