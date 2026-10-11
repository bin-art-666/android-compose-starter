package com.example.test

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView

class TargetAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private lateinit var tracker: UsageTracker
    private lateinit var manager: WindowManager
    private lateinit var actionIntervention: ActionIntervention
    private var actionScanAt = 0L
    private var actionWindowId = -1
    private var cachedActionRegion: android.graphics.Rect? = null
    private var refreshQueued = false
    private val refresh = Runnable {
        refreshQueued = false
        if (connected) { settleTime(); updateForeground() }
    }
    private var overlay: GoalView? = null
    private var goalParams: WindowManager.LayoutParams? = null
    private var reminder: View? = null
    private var reminderPackage: String? = null
    private var reminderShownAt = 0L
    private var continueButton: Button? = null
    private var endButton: Button? = null
    private var countdown: TextView? = null
    private var reminderProgress: android.widget.ProgressBar? = null
    private val confirmationTick = object : Runnable {
        override fun run() {
            if (reminder == null) return
            val seconds = ReminderTiming.remainingSeconds(reminderShownAt, SystemClock.elapsedRealtime())
            reminderProgress?.progress = ((SystemClock.elapsedRealtime()-reminderShownAt).coerceIn(0,3000)*100/3000).toInt()
            continueButton?.text = if (seconds == 0) "继续观看" else "等待 $seconds 秒"
            countdown?.text = if (seconds == 0) "现在可以做出选择" else "停一停 · $seconds 秒后可以选择"
            continueButton?.isEnabled = seconds == 0
            endButton?.isEnabled = seconds == 0
            continueButton?.alpha = if (seconds == 0) 1f else .45f
            endButton?.alpha = if (seconds == 0) 1f else .45f
            if (seconds > 0) handler.postDelayed(this, 100)
        }
    }
    private var activePackage: String? = null
    private var sessionMs = 0L
    private var previousTick = 0L
    private var connected = false
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "goal_opacity") overlay?.alpha = prefs.getInt("goal_opacity",100).coerceIn(20,100)/100f
        if (key == "enabled" || key == "mode" || key == "value0" || key == "value1" || key == "value2" || key == "value3" || key == "frequency" || key?.startsWith("app") == true) {
            settleTime()
            actionIntervention.clear()
            actionScanAt = 0L
            updateForeground()
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            if (!connected) return
            settleTime()
            updateForeground()
            handler.postDelayed(this, 1000)
        }
    }
    override fun onServiceConnected() {
        prefs = getSharedPreferences("tingke", 0)
        tracker = UsageTracker(prefs)
        manager = getSystemService(WindowManager::class.java)
        actionIntervention = ActionIntervention(this, prefs, handler, ::actionRegion)
        prefs.registerOnSharedPreferenceChangeListener(listener)
        connected = true
        previousTick = SystemClock.elapsedRealtime()
        handler.removeCallbacks(tick)
        handler.post(tick)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!connected) return
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.packageName?.toString() == TARGETS[0]) actionScanAt = 0L
        if (!refreshQueued) { refreshQueued = true; handler.postDelayed(refresh, 150) }
    }
    private fun settleTime() {
        val now = SystemClock.elapsedRealtime()
        val duration = (now - previousTick).coerceIn(0, 2000)
        previousTick = now
        if (activePackage != null && screenAvailable() && reminder == null) {
            sessionMs += duration
            tracker.record(duration)
            prefs.edit().putLong("session_$activePackage", sessionMs).apply()
        }
    }
    private fun screenAvailable(): Boolean = getSystemService(PowerManager::class.java).isInteractive && !getSystemService(KeyguardManager::class.java).isKeyguardLocked
    private fun updateForeground() {
        val mode = prefs.getInt("mode", 0)
        val target = if (screenAvailable() && prefs.getBoolean("enabled", false) && mode in 0..3) {
            // Ignore our overlay and keyboard; only the active application window counts.
            val window = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive }
            val root = window?.root
            val foreground = root?.packageName?.toString()
            @Suppress("DEPRECATION")
            root?.recycle()
            TARGETS.indexOf(foreground).takeIf { it >= 0 && prefs.getBoolean("app$it", it == 0) }?.let { foreground }
        } else null
        if (activePackage != target) {
            hideReminder()
            sessionMs = if (target == null) 0 else prefs.getLong("session_$target", 0)
            activePackage = target
        }
        when {
            target == null -> { hideOverlay(); hideReminder(); actionIntervention.clear() }
            mode == 0 -> { hideReminder(); actionIntervention.clear(); showOverlay() }
            mode == 2 || mode == 3 -> { hideOverlay(); hideReminder(); actionIntervention.update(actionRegion()) }
            else -> {
                actionIntervention.clear()
                hideOverlay()
                val pending = prefs.getBoolean("pending_$target", false)
                if (pending || ReminderTiming.isDue(sessionMs, prefs.getInt("value1", 10))) showReminder(target, pending)
            }
        }
    }
    private fun actionRegion(): android.graphics.Rect? {
        if (!connected || !screenAvailable() || !prefs.getBoolean("enabled", false) || prefs.getInt("mode", 0) !in 2..3 || !prefs.getBoolean("app0", true)) return null
        if (getSystemService(android.view.accessibility.AccessibilityManager::class.java).isTouchExplorationEnabled) return null
        val applicationWindows = windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        if (applicationWindows.size != 1 || windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }) { actionScanAt = 0L; return null }
        val window = applicationWindows.single().takeIf { it.isActive } ?: return null
        val now = SystemClock.elapsedRealtime()
        if (window.id == actionWindowId && actionScanAt > 0 && now - actionScanAt < 1000) return cachedActionRegion?.let { android.graphics.Rect(it) }
        val root = window.root ?: return null
        return try {
            cachedActionRegion = DouyinFeedRegion.find(root)
            actionWindowId = window.id
            actionScanAt = SystemClock.elapsedRealtime()
            cachedActionRegion?.let { android.graphics.Rect(it) }
        } finally { @Suppress("DEPRECATION") root.recycle() }
    }
    private fun showReminder(target: String, pending: Boolean) {
        if (reminder != null) return
        val background = android.widget.FrameLayout(this).apply { setBackgroundColor(Color.argb(175, 21, 45, 41)); isClickable = true; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES }
        val scroll = ScrollView(this).apply { isFillViewport = false }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(24), dp(24), dp(20))
            this.background = rounded(Color.rgb(247,248,243), 24)
        }
        fun text(value: String, size: Float, color: Int) = TextView(this).apply { text = value; textSize = size; setTextColor(color); gravity = Gravity.CENTER; setPadding(0,dp(8),0,dp(8)) }
        card.addView(object : View(this) {
            override fun onDraw(canvas: Canvas) { InterventionArtwork.symbol(canvas,1,0f,0f,width.toFloat()) }
        }, LinearLayout.LayoutParams(dp(46),dp(46)).apply { bottomMargin = dp(18) })
        card.addView(text("给自己一个停顿", 20f, Color.rgb(35,59,54)))
        card.addView(text("本轮已观看 ${sessionMs / 60000} 分钟。\n现在想继续，还是休息一下？", 12f, Color.rgb(109,124,117)))
        val progress = android.widget.ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(InterventionArtwork.colors[1])
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(InterventionArtwork.tints[1])
        }
        card.addView(progress,LinearLayout.LayoutParams(-1,dp(4)).apply { topMargin=dp(12); bottomMargin=dp(12) })
        val status = text("停一停 · 3 秒后可以选择", 13f, Color.rgb(172,117,64)).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        card.addView(status)
        fun action(label: String, primary: Boolean, callback: () -> Unit) = Button(this).apply {
            text = label; textSize = 15f; isAllCaps = false; isEnabled = false
            minHeight = dp(52)
            this.background = rounded(if (primary) InterventionArtwork.colors[1] else InterventionArtwork.tints[1], 15)
            setTextColor(if (primary) Color.WHITE else Color.rgb(35,59,54))
            setOnClickListener { if (ReminderTiming.remainingSeconds(reminderShownAt, SystemClock.elapsedRealtime()) == 0) callback() }
        }
        val resume = action("继续观看", true) { resolveReminder(target, false) }
        val end = action("结束本次使用", false) { resolveReminder(target, true) }
        card.addView(resume, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        card.addView(end, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        card.addView(TextView(this).apply { text = "暂停干预"; textSize = 14f; gravity = Gravity.CENTER; setTextColor(Color.rgb(38,115,96)); setPadding(0,dp(16),0,dp(16)); isClickable = true; isFocusable = true; setOnClickListener { prefs.edit().putBoolean("enabled", false).apply(); hideReminder() } })
        scroll.addView(card)
        val cardWidth = minOf(resources.displayMetrics.widthPixels-dp(48),dp(320))
        background.addView(scroll, android.widget.FrameLayout.LayoutParams(cardWidth,-2,Gravity.CENTER).apply { topMargin = dp(24); bottomMargin = dp(24) })
        val params = WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,android.graphics.PixelFormat.TRANSLUCENT)
        androidx.core.view.ViewCompat.setAccessibilityPaneTitle(background, "视界有度提醒：给自己一个停顿")
        try { manager.addView(background, params) } catch (_: WindowManager.BadTokenException) { return }
        reminder = background; reminderPackage = target; reminderShownAt = SystemClock.elapsedRealtime()
        continueButton = resume; endButton = end; countdown = status; reminderProgress = progress
        if (!pending) { prefs.edit().putBoolean("pending_$target", true).apply(); tracker.increment("reminder") }
        handler.post(confirmationTick)
    }
    private fun resolveReminder(target: String, end: Boolean) {
        if (reminderPackage != target || !screenAvailable()) return
        if (end && !performGlobalAction(GLOBAL_ACTION_HOME)) { countdown?.text = "无法返回桌面，请使用系统主页键。"; return }
        if (end) tracker.increment("ended")
        prefs.edit().putLong("session_$target", 0).putBoolean("pending_$target", false).apply()
        sessionMs = 0
        previousTick = SystemClock.elapsedRealtime()
        if (end) activePackage = null
        hideReminder()
    }
    private fun hideReminder() {
        if (reminder == null) return
        handler.removeCallbacks(confirmationTick)
        reminder?.let { manager.removeView(it) }
        reminder = null; reminderPackage = null; continueButton = null; endButton = null; countdown = null; reminderProgress = null
        previousTick = SystemClock.elapsedRealtime()
    }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun showOverlay() {
        if (overlay == null) {
            val view = GoalView()
            val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.LEFT }
            goalParams = params
            view.alpha = prefs.getInt("goal_opacity",100).coerceIn(20,100)/100f
            try { manager.addView(view, params); overlay = view } catch (_: WindowManager.BadTokenException) { goalParams = null; return }
        }
        overlay?.apply { elapsed = sessionMs; goal = prefs.getInt("value0", 15).coerceIn(5, 60); invalidate() }
    }
    private fun hideOverlay() { overlay?.let { manager.removeView(it) }; overlay = null; goalParams = null }
    override fun onInterrupt() { if (connected) { settleTime(); activePackage = null; hideOverlay(); hideReminder(); actionIntervention.clear() } }
    override fun onDestroy() {
        if (connected) { settleTime(); connected = false; prefs.unregisterOnSharedPreferenceChangeListener(listener); hideOverlay(); hideReminder(); actionIntervention.clear() }
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private inner class GoalView : View(this@TargetAccessibilityService) {
        var elapsed = 0L
        var goal = 15
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
        init { contentDescription = "目标进度环，可拖动位置。不透明度可在视界有度目标模式设置中调节。"; isClickable = true }
        @Suppress("DEPRECATION")
        private fun movementBounds(): android.graphics.Rect {
            val metrics = android.util.DisplayMetrics().also { manager.defaultDisplay.getRealMetrics(it) }
            val insets = rootWindowInsets
            val top = maxOf(dp(24),insets?.systemWindowInsetTop ?: 0)+dp(8)
            val bottom = maxOf(dp(24),insets?.systemWindowInsetBottom ?: 0)+dp(8)
            return android.graphics.Rect(dp(8),top,maxOf(dp(8),metrics.widthPixels-width-dp(8)),maxOf(top,metrics.heightPixels-height-bottom))
        }
        override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
            super.onSizeChanged(w,h,oldw,oldh)
            post {
                if (this@TargetAccessibilityService.overlay !== this) return@post
                val params = goalParams ?: return@post
                val bounds = movementBounds()
                params.x = bounds.left+((bounds.right-bounds.left)*prefs.getFloat("goal_position_x",1f).coerceIn(0f,1f)).toInt()
                params.y = bounds.top+((bounds.bottom-bounds.top)*prefs.getFloat("goal_position_y",.1f).coerceIn(0f,1f)).toInt()
                manager.updateViewLayout(this,params)
            }
        }
        override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
            val params = goalParams ?: return false
            when(event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> { downX=event.rawX; downY=event.rawY; startX=params.x; startY=params.y; dragging=false }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx=event.rawX-downX; val dy=event.rawY-downY
                    if (kotlin.math.abs(dx)>slop || kotlin.math.abs(dy)>slop) dragging=true
                    if (dragging) {
                        val bounds=movementBounds()
                        params.x=(startX+dx.toInt()).coerceIn(bounds.left,bounds.right)
                        params.y=(startY+dy.toInt()).coerceIn(bounds.top,bounds.bottom)
                        manager.updateViewLayout(this,params)
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        val bounds=movementBounds()
                        prefs.edit().putFloat("goal_position_x",(params.x-bounds.left).toFloat()/maxOf(1,bounds.right-bounds.left))
                            .putFloat("goal_position_y",(params.y-bounds.top).toFloat()/maxOf(1,bounds.bottom-bounds.top)).apply()
                    } else if (event.actionMasked == android.view.MotionEvent.ACTION_UP) performClick()
                    dragging=false
                }
            }
            return true
        }
        override fun performClick(): Boolean { super.performClick(); return true }
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            paint.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
            setMeasuredDimension(maxOf(dp(150), dp(62) + paint.measureText("目标已到 · 999分").toInt()), dp(48))
        }
        override fun onDraw(canvas: Canvas) {
            val d = resources.displayMetrics.density
            paint.style = Paint.Style.FILL; paint.color = Color.rgb(247,248,243)
            canvas.drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),24*d,24*d,paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3*d; paint.color = Color.rgb(211,223,204)
            val ring = RectF(13*d,12*d,37*d,36*d)
            canvas.drawOval(ring,paint)
            val reached = elapsed >= goal * 60000L
            paint.color = if (reached) Color.rgb(172,117,64) else Color.rgb(38,115,96)
            canvas.drawArc(ring,-90f,(elapsed.toFloat() / (goal * 60000)).coerceAtMost(1f)*360,false,paint)
            paint.style = Paint.Style.FILL; paint.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
            canvas.drawText(if (reached) "目标已到 · ${elapsed / 60000}分" else "${elapsed / 60000}:${(elapsed / 1000 % 60).toString().padStart(2,'0')} / ${goal}分钟",46*d,29*d,paint)
        }
    }
    companion object { val TARGETS = listOf("com.ss.android.ugc.aweme") }
}
