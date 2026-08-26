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

package adrianogba.stario.launcher.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.net.ConnectivityManager
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import androidx.annotation.FloatRange
import com.github.sisyphsu.dateparser.DateParser
import com.google.gson.Gson
import adrianogba.stario.launcher.BuildConfig
import adrianogba.stario.launcher.apps.ProfileManager
import adrianogba.stario.launcher.services.AccessibilityService
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.math.pow

object Utils {
    const val USER_AGENT: String =
        "Mozilla/5.0 (Linux; Android 6.0.1; Nexus 5X Build/MMB29P) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/W.X.Y.Z Mobile Safari/537.36 " +
                "(compatible; Googlebot/2.1; +https://www.google.com/bot.html)"

    private const val TAG = "Utils"

    private val executorPool = Executors.newCachedThreadPool()

    private val IPV4_APIS = arrayOf(
        "https://checkip.amazonaws.com/",
        "https://ipv4.icanhazip.com/",
        "https://ipv4.seeip.org",
        "https://api.ipify.org/"
    )

    private val IMPERIAL_COUNTRIES = setOf(
        "US", // United States
        "PW", // Palau
        "MH", // Marshall Islands
        "MP", // Northern Mariana Islands
        "AS", // American Samoa
        "KY", // Cayman Islands
        "VI", // U.S. Virgin Islands
        "FM", // Micronesia
        "GU", // Guam
        "LR", // Liberia
        "PR"  // Puerto Rico
    )

    private var dateParser: DateParser? = null
    private var gson: Gson? = null

    @JvmStatic
    fun submitTask(runnable: Runnable): Future<*> = executorPool.submit(runnable)

    /**
     * Completes with null when the callable throws, which is what the Java
     * version did. The cast keeps that platform-typed behaviour rather than
     * pushing a nullable type onto every caller.
     */
    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun <O> submitTask(callable: Callable<O>): CompletableFuture<O> =
        CompletableFuture.supplyAsync({
            try {
                callable.call()
            } catch (exception: Exception) {
                Log.e(TAG, "submitTask: ", exception)

                null
            }
        }, executorPool) as CompletableFuture<O>

    @JvmStatic
    fun parseDate(date: String): Date {
        val parser = dateParser ?: DateParser.newBuilder().build().also { dateParser = it }

        return parser.parseDate(date)
    }

    @JvmStatic
    fun getGsonInstance(): Gson = gson ?: Gson().also { gson = it }

    @JvmStatic
    fun isSystemUsingImperial(context: Context?): Boolean {
        if (context == null) {
            return false
        }

        val locale = context.resources.configuration.locales.get(0)

        return IMPERIAL_COUNTRIES.contains(locale.country)
    }

    @JvmStatic
    fun isMinimumSDK(sdk: Int): Boolean = Build.VERSION.SDK_INT >= sdk

    @JvmStatic
    fun toFahrenheit(celsius: Double): Double = celsius * 1.8 + 32

    @JvmStatic
    fun msToMph(speed: Double): Double = speed * 2.237

    @JvmStatic
    fun intToUUID(value: Int): UUID = UUID.nameUUIDFromBytes(
        ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array()
    )

    @JvmStatic
    fun getGenericInterpolatedValue(
        @FloatRange(from = 0.0, to = 1.0) value: Double
    ): Double = if (value < 0.5) {
        4 * value * value * value
    } else {
        1 - (-2 * value + 2).pow(3) / 2
    }

    /**
     * The launcher apps service is nullable because ImageUtils passes a nullable
     * one through. The Java version would have thrown there; null now means no
     * main activity, which the callers already handle.
     */
    @JvmStatic
    fun getMainActivity(
        launcherApps: LauncherApps?, packageName: String?, handle: UserHandle?
    ): LauncherActivityInfo? = launcherApps?.getActivityList(packageName, handle)?.firstOrNull()

    @JvmStatic
    fun getMainActivity(
        context: Context, packageName: String?, handle: UserHandle?
    ): LauncherActivityInfo? =
        getMainActivity(context.getSystemService(LauncherApps::class.java), packageName, handle)

    @JvmStatic
    fun isMainProfile(handle: UserHandle?): Boolean = handle == ProfileManager.getOwner()

    @JvmStatic
    fun isProfileAvailable(context: Context, handle: UserHandle?): Boolean =
        context.getSystemService(UserManager::class.java).isUserUnlocked(handle)

    @JvmStatic
    @Suppress("DEPRECATION")
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?

        return connectivityManager?.activeNetworkInfo?.isConnected == true
    }

    @JvmStatic
    fun getPublicIPAddress(): String? {
        for (api in IPV4_APIS) {
            try {
                val reader = BufferedReader(
                    InputStreamReader(URL(api).openStream(), StandardCharsets.UTF_8)
                )

                return reader.readLine()
            } catch (exception: Exception) {
                Log.e(TAG, "getPublicIPAddress: ", exception)
            }
        }

        return null
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readStream(inputStream: InputStream): String {
        BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
            return reader.lineSequence().joinToString("")
        }
    }

    @JvmStatic
    fun isNotificationServiceEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners"
        )

        if (TextUtils.isEmpty(flat)) {
            return false
        }

        return flat.split(":")
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { BuildConfig.APPLICATION_ID == it.packageName }
    }

    @JvmStatic
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val service = BuildConfig.APPLICATION_ID + "/" +
                AccessibilityService::class.java.canonicalName

        val enabled = try {
            Settings.Secure.getInt(
                context.applicationContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (exception: Settings.SettingNotFoundException) {
            Log.e(
                TAG,
                "Error finding setting, default accessibility not found: " + exception.message
            )

            0
        }

        if (enabled != 1) {
            return false
        }

        val settingValue = Settings.Secure.getString(
            context.applicationContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(settingValue)

        while (splitter.hasNext()) {
            if (splitter.next().equals(service, ignoreCase = true)) {
                return true
            }
        }

        return false
    }
}
