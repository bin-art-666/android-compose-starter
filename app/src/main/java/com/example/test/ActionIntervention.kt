package com.example.test

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

/** A feed touch window. Taps are replayed; vertical swipes pass through the hold gate. */
class ActionIntervention(
    private val service: AccessibilityService,
    private val prefs: SharedPreferences,
    private val handler: Handler,
    private val currentRegion: () -> Rect?
) {
    private val manager = service.getSystemService(WindowManager::class.java)
    private var region: Rect? = null
    private var view: TouchView? = null
    private var gate = SwipeGate()
    private data class Stroke(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val duration: Long)
    private var pending: Stroke? = null
    private var replaying = false
    private var generation = 0
    private var ready = false
    private var waitStartedAt = 0L
    private fun slowMode() = prefs.getInt("mode", 0) == 3
    fun update(next: Rect?) {
        if (next == null) { clear(); return }
        if (replaying) return
        if (region != next) {
            // Layout changes during a hold must not discard the user's progress or swipe count.
            if (pending != null && view != null) return
            view?.let { handler.removeCallbacks(it.holdTick); handler.removeCallbacks(it.waitTick); manager.removeView(it) }
            view = null
            region = Rect(next)
        }
        if (view != null) return
        val touch = TouchView()
        val params = WindowManager.LayoutParams(next.width(), next.height(), WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.LEFT; x = next.left; y = next.top }
        try { manager.addView(touch, params); view = touch } catch (_: WindowManager.BadTokenException) { view = null }
        if (view != null && pending != null && slowMode() && !ready) handler.post(touch.waitTick)
    }
    fun clear() {
        generation++
        view?.let { handler.removeCallbacks(it.holdTick); handler.removeCallbacks(it.waitTick); manager.removeView(it) }
        view = null; region = null; pending = null; gate = SwipeGate(); replaying = false; ready = false
    }
    private fun replay(stroke: Stroke) {
        val feed = currentRegion() ?: run { clear(); return }
        val approved = ready
        // A short or diagonal user swipe may be detected without moving the feed far enough.
        // Once approved, perform a deliberate vertical swipe in the middle of the video.
        val replayStroke = if (approved && stroke.y1 != stroke.y2) {
            val center = feed.exactCenterX()
            val upper = feed.top + feed.height() * .25f
            val lower = feed.top + feed.height() * .75f
            if (stroke.y2 < stroke.y1) Stroke(center, lower, center, upper, 300)
            else Stroke(center, upper, center, lower, 300)
        } else stroke
        pending = null; gate.cancelHold()
        ready = false
        replaying = true
        view?.let { handler.removeCallbacks(it.holdTick); handler.removeCallbacks(it.waitTick); manager.removeViewImmediate(it) }; view = null
        val token = ++generation
        val path = Path().apply { moveTo(replayStroke.x1, replayStroke.y1); if (replayStroke.x1 != replayStroke.x2 || replayStroke.y1 != replayStroke.y2) lineTo(replayStroke.x2, replayStroke.y2) }
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, replayStroke.duration.coerceIn(1, 600))).build()
        fun finish(success: Boolean) {
            if (token != generation || !replaying) return
            android.util.Log.i("ActionVerify", "gesture finished success=$success token=$token generation=$generation")
            replaying = false
            // Restore interception after cancellation. A completed hold remains approved.
            if (!success && approved) { pending = stroke; ready = true }
            update(currentRegion())
        }
        // Let WindowManager commit removal before the first injected DOWN reaches the app.
        handler.postDelayed({
            if (token != generation || !replaying) return@postDelayed
            if (currentRegion() == null) { clear(); return@postDelayed }
            val accepted = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) = finish(true)
                override fun onCancelled(gestureDescription: GestureDescription?) = finish(false)
            }, handler)
            android.util.Log.i("ActionVerify", "dispatch accepted=$accepted stroke=$replayStroke")
            if (!accepted) finish(false)
            handler.postDelayed({ finish(false) }, 1200)
        }, 100)
    }
    private inner class TouchView : View(service) {
        private val density = resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var x1 = 0f; private var y1 = 0f
        private var downTouchAt = 0L
        private var pauseTouch = false
        private var holding = false
        private var cancelled = false
        private var touching = false
        private fun cardBounds() = android.graphics.RectF().apply {
            val cardWidth = minOf(width - 40 * density, 320 * density)
            val cardHeight = minOf(height - 24 * density, 320 * density)
            set((width-cardWidth)/2, (height-cardHeight)/2, (width+cardWidth)/2, (height+cardHeight)/2)
        }
        private fun buttonBounds(): android.graphics.RectF {
            val card = cardBounds()
            return android.graphics.RectF(card.left+24*density,card.bottom-110*density,card.right-24*density,card.bottom-58*density)
        }
        private fun pauseBounds(): android.graphics.RectF {
            val card = cardBounds()
            return android.graphics.RectF(card.left+24*density,card.bottom-54*density,card.right-24*density,card.bottom-6*density)
        }
        val waitTick = object : Runnable {
            override fun run() {
                if (view !== this@TouchView || pending == null || !slowMode()) return
                if (currentRegion() == null) { clear(); return }
                ready = SlowTiming.progress(waitStartedAt, SystemClock.uptimeMillis(), prefs.getInt("value3", 3)) >= 1f
                invalidate()
                // Dispatch only with no finger down, so the injected swipe isn't cancelled.
                if (ready && !touching) pending?.let(::replay)
                else if (!ready) handler.postDelayed(this, 100)
            }
        }
        val holdTick = object : Runnable {
            override fun run() {
                if (!holding || view !== this@TouchView || pending == null) return
                if (currentRegion() == null) { clear(); return }
                if (gate.progress(SystemClock.uptimeMillis(), prefs.getInt("value2", 2)) >= 1f) {
                    holding = false
                    ready = true
                    invalidate()
                } else { invalidate(); handler.postDelayed(this, 100) }
            }
        }
        init {
            contentDescription = if (slowMode()) "视界有度慢速干预。滑动后等待倒计时切换；卡片底部可暂停干预。" else "视界有度动作干预。滑动后持续长按以切换；卡片底部可暂停干预。"
            isClickable = true; isFocusable = true
            setOnClickListener { prefs.edit().putBoolean("enabled", false).apply(); clear() }
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touching = true
                    x1 = event.rawX; y1 = event.rawY
                    downTouchAt = event.eventTime
                    cancelled = false
                    pauseTouch = pending != null && pauseBounds().contains(event.x,event.y)
                    if (pending != null && buttonBounds().contains(event.x,event.y) && !ready && !slowMode()) { holding = true; gate.beginHold(downTouchAt); handler.post(holdTick) }
                }
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                    if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        touching = false
                        if (slowMode() && pending != null) handler.post(waitTick)
                    }
                    cancelled = true
                    holding = false; gate.cancelHold(); handler.removeCallbacks(holdTick); invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (holding && (abs(event.rawX - x1) > 24 * density || abs(event.rawY - y1) > 24 * density)) {
                        holding = false; gate.cancelHold(); handler.removeCallbacks(holdTick); invalidate()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    touching = false
                    val completedHold = holding && gate.progress(event.eventTime, prefs.getInt("value2", 2)) >= 1f
                    holding = false; gate.cancelHold(); handler.removeCallbacks(holdTick)
                    if (cancelled) { if (slowMode() && pending != null) handler.post(waitTick); return true }
                    if ((ready || completedHold) && pending != null && !pauseTouch && (slowMode() || buttonBounds().contains(event.x,event.y))) { ready = true; pending?.let(::replay); return true }
                    val dx = event.rawX - x1; val dy = event.rawY - y1
                    if (pauseTouch && abs(dx) < 16 * density && abs(dy) < 16 * density) {
                        performClick(); return true
                    }
                    if (pending != null) { invalidate(); return true }
                    val stroke = Stroke(x1, y1, event.rawX, event.rawY, (event.eventTime - downTouchAt).coerceIn(1, 600))
                    if (abs(dy) > 24 * density && abs(dy) > abs(dx) * 1.2f && gate.requiresHold(prefs.getInt("frequency", 1))) {
                        android.util.Log.i("ActionVerify", "hold requested dx=$dx dy=$dy")
                        pending = stroke; waitStartedAt = SystemClock.uptimeMillis(); invalidate()
                        if (slowMode()) handler.post(waitTick)
                    } else replay(stroke)
                }
            }
            return true
        }
        override fun onDraw(canvas: Canvas) {
            if (pending == null) return
            val card = cardBounds()
            val button = buttonBounds()
            val mode = if (slowMode()) 3 else 2
            val accent = InterventionArtwork.colors[mode]
            canvas.drawColor(Color.argb(175,21,45,41))
            paint.color = Color.rgb(247, 248, 243); paint.style = Paint.Style.FILL
            canvas.drawRoundRect(card,24*density,24*density,paint)
            InterventionArtwork.symbol(canvas,mode,card.centerX()-23*density,card.top+24*density,46*density)
            fun text(value: String, size: Float, y: Float, color: Int) {
                paint.textAlign = Paint.Align.CENTER; paint.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,size,resources.displayMetrics); paint.color = color
                canvas.drawText(value,card.centerX(),y,paint)
            }
            text(if(slowMode()) "下一条，稍等一下" else "长按，再看下一条",20f,card.top+105*density,Color.rgb(35,59,54))
            text(if(slowMode()) "留一点空隙，让注意力跟上节奏。" else "按住下方按钮，完成这次切换。",12f,card.top+140*density,Color.rgb(109,124,117))
            text(if(slowMode()) "等待结束后自动切换" else "长按完成后，松手即可切换",12f,card.top+161*density,Color.rgb(109,124,117))
            val progress = if (ready) 1f else if (slowMode()) SlowTiming.progress(waitStartedAt,SystemClock.uptimeMillis(),prefs.getInt("value3",3)) else gate.progress(SystemClock.uptimeMillis(),prefs.getInt("value2",2))
            val bar = android.graphics.RectF(button.left,button.top-20*density,button.right,button.top-16*density)
            paint.color = InterventionArtwork.tints[mode]; canvas.drawRoundRect(bar,2*density,2*density,paint)
            paint.color = accent; canvas.drawRoundRect(android.graphics.RectF(bar.left,bar.top,bar.left+bar.width()*progress,bar.bottom),2*density,2*density,paint)
            canvas.drawRoundRect(button,15*density,15*density,paint)
            val label = if(ready) "已完成，松手切换" else if(slowMode()) "等待 ${SlowTiming.remainingSeconds(waitStartedAt,SystemClock.uptimeMillis(),prefs.getInt("value3",3))} 秒" else if(holding) "保持长按…" else "按住 ${prefs.getInt("value2",2)} 秒"
            text(label,15f,button.centerY()+5*density,Color.WHITE)
            text("暂停干预",14f,card.bottom-24*density,Color.rgb(38,115,96))
        }
    }
}
