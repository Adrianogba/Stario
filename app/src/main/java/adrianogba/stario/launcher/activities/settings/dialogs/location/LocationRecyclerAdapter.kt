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

package adrianogba.stario.launcher.activities.settings.dialogs.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Address
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.activities.launcher.widgets.glance.extensions.weather.GeocoderFallback
import adrianogba.stario.launcher.activities.launcher.widgets.glance.extensions.weather.Weather
import adrianogba.stario.launcher.preferences.Entry
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.utils.UiUtils
import adrianogba.stario.launcher.utils.Utils

class LocationRecyclerAdapter(
    private val activity: ThemedActivity,
    private val clickListener: View.OnClickListener?
) : RecyclerView.Adapter<LocationRecyclerAdapter.ViewHolder>() {
    private val geocoder = GeocoderFallback(activity)
    private val preferences: SharedPreferences =
        activity.applicationContext.getSharedPreferences(Entry.WEATHER)

    private val defaultAddresses: List<Address> = loadDefaultAddresses()
    private val addresses: MutableList<Address> = ArrayList(defaultAddresses)
    private val broadcastManager: LocalBroadcastManager =
        LocalBroadcastManager.getInstance(activity)

    private var query: String? = null

    // Deliberately empty. This used to seed four cities the original author
    // picked, which read like stray data rather than suggestions. With none,
    // the list shows just the exact and approximate options until you type.
    private fun loadDefaultAddresses(): ArrayList<Address> = ArrayList()

    @SuppressLint("NotifyDataSetChanged")
    fun update(query: String?) {
        this.query = query

        if (query == null || query.isBlank()) {
            addresses.clear()
            addresses.addAll(defaultAddresses)

            notifyDataSetChanged()
        } else {
            Utils.submitTask(Runnable {
                val addressList = geocoder.getFromLocationName(query, MAX_LOCALITIES)

                if (query == this@LocationRecyclerAdapter.query) {
                    UiUtils.post {
                        addresses.clear()

                        if (addressList != null) {
                            for (address in addressList) {
                                if (address.hasLatitude() && address.hasLongitude()) {
                                    addresses.add(address)
                                }
                            }
                        }

                        notifyDataSetChanged()
                    }
                }
            })
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // public because the enclosing adapter reads them; Kotlin has no package-private
        val locality: TextView = itemView.findViewById(R.id.locality)
        val location: TextView = itemView.findViewById(R.id.location)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        if (position == 0) {
            viewHolder.locality.setText(R.string.precise_location)
            viewHolder.location.visibility = View.GONE

            viewHolder.itemView.setOnClickListener { v ->
                activity.requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
                ) { granted ->
                    if (granted[0] == PackageManager.PERMISSION_GRANTED) {
                        preferences.edit()
                            .remove(Weather.LATITUDE_KEY)
                            .remove(Weather.LONGITUDE_KEY)
                            .remove(Weather.LOCATION_NAME)
                            .putBoolean(Weather.PRECISE_LOCATION, true)
                            .apply()

                        broadcastManager.sendBroadcastSync(Intent(Weather.ACTION_REQUEST_UPDATE))

                        clickListener?.onClick(v)
                    } else {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            val uri = Uri.fromParts("package", activity.packageName, null)
                            intent.data = uri

                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

                            activity.startActivity(intent)
                        } catch (exception: ActivityNotFoundException) {
                            Log.e(TAG, "onBindViewHolder: Settings activity not found.")
                        }

                        Toast.makeText(activity, R.string.location_reasoning, Toast.LENGTH_LONG)
                            .show()
                    }
                }
            }
        } else if (position == 1) {
            viewHolder.locality.setText(R.string.location_ip_based)
            viewHolder.location.visibility = View.GONE

            viewHolder.itemView.setOnClickListener { v ->
                preferences.edit()
                    .remove(Weather.LATITUDE_KEY)
                    .remove(Weather.LONGITUDE_KEY)
                    .remove(Weather.LOCATION_NAME)
                    .putBoolean(Weather.PRECISE_LOCATION, false)
                    .apply()

                broadcastManager.sendBroadcastSync(Intent(Weather.ACTION_REQUEST_UPDATE))

                clickListener?.onClick(v)
            }
        } else {
            val address = addresses[position - 2]

            var locality = address.subLocality

            if (locality == null || locality.isBlank()) {
                locality = address.locality

                if (locality == null || locality.isBlank()) {
                    locality = address.featureName
                }

                if (locality == null || locality.isBlank()) {
                    locality = address.adminArea
                }

                viewHolder.locality.text = locality
                viewHolder.location.text = address.countryName
            } else {
                viewHolder.locality.text = locality

                val mainLocality = address.locality
                if (mainLocality != null) {
                    viewHolder.location.text = mainLocality + ", " + address.countryName
                } else {
                    viewHolder.location.text = address.countryName
                }
            }

            viewHolder.location.visibility = View.VISIBLE

            viewHolder.itemView.setOnClickListener { v ->
                val editor = preferences.edit()
                    .putLong(
                        Weather.LATITUDE_KEY,
                        java.lang.Double.doubleToLongBits(address.latitude)
                    )
                    .putLong(
                        Weather.LONGITUDE_KEY,
                        java.lang.Double.doubleToLongBits(address.longitude)
                    )

                var city = address.subLocality
                if (city != null && !city.isBlank()) {
                    editor.putString(Weather.LOCATION_NAME, city)
                } else {
                    city = address.locality

                    if (city != null && !city.isBlank()) {
                        editor.putString(Weather.LOCATION_NAME, city)
                    } else {
                        editor.putString(Weather.LOCATION_NAME, null)
                    }
                }

                editor.putBoolean(Weather.PRECISE_LOCATION, false)
                editor.apply()

                broadcastManager.sendBroadcastSync(Intent(Weather.ACTION_REQUEST_UPDATE))

                clickListener?.onClick(v)
            }
        }
    }

    override fun getItemCount(): Int = addresses.size + 2

    override fun onCreateViewHolder(container: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(
            LayoutInflater.from(activity)
                .inflate(R.layout.location_item, container, false)
        )

    private companion object {
        private const val TAG = "LocationRecyclerAdapter"
        private const val MAX_LOCALITIES = 4
    }
}
