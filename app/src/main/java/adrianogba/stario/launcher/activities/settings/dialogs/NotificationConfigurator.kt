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

package adrianogba.stario.launcher.activities.settings.dialogs

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.bottomsheet.BottomSheetBehavior
import adrianogba.stario.launcher.BuildConfig
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.services.NotificationService
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.dialogs.ActionDialog

class NotificationConfigurator(activity: ThemedActivity) : ActionDialog(activity) {

    override fun inflateContent(inflater: LayoutInflater): View {
        val root = inflater.inflate(R.layout.pop_up_notifications, null)

        root.findViewById<View>(R.id.proceed)
            .setOnClickListener {
                setOnDismissListener(null)
                showSettingsActivity()
            }
        root.findViewById<View>(R.id.cancel)
            .setOnClickListener { dismiss() }

        return root
    }

    private fun showSettingsActivity() {
        val showArgs = BuildConfig.APPLICATION_ID + "/" + NotificationService::class.java.name

        val bundle = Bundle()
        bundle.putString(":settings:fragment_args_key", showArgs)

        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

        intent.putExtra(":settings:fragment_args_key", showArgs)
        intent.putExtra(":settings:show_fragment_args", bundle)
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        activity.startActivity(intent)
    }

    override fun blurBehind(): Boolean = true

    override fun getDesiredInitialState(): Int = BottomSheetBehavior.STATE_EXPANDED
}
