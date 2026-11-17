package com.wangzan101.mika.pocket

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import com.wangzan101.mika.R
import com.wangzan101.mika.cap.phone.a11y.ScreenAccessibility
import com.wangzan101.mika.i18n.tr

/** One instance per TaskService; all sensors and windows are managed on the main thread. */
class PocketGuardManager(private val context: Context) : SensorEventListener, GuardLifecycle.Port {
    private val main = Handler(Looper.getMainLooper())
    private val settings = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val proximity = sensors?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val light = sensors?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val lifecycle = GuardLifecycle(this)
    private var active = false
    private var monitoring = false
    private var closed = false
    private var detector = PocketDetector()
    private var distance = Float.NaN
    private var lux = Float.NaN
    private var guard: View? = null
    private var windowManager: WindowManager? = null
    private var owner: ScreenAccessibility? = null
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == ENABLED) main.post { refresh() }
    }
    private val retryStart = Runnable { if (!closed) refresh() }
    private val timer = object : Runnable {
        override fun run() {
            if (!monitoring) return
            evaluate()
            main.postDelayed(this, 250)
        }
    }

    init { settings.registerOnSharedPreferenceChangeListener(listener) }

    fun setTasksActive(value: Boolean) { main.post { if (!closed) { active = value; refresh() } } }
    private fun refresh() {
        val enabled = settings.getBoolean(ENABLED, false)
        if (!enabled || !active) main.removeCallbacks(retryStart)
        lifecycle.update(enabled, active)
    }
    fun close() {
        main.post {
            if (closed) return@post
            closed = true
            main.removeCallbacks(retryStart)
            settings.unregisterOnSharedPreferenceChangeListener(listener)
            lifecycle.close()
        }
    }

    override fun start(): Boolean {
        if (monitoring) return true
        if (proximity == null || light == null) {
            Log.w(TAG, "pocket guard unavailable: missing sensors")
            return false
        }
        detector = PocketDetector()
        distance = Float.NaN
        lux = Float.NaN
        val p = sensors.registerListener(this, proximity, SensorManager.SENSOR_DELAY_NORMAL)
        val l = sensors.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL)
        if (!p || !l) {
            sensors.unregisterListener(this)
            Log.w(TAG, "pocket guard unavailable: sensor registration failed")
            main.removeCallbacks(retryStart)
            main.postDelayed(retryStart, 10_000)
            return false
        }
        main.removeCallbacks(retryStart)
        monitoring = true
        main.post(timer)
        return true
    }

    override fun stop() {
        main.removeCallbacks(retryStart)
        monitoring = false
        main.removeCallbacks(timer)
        sensors?.unregisterListener(this)
        removeGuard()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> distance = event.values[0]
            Sensor.TYPE_LIGHT -> lux = event.values[0]
        }
        evaluate()
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun evaluate() {
        if (!monitoring) return
        val service = ScreenAccessibility.instance.value
        if (guard != null && owner !== service) { removeGuard(); detector.failed() }
        val ready = !distance.isNaN() && !lux.isNaN() && proximity != null
        val covered = ready && distance < proximity!!.maximumRange && lux < 5f
        val away = ready && distance >= proximity!!.maximumRange
        if (detector.update(covered, ready, away, SystemClock.elapsedRealtime())) {
            if (service == null || !showGuard(service)) detector.failed()
        }
    }

    private fun showGuard(service: ScreenAccessibility): Boolean {
        if (guard != null) return true
        val manager = service.getSystemService(WindowManager::class.java)
        val view = GuardView(service) { detector.dismiss(); removeGuard() }
        @Suppress("DEPRECATION")
        view.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val params = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
        return try {
            manager.addView(view, params)
            windowManager = manager
            guard = view
            owner = service
            true
        } catch (error: RuntimeException) {
            Log.w(TAG, "pocket guard could not attach", error)
            false
        }
    }

    private fun removeGuard() {
        guard?.let { view -> runCatching { windowManager?.removeView(view) } }
        guard = null
        windowManager = null
        owner = null
    }

    companion object {
        const val PREFS = "pocket_guard"
        const val ENABLED = "enabled"
        private const val TAG = "MikaPocketGuard"

        fun sensorsAvailable(context: Context): Boolean {
            val sensors = context.getSystemService(SensorManager::class.java) ?: return false
            return sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null &&
                sensors.getDefaultSensor(Sensor.TYPE_LIGHT) != null
        }
    }
}

private class GuardView(context: Context, private val dismiss: () -> Unit) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var startX = 0f
    private var currentX = 0f
    private var dragging = false
    init {
        contentDescription = tr(R.string.pocket_guard_exit)
        isFocusable = true
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
        info.isClickable = true
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (action == AccessibilityNodeInfo.ACTION_CLICK) {
            dismiss()
            return true
        }
        return super.performAccessibilityAction(action, arguments)
    }
    private fun dp(n: Float) = n * resources.displayMetrics.density

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (Build.VERSION.SDK_INT >= 29) systemGestureExclusionRects = listOf(Rect(0, 0, w, h))
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = dp(24f); val right = width - dp(24f); val bottom = height - dp(32f); val top = bottom - dp(66f)
        paint.color = 0xdd18202b.toInt(); canvas.drawRoundRect(left, top, right, bottom, dp(33f), dp(33f), paint)
        paint.color = Color.WHITE
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics)
        paint.textAlign = Paint.Align.CENTER
        val label = tr(R.string.pocket_guard_exit)
        val availableWidth = (right - left - dp(140f)).coerceAtLeast(0f)
        if (paint.measureText(label) > availableWidth && availableWidth > 0f) {
            paint.textSize *= availableWidth / paint.measureText(label)
        }
        val textBaseline = (top + bottom - paint.fontMetrics.ascent - paint.fontMetrics.descent) / 2
        canvas.drawText(label, width / 2f + dp(15f), textBaseline, paint)
        paint.color = 0xff77d9b1.toInt()
        val cx = left + dp(31f) + if (dragging) (currentX - startX).coerceIn(0f, right - left - dp(62f)) else 0f
        canvas.drawCircle(cx, (top + bottom) / 2, dp(25f), paint)
        paint.color = 0xff123027.toInt(); paint.textSize = dp(24f)
        canvas.drawText("›", cx, (top + bottom) / 2 + dp(8f), paint)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val left = dp(24f); val bottom = height - dp(32f); val top = bottom - dp(66f)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = event.x in left..(left + dp(72f)) && event.y in top..bottom
                startX = event.x; currentX = event.x; invalidate()
            }
            MotionEvent.ACTION_MOVE -> if (dragging) { currentX = event.x; invalidate() }
            MotionEvent.ACTION_UP -> {
                if (dragging && event.x - startX > width * .6f) dismiss()
                dragging = false; invalidate()
            }
            MotionEvent.ACTION_CANCEL -> { dragging = false; invalidate() }
        }
        return true
    }
}
