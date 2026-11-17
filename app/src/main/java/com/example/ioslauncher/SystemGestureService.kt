package com.example.ioslauncher

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.content.Context
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * Optional system-wide gesture bridge.
 *
 * The narrow edge zones translate inward swipes to Android's global Back action.
 * The bottom zone sits immediately above the system navigation bar so three-button
 * navigation remains clickable while quick-up / hold-up gestures trigger Home / Recents.
 */
class SystemGestureService : AccessibilityService() {
    private lateinit var windowManager: WindowManager
    private val overlays = mutableListOf<View>()
    private var density = 1f
    private var edgeSize = 22
    private var bottomSize = 34
    private var navBarHeight = 0
    private val threshold get() = (58 * density).toInt()
    private val longSwipeMs = 260L

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        density = resources.displayMetrics.density
        edgeSize = (22 * density).toInt()
        bottomSize = (34 * density).toInt()
        navBarHeight = getNavigationBarHeight()
        addGestureZone(Gravity.START or Gravity.TOP, edgeSize, WindowManager.LayoutParams.MATCH_PARENT, 0, 0, Zone.LEFT)
        addGestureZone(Gravity.END or Gravity.TOP, edgeSize, WindowManager.LayoutParams.MATCH_PARENT, 0, 0, Zone.RIGHT)
        addGestureZone(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, WindowManager.LayoutParams.MATCH_PARENT, bottomSize, 0, navBarHeight, Zone.BOTTOM)
    }

    private enum class Zone { LEFT, RIGHT, BOTTOM }

    private fun getNavigationBarHeight(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id != 0) resources.getDimensionPixelSize(id) else (24 * density).toInt()
    }

    private fun addGestureZone(gravity: Int, width: Int, height: Int, x: Int, y: Int, zone: Zone) {
        val view = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            isClickable = true
            setOnTouchListener(GestureTouchListener(zone))
        }
        val params = WindowManager.LayoutParams(
            width, height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.x = x
            this.y = y
        }
        try {
            windowManager.addView(view, params)
            overlays += view
        } catch (_: Exception) { }
    }

    private inner class GestureTouchListener(private val zone: Zone) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var downAt = 0L

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    downAt = SystemClock.uptimeMillis()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    val elapsed = SystemClock.uptimeMillis() - downAt
                    when (zone) {
                        Zone.LEFT -> if (dx >= threshold && abs(dx) > abs(dy) * 1.25f) performGlobalAction(GLOBAL_ACTION_BACK)
                        Zone.RIGHT -> if (dx <= -threshold && abs(dx) > abs(dy) * 1.25f) performGlobalAction(GLOBAL_ACTION_BACK)
                        Zone.BOTTOM -> if (dy <= -threshold && abs(dy) > abs(dx) * 1.15f) {
                            if (elapsed >= longSwipeMs) performGlobalAction(GLOBAL_ACTION_RECENTS)
                            else performGlobalAction(GLOBAL_ACTION_HOME)
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> return true
            }
            return true
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        removeGestureZones()
    }

    override fun onDestroy() {
        removeGestureZones()
        super.onDestroy()
    }

    private fun removeGestureZones() {
        if (!::windowManager.isInitialized) return
        overlays.forEach { view ->
            try { windowManager.removeView(view) } catch (_: Exception) { }
        }
        overlays.clear()
    }
}
