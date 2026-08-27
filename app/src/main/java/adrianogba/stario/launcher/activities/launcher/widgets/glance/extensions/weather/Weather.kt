/*
 * Copyright (C) 2026 Răzvan Albu
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

package adrianogba.stario.launcher.activities.launcher.widgets.glance.extensions.weather

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Address
import android.location.LocationManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebSettings
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.FloatRange
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator
import com.luckycatlabs.sunrisesunset.dto.Location
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.activities.launcher.widgets.glance.GlanceDialogExtension
import adrianogba.stario.launcher.activities.launcher.widgets.glance.GlanceViewExtension
import adrianogba.stario.launcher.preferences.Entry
import adrianogba.stario.launcher.ui.common.glance.GlanceConstraintLayout
import adrianogba.stario.launcher.ui.utils.UiUtils
import adrianogba.stario.launcher.utils.Utils
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Future
import kotlin.math.abs
import kotlin.math.roundToInt

class Weather : GlanceDialogExtension() {
    private val preview = WeatherPreview()

    private var weatherPreferences: SharedPreferences? = null
    private var geocoder: GeocoderFallback? = null

    @Volatile
    private var address: Address? = null

    @Volatile
    private var lastUpdate: Long = 0

    private var weatherData: MutableList<Data> = CopyOnWriteArrayList()
    private var runningTask: Future<*>? = null

    private var recycler: RecyclerView? = null
    private var temperature: TextView? = null
    private var location: TextView? = null
    private var summary: TextView? = null
    private var direction: View? = null
    private var speed: TextView? = null
    private var container: View? = null
    private var icon: ImageView? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            lastUpdate = 0
            address = null
            weatherData.clear()
            preview.update(null)

            synchronized(this@Weather) {
                val task = runningTask
                if (task != null && !task.isDone) {
                    task.cancel(true)
                }
            }

            update()
        }
    }

    override fun getTAG(): String = TAG

    override fun getViewExtensionPreview(): GlanceViewExtension = preview

    override fun inflateExpanded(
        inflater: LayoutInflater,
        container: ConstraintLayout
    ): GlanceConstraintLayout {
        val activity = activity!!

        val root = inflater.inflate(R.layout.weather, container, false)
                as GlanceConstraintLayout

        weatherPreferences = activity.applicationContext.getSharedPreferences(Entry.WEATHER)

        geocoder = GeocoderFallback(activity)

        this.container = root.findViewById(R.id.container)
        temperature = root.findViewById(R.id.temperature)
        location = root.findViewById(R.id.location)
        summary = root.findViewById(R.id.summary)
        direction = root.findViewById(R.id.direction)
        speed = root.findViewById(R.id.speed)
        recycler = root.findViewById(R.id.forecast)
        icon = root.findViewById(R.id.icon)

        recycler!!.layoutManager =
            LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)

        return root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        val activity = activity!!

        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                val index = getFirstIndexInTime()

                if (index > 0) {
                    preview.update(weatherData[index])
                }
            }
        })

        @Suppress("DEPRECATION")
        LocalBroadcastManager.getInstance(activity)
            .registerReceiver(receiver, IntentFilter(ACTION_REQUEST_UPDATE))
    }

    override fun onDetach() {
        super.onDetach()

        try {
            @Suppress("DEPRECATION")
            LocalBroadcastManager.getInstance(activity!!).unregisterReceiver(receiver)
        } catch (exception: Exception) {
            Log.e(TAG, "onDetach: Receiver not registered.")
        }
    }

    override fun isEnabled(): Boolean = true

    override fun updateScaling(
        @FloatRange(from = 0.0, to = 1.0) fraction: Float,
        scale: Float
    ) {
        container?.scaleY = scale
        container?.alpha = fraction
    }

    @Synchronized
    override fun update() {
        val activity = activity ?: return
        val weatherPreferences = this.weatherPreferences ?: return
        val task = runningTask

        if (!weatherPreferences.getBoolean(FORECAST_KEY, true) ||
            (task != null && !task.isDone)
        ) {
            return
        }

        runningTask = Utils.submitTask {
            if (abs(System.currentTimeMillis() - lastUpdate) > DEFAULT_UPDATE_INTERVAL) {
                val prefersPreciseLocation =
                    weatherPreferences.getBoolean(PRECISE_LOCATION, false)
                var fetchedPreciseLocation = false

                if (prefersPreciseLocation) {
                    if (activity.checkSelfPermission(
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        val locationManager = activity
                            .getSystemService(Context.LOCATION_SERVICE) as LocationManager
                        val location = locationManager
                            .getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                        if (location != null) {
                            lat = location.latitude
                            lon = location.longitude

                            fetchedPreciseLocation = true
                        }
                    }
                }

                if (!fetchedPreciseLocation) {
                    if (weatherPreferences.contains(LATITUDE_KEY) &&
                        weatherPreferences.contains(LONGITUDE_KEY)
                    ) {
                        lat = java.lang.Double.longBitsToDouble(
                            weatherPreferences.getLong(LATITUDE_KEY, Long.MAX_VALUE)
                        )
                        lon = java.lang.Double.longBitsToDouble(
                            weatherPreferences.getLong(LONGITUDE_KEY, Long.MAX_VALUE)
                        )
                    } else {
                        loadApproximatedLocation(Utils.getPublicIPAddress())
                    }
                }

                if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                    return@submitTask
                }

                val payload = getWeatherInfo() ?: return@submitTask

                try {
                    val properties = payload.getJSONObject("properties")
                    val timeSeries = properties.getJSONArray("timeseries")

                    val entries = ArrayList<Data>()

                    var index = 0
                    while (index < timeSeries.length() && entries.size <= FORECAST_MAX_ENTRIES) {
                        try {
                            val entry = timeSeries.getJSONObject(index)

                            val time = entry.getString("time")

                            val data = entry.getJSONObject("data")
                            val instant = data.getJSONObject("instant")
                            val details = instant.getJSONObject("details")

                            val temperature = details.getDouble("air_temperature")
                            val windDirection = details.getDouble("wind_from_direction")
                            val windSpeed = details.getDouble("wind_speed")

                            val nextHour = data.getJSONObject("next_6_hours")
                            val summary = nextHour.getJSONObject("summary")

                            val iconCode = summary.getString("symbol_code")

                            entries.add(
                                Data(
                                    Utils.parseDate(time), iconCode,
                                    temperature, windDirection, windSpeed
                                )
                            )
                        } catch (exception: JSONException) {
                            Log.e(TAG, "update: Parse exception for item $index.")
                        }

                        index++
                    }

                    if (entries.isNotEmpty()) {
                        this.weatherData = entries
                    }

                    val firstInTime = getFirstIndexInTime()

                    if (firstInTime > 0) {
                        UiUtils.post { preview.update(this.weatherData[firstInTime]) }
                    }

                    val addressName = weatherPreferences.getString(LOCATION_NAME, null)
                    address = if (addressName == null) {
                        geocoder?.getFromLocation(lat, lon)
                    } else {
                        Address(Locale.ENGLISH).apply { locality = addressName }
                    }

                    // Artificially change the update interval if we want precise location data
                    // But location is not accessible
                    lastUpdate = if (!fetchedPreciseLocation && prefersPreciseLocation) {
                        System.currentTimeMillis() - DEFAULT_UPDATE_INTERVAL +
                                FALLBACK_UPDATE_INTERVAL
                    } else {
                        System.currentTimeMillis()
                    }
                } catch (exception: JSONException) {
                    Log.e(TAG, "updateWeather: ", exception)
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateData() {
        val activity = activity ?: return
        val weatherPreferences = this.weatherPreferences ?: return

        val index = getFirstIndexInTime()

        if (index > 0) {
            val data = weatherData[index]
            val imperial = weatherPreferences.getBoolean(
                IMPERIAL_KEY, Utils.isSystemUsingImperial(activity)
            )

            icon?.setImageResource(getIcon(data.iconCode))

            temperature?.text = if (imperial) {
                Utils.toFahrenheit(data.temperature).roundToInt().toString() + DEGREE
            } else {
                data.temperature.roundToInt().toString() + DEGREE
            }

            summary?.setText(getSummary(data.iconCode))

            val address = this.address
            if (address != null) {
                val subLocality = address.subLocality
                var locality = address.locality

                if (locality == null) {
                    locality = address.subAdminArea
                }

                if (locality == null) {
                    locality = address.adminArea
                }

                locality = if (locality == null) {
                    subLocality
                } else if (subLocality != null) {
                    "$subLocality, $locality"
                } else {
                    locality
                }

                location?.text = locality
            } else {
                location?.text = null
            }

            direction?.rotation = data.windDirection.toFloat() + 180f
            speed?.text = if (imperial) {
                Utils.msToMph(data.windSpeed).roundToInt().toString() + MPH
            } else {
                data.windSpeed.roundToInt().toString() + MS
            }

            recycler?.adapter = ForecastAdapter(activity, weatherData, index)
        }
    }

    override fun show() {
        updateData()

        super.show()
    }

    private fun getFirstIndexInTime(): Int {
        val calendar = Calendar.getInstance()

        for (index in weatherData.indices) {
            if (weatherData[index].date.after(calendar.time)) {
                return index
            }
        }

        return -1
    }

    private fun loadApproximatedLocation(ip: String?) {
        for (entry in LOCATION_APIS) {
            var connection: HttpURLConnection? = null

            try {
                // Left inside the try so a missing public IP is logged and
                // skipped, exactly as it was when this threw from replace.
                val url = URL(entry.api.replace(LOCATION_API_IP_WILDCARD, ip!!))
                connection = url.openConnection() as HttpURLConnection

                connection.readTimeout = REQUEST_TIMEOUT
                connection.connectTimeout = REQUEST_TIMEOUT
                connection.requestMethod = "GET"
                connection.addRequestProperty(
                    "User-Agent", WebSettings.getDefaultUserAgent(activity)
                )
                connection.addRequestProperty("Content-type", "application/json")

                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val jsonObject = JSONObject(Utils.readStream(connection.inputStream))

                    lat = jsonObject.getDouble(entry.latitudeField)
                    lon = jsonObject.getDouble(entry.longitudeField)
                } else {
                    Log.w(
                        TAG,
                        "getWeatherInfo: Server returned non-OK status: $responseCode"
                    )
                }
            } catch (exception: Exception) {
                Log.e(TAG, "updateLocation: " + exception.message)
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun getWeatherInfo(): JSONObject? {
        var connection: HttpURLConnection? = null

        try {
            val url = URL(
                "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=" +
                        lat + "&lon=" + lon
            )
            connection = url.openConnection() as HttpURLConnection

            connection.readTimeout = REQUEST_TIMEOUT
            connection.connectTimeout = REQUEST_TIMEOUT
            connection.requestMethod = "GET"
            connection.addRequestProperty(
                "User-Agent", WebSettings.getDefaultUserAgent(activity)
            )
            connection.setRequestProperty("Content-type", "application/json")

            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return JSONObject(Utils.readStream(connection.inputStream))
            } else {
                Log.w(TAG, "getWeatherInfo: Server returned non-OK status: $responseCode")
            }

            return null
        } catch (exception: Exception) {
            Log.e(TAG, "getWeatherInfo: ", exception)

            return null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * One IP geolocation service. The Java version carried a pair of abstract
     * Callback subclasses per entry purely to assign lat and lon, which is two
     * anonymous classes to say which two JSON fields to read.
     */
    private class IpApiEntry(
        val api: String,
        val latitudeField: String,
        val longitudeField: String
    )

    /**
     * The three resources a weather symbol maps to. The Java version nested a
     * HashMap keyed by three int constants inside another HashMap, so every
     * lookup needed a default that could never be reached: all 41 entries
     * carry all three.
     */
    private class WeatherResource(val day: Int, val night: Int, val summary: Int)

    class Data internal constructor(
        @JvmField val date: Date,
        iconCode: String?,
        @JvmField val temperature: Double, // Celsius
        @JvmField val windDirection: Double, // Degrees
        @JvmField val windSpeed: Double // m/s
    ) {
        @JvmField
        val iconCode: String? = iconCode?.split(Regex("[_%.-]"))?.get(0)
    }

    companion object {
        const val ACTION_REQUEST_UPDATE: String = "com.stario.REQUEST_UPDATE"
        const val PRECISE_LOCATION: String = "com.stario.PRECISE_LOCATION"
        const val FORECAST_KEY: String = "com.stario.WEATHER_FORECAST"
        const val IMPERIAL_KEY: String = "com.stario.IMPERIAL"
        const val LOCATION_NAME: String = "com.stario.LOCATION"
        const val LATITUDE_KEY: String = "com.stario.LATITUDE"
        const val LONGITUDE_KEY: String = "com.stario.LONGITUDE"

        private const val TAG = "adrianogba.stario.launcher.Weather"

        private const val FORECAST_MAX_ENTRIES = 20
        private const val DEFAULT_UPDATE_INTERVAL = 3_600_000
        private const val FALLBACK_UPDATE_INTERVAL = 300_000
        private const val REQUEST_TIMEOUT = 10_000
        private const val LOCATION_API_IP_WILDCARD = "$"

        private const val DEGREE = "°"
        private const val MPH = "mi/h"
        private const val MS = "m/s"

        @Volatile
        private var lat = Double.MAX_VALUE

        @Volatile
        private var lon = Double.MAX_VALUE

        private val LOCATION_APIS = arrayOf(
            IpApiEntry("https://ip-api.com/json/$", "lat", "lon"),
            IpApiEntry("https://freeipapi.com/api/json/$", "latitude", "longitude"),
            IpApiEntry("https://ipapi.co/$/json/", "latitude", "longitude")
        )

        private val WEATHER_RESOURCES = mapOf(
        "clearsky" to WeatherResource(
            R.drawable.clear_day, R.drawable.clear_night,
            R.string.clear_sky
        ),
        "cloudy" to WeatherResource(R.drawable.cloudy, R.drawable.cloudy, R.string.cloudy),
        "fair" to WeatherResource(
            R.drawable.mostly_clear_day, R.drawable.mostly_clear_night,
            R.string.fair
        ),
        "fog" to WeatherResource(
            R.drawable.haze_fog_dust_smoke, R.drawable.haze_fog_dust_smoke,
            R.string.fog
        ),
        "heavyrain" to WeatherResource(
            R.drawable.heavy_rain, R.drawable.heavy_rain,
            R.string.heavy_rain
        ),
        "heavyrainandthunder" to WeatherResource(
            R.drawable.strong_thunderstorms, R.drawable.strong_thunderstorms,
            R.string.heavy_rain_and_thunder
        ),
        "heavyrainshowers" to WeatherResource(
            R.drawable.scattered_showers_day, R.drawable.scattered_showers_night,
            R.string.heavy_rain_showers
        ),
        "heavyrainshowersandthunder" to WeatherResource(
            R.drawable.isolated_scattered_thunderstorms_day, R.drawable.isolated_scattered_thunderstorms_night,
            R.string.heavy_rain_showers_and_thunder
        ),
        "heavysleet" to WeatherResource(
            R.drawable.sleet_hail, R.drawable.sleet_hail,
            R.string.heavy_sleet
        ),
        "heavysleetandthunder" to WeatherResource(
            R.drawable.sleet_hail, R.drawable.sleet_hail,
            R.string.heavy_sleet_and_thunder
        ),
        "heavysleetshowers" to WeatherResource(
            R.drawable.sleet_hail, R.drawable.sleet_hail,
            R.string.heavy_sleet_showers
        ),
        "heavysleetshowersandthunder" to WeatherResource(
            R.drawable.sleet_hail, R.drawable.sleet_hail,
            R.string.heavy_sleet_showers_and_thunder
        ),
        "heavysnow" to WeatherResource(
            R.drawable.heavy_snow, R.drawable.heavy_snow,
            R.string.heavy_snow
        ),
        "heavysnowandthunder" to WeatherResource(
            R.drawable.blowing_snow, R.drawable.blowing_snow,
            R.string.heavy_snow_and_thunder
        ),
        "heavysnowshowers" to WeatherResource(
            R.drawable.scattered_snow_showers_day, R.drawable.scattered_snow_showers_night,
            R.string.heavy_snow_showers
        ),
        "heavysnowshowersandthunder" to WeatherResource(
            R.drawable.heavy_snow, R.drawable.heavy_snow,
            R.string.heavy_snow_showers_and_thunder
        ),
        "lightrain" to WeatherResource(R.drawable.drizzle, R.drawable.drizzle, R.string.light_rain),
        "lightrainandthunder" to WeatherResource(
            R.drawable.isolated_scattered_thunderstorms_day, R.drawable.isolated_scattered_thunderstorms_night,
            R.string.light_rain_and_thunder
        ),
        "lightrainshowers" to WeatherResource(
            R.drawable.scattered_showers_day, R.drawable.scattered_showers_night,
            R.string.light_rain_showers
        ),
        "lightrainshowersandthunder" to WeatherResource(
            R.drawable.isolated_scattered_thunderstorms_day, R.drawable.isolated_scattered_thunderstorms_night,
            R.string.light_rain_showers_and_thunder
        ),
        "lightsleet" to WeatherResource(
            R.drawable.flurries, R.drawable.flurries,
            R.string.light_sleet
        ),
        "lightsleetandthunder" to WeatherResource(
            R.drawable.flurries, R.drawable.flurries,
            R.string.light_sleet_and_thunder
        ),
        "lightsleetshowers" to WeatherResource(
            R.drawable.flurries, R.drawable.flurries,
            R.string.light_sleet_showers
        ),
        "lightsnow" to WeatherResource(
            R.drawable.flurries, R.drawable.flurries,
            R.string.light_snow
        ),
        "lightsnowandthunder" to WeatherResource(
            R.drawable.scattered_snow_showers_day, R.drawable.scattered_snow_showers_night,
            R.string.light_snow_and_thunder
        ),
        "lightsnowshowers" to WeatherResource(
            R.drawable.flurries, R.drawable.flurries,
            R.string.light_snow_showers
        ),
        "lightssleetshowersandthunder" to WeatherResource(
            R.drawable.flurries, R.drawable.flurries,
            R.string.light_sleet_showers_and_thunder
        ),
        "lightssnowshowersandthunder" to WeatherResource(
            R.drawable.flurries, R.drawable.flurries,
            R.string.light_snow_showers_and_thunder
        ),
        "partlycloudy" to WeatherResource(
            R.drawable.partly_cloudy_day, R.drawable.partly_cloudy_night,
            R.string.partly_cloudy
        ),
        "rain" to WeatherResource(R.drawable.showers_rain, R.drawable.showers_rain, R.string.rain),
        "rainandthunder" to WeatherResource(
            R.drawable.isolated_thunderstorms, R.drawable.isolated_thunderstorms,
            R.string.rain_and_thunder
        ),
        "rainshowers" to WeatherResource(
            R.drawable.showers_rain, R.drawable.showers_rain,
            R.string.rain_showers
        ),
        "rainshowersandthunder" to WeatherResource(
            R.drawable.isolated_scattered_thunderstorms_day, R.drawable.isolated_scattered_thunderstorms_night,
            R.string.rain_showers_and_thunder
        ),
        "sleet" to WeatherResource(R.drawable.sleet_hail, R.drawable.sleet_hail, R.string.sleet),
        "sleetandthunder" to WeatherResource(
            R.drawable.sleet_hail, R.drawable.sleet_hail,
            R.string.sleet_and_thunder
        ),
        "sleetshowers" to WeatherResource(
            R.drawable.sleet_hail, R.drawable.sleet_hail,
            R.string.sleet_showers
        ),
        "sleetshowersandthunder" to WeatherResource(
            R.drawable.sleet_hail, R.drawable.sleet_hail,
            R.string.sleet_showers_and_thunder
        ),
        "snow" to WeatherResource(R.drawable.showers_snow, R.drawable.showers_snow, R.string.snow),
        "snowandthunder" to WeatherResource(
            R.drawable.heavy_snow, R.drawable.heavy_snow,
            R.string.snow_and_thunder
        ),
        "snowshowers" to WeatherResource(
            R.drawable.scattered_snow_showers_day, R.drawable.scattered_snow_showers_night,
            R.string.snow_showers
        ),
        "snowshowersandthunder" to WeatherResource(
            R.drawable.showers_snow, R.drawable.showers_snow,
            R.string.snow_showers_and_thunder
        )
        )

        @JvmStatic
        fun getIcon(iconCode: String?): Int {
            val calendar = Calendar.getInstance()

            val calculator = SunriseSunsetCalculator(Location(lat, lon), calendar.timeZone)

            val sunrise = calculator.getOfficialSunriseCalendarForDate(calendar)
            val sunset = calculator.getOfficialSunsetCalendarForDate(calendar)

            val resource = WEATHER_RESOURCES[iconCode] ?: return R.drawable.unavailable

            return if (sunrise.timeInMillis < calendar.timeInMillis &&
                calendar.timeInMillis < sunset.timeInMillis
            ) {
                resource.day
            } else {
                resource.night
            }
        }

        @JvmStatic
        fun getSummary(iconCode: String?): Int =
            WEATHER_RESOURCES[iconCode]?.summary ?: R.string.unavailable
    }
}
