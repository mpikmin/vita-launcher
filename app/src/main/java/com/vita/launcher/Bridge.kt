package com.vita.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * Everything the web layer can ask the system for. Each method is small and
 * synchronous; icons are fetched one at a time as bubbles scroll into view so
 * a device with 200 apps doesn't stall on first paint.
 */
class Bridge(private val activity: Activity, private val web: WebView) {

    private val pm: PackageManager get() = activity.packageManager
    private val iconCache = HashMap<String, String>()

    /** All launchable apps: label, package, and activity class. */
    @JavascriptInterface
    fun apps(): String {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val found = pm.queryIntentActivities(intent, 0)
        val out = JSONArray()
        found.forEach { ri ->
            val pkg = ri.activityInfo.packageName
            if (pkg == activity.packageName) return@forEach   // hide ourselves
            out.put(JSONObject().apply {
                put("label", ri.loadLabel(pm).toString())
                put("pkg", pkg)
                put("cls", ri.activityInfo.name)
                put("system", (ri.activityInfo.applicationInfo.flags and
                        android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)
            })
        }
        return out.toString()
    }

    /** A PNG data URI for one package. Cached, so repeat calls are cheap. */
    @JavascriptInterface
    fun icon(pkg: String): String {
        iconCache[pkg]?.let { return it }
        return try {
            val d: Drawable = pm.getApplicationIcon(pkg)
            val size = 144
            val bmp = if (d is BitmapDrawable && d.bitmap != null) {
                Bitmap.createScaledBitmap(d.bitmap, size, size, true)
            } else {
                Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { b ->
                    val c = Canvas(b)
                    d.setBounds(0, 0, size, size)
                    d.draw(c)
                }
            }
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
            val uri = "data:image/png;base64," +
                    Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
            iconCache[pkg] = uri
            uri
        } catch (e: Exception) {
            ""
        }
    }

    /** The dominant colour of an app's icon, so its LiveArea art matches it. */
    @JavascriptInterface
    fun iconColor(pkg: String): String {
        return try {
            val d = pm.getApplicationIcon(pkg)
            val bmp = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            d.setBounds(0, 0, 24, 24)
            d.draw(c)
            var r = 0L; var g = 0L; var b = 0L; var n = 0L
            for (x in 0 until 24) for (y in 0 until 24) {
                val p = bmp.getPixel(x, y)
                val a = (p ushr 24) and 0xff
                if (a < 40) continue
                r += (p shr 16) and 0xff; g += (p shr 8) and 0xff; b += p and 0xff; n++
            }
            if (n == 0L) "#3a6ea8"
            else String.format("#%02x%02x%02x", (r / n).toInt(), (g / n).toInt(), (b / n).toInt())
        } catch (e: Exception) {
            "#3a6ea8"
        }
    }

    @JavascriptInterface
    fun launch(pkg: String) {
        val i = pm.getLaunchIntentForPackage(pkg) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { activity.startActivity(i) }
    }

    @JavascriptInterface
    fun appInfo(pkg: String) {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { activity.startActivity(i) }
    }

    @JavascriptInterface
    fun uninstall(pkg: String) {
        val i = Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { activity.startActivity(i) }
    }

    @JavascriptInterface
    fun openSettings() {
        runCatching {
            activity.startActivity(Intent(Settings.ACTION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Opens the picker so the user can make this the default home app. */
    @JavascriptInterface
    fun chooseDefaultHome() {
        runCatching {
            activity.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    @JavascriptInterface
    fun isDefaultHome(): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val res = pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        return res?.activityInfo?.packageName == activity.packageName
    }

    /** Battery level and charging state for the status bar. */
    @JavascriptInterface
    fun battery(): String {
        return try {
            val bm = activity.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val charging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) bm.isCharging else false
            JSONObject().put("level", level).put("charging", charging).toString()
        } catch (e: Exception) {
            JSONObject().put("level", -1).put("charging", false).toString()
        }
    }

    @JavascriptInterface
    fun vibrate(ms: Int) {
        runCatching {
            val v = activity.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION") v.vibrate(ms.toLong())
            }
        }
    }

    /** Safe-area insets in CSS pixels, so the UI can dodge the notch and gesture bar. */
    @JavascriptInterface
    fun insets(): String {
        val d = activity.resources.displayMetrics.density
        var top = 0; var bottom = 0
        val res = activity.resources
        val idTop = res.getIdentifier("status_bar_height", "dimen", "android")
        if (idTop > 0) top = (res.getDimensionPixelSize(idTop) / d).toInt()
        val idBot = res.getIdentifier("navigation_bar_height", "dimen", "android")
        if (idBot > 0) bottom = (res.getDimensionPixelSize(idBot) / d).toInt()
        return JSONObject().put("top", top).put("bottom", bottom).toString()
    }
}
