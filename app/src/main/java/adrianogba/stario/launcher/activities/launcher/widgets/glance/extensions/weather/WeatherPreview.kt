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

package adrianogba.stario.launcher.activities.launcher.widgets.glance.extensions.weather

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import adrianogba.stario.launcher.R
import adrianogba.stario.launcher.activities.launcher.widgets.glance.GlanceViewExtension
import adrianogba.stario.launcher.preferences.Entry
import adrianogba.stario.launcher.themes.ThemedActivity
import adrianogba.stario.launcher.ui.utils.LayoutSizeObserver
import adrianogba.stario.launcher.utils.Utils
import kotlin.math.roundToInt

// Public rather than package-private: Weather, still Java and in this package,
// calls update(Data), and internal would mangle that name on the JVM.
class WeatherPreview : GlanceViewExtension {
    private var preferences: SharedPreferences? = null
    private var hasTemperature = false
    private var temperature: TextView? = null
    private var activity: Activity? = null
    private var hasIcon = false
    private var icon: ImageView? = null
    private var root: View? = null

    override fun inflate(activity: ThemedActivity?, container: LinearLayout?): View? {
        if (activity == null) {
            return null
        }

        this.activity = activity

        val root = activity.layoutInflater.inflate(R.layout.weather_preview, container, false)
        this.root = root

        preferences = activity.applicationContext.getSharedPreferences(Entry.WEATHER)

        icon = root.findViewById(R.id.icon)
        val temperature = root.findViewById<TextView>(R.id.temperature)
        this.temperature = temperature

        val background = root.findViewById<View>(R.id.rotating_background)
        background.background = ResourcesCompat.getDrawable(
            activity.resources, R.drawable.weather_background, activity.getTheme(true)
        )

        LayoutSizeObserver.attach(
            background, LayoutSizeObserver.WIDTH or LayoutSizeObserver.HEIGHT,
            object : LayoutSizeObserver.OnChange {
                override fun onChange(view: View, watchFlags: Int) {
                    background.pivotX = background.width / 2f
                    background.pivotY = background.height / 2f
                }
            })
        background.pivotX = background.width / 2f
        background.pivotY = background.height / 2f

        val rotate = ObjectAnimator.ofFloat(background, View.ROTATION, 0f, -360f)
        rotate.duration = 100000
        rotate.repeatCount = ValueAnimator.INFINITE
        rotate.interpolator = LinearInterpolator()
        rotate.start()

        temperature.setTextColor(
            activity.getAttributeData(
                com.google.android.material.R.attr.colorOnPrimaryContainer, true
            )
        )

        return root
    }

    @SuppressLint("SetTextI18n")
    fun update(data: Weather.Data?) {
        if (data == null || root == null) {
            hasIcon = false
            hasTemperature = false

            update()

            return
        }

        if (!data.temperature.isNaN()) {
            val imperial = preferences!!.getBoolean(
                Weather.IMPERIAL_KEY, Utils.isSystemUsingImperial(activity)
            )

            temperature!!.text = if (imperial) {
                Utils.toFahrenheit(data.temperature).roundToInt().toString() + FAHRENHEIT
            } else {
                data.temperature.roundToInt().toString() + CELSIUS
            }

            hasTemperature = true
        } else {
            hasTemperature = false
        }

        if (data.iconCode != null) {
            icon!!.setImageResource(Weather.getIcon(data.iconCode))
            hasIcon = true
        } else {
            hasIcon = false
        }

        update()
    }

    override fun update() {
        // Kept as a hard dereference: the Java original threw here too when root
        // was still null, despite the guard above implying otherwise.
        root!!.visibility = if (hasIcon && hasTemperature) View.VISIBLE else View.GONE
    }

    private companion object {
        private const val CELSIUS = "\u00B0C"
        private const val FAHRENHEIT = "\u00B0F"
    }
}
