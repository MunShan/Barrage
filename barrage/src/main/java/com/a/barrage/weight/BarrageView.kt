package com.a.barrage.weight

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlinx.coroutines.*

class BarrageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {
    var getShowTime: (() -> Long)? = null
    var actionBarrage: ((Barrage) -> Unit)? = null
    private val scope = MainScope()
    private val barrageQueue = BarrageQueue().apply {
        rowCount = 7
        getShowTime = this@BarrageView::getCurrentTime
        getMeasuredWidth = this@BarrageView::getMeasuredWidth
    }
    private val textPaint = Paint().apply {
        color = Color.BLUE
        textSize = 36f
    }
    private val mSurfaceHolder: SurfaceHolder?
        get() = holder
    private var job: Job? = null
    private val yStart = 36f
    private val yOffset = 48

    init {
        holder.apply {
            addCallback(this@BarrageView)
            setFormat(PixelFormat.TRANSPARENT)
            setZOrderOnTop(true)
        }
        isClickable = true
        isFocusable = true
        keepScreenOn = true
        isFocusableInTouchMode = true
    }

    fun setBarrageData(data: List<Barrage>, isReset: Boolean = true) {
        val measuredWidth = measuredWidth.toFloat()
        if (measuredWidth <= 0) {
            // todo lifecycleScope
            scope.launch {
                delay(128)
                setBarrageData(data, isReset)
            }
            return
        }
        data.forEach {
            val count = it.setCount?.size
            if (count != null && count > 1) {
                it.text = "${it.text}($count)"
            }
            it.textWidth = textPaint.measureText(it.text)
            it.lastX = measuredWidth.toInt()
            it.xOffset = it.text.length - 1.coerceAtLeast(2)
        }
        if (isReset) {
            barrageQueue.setBarrageData(data)
        } else {
            barrageQueue.addBarrageData(data)
        }
    }

    /**
     * @return [0,length]
     */
    private fun getCurrentTime() = getShowTime?.invoke() ?: 0


    private suspend fun action(isActive: () -> Boolean) {
        val delayTime = 128L
        while (isActive()) {
            var drawCount = 0
            var canvas: Canvas? = null
            try {
                canvas = mSurfaceHolder?.lockCanvas()
                canvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                var y = yStart
                for (q in barrageQueue.showBarrageRow) {
                    for (barrage in q) {
                        val x = barrage.lastX.toFloat()
                        canvas?.drawText(
                            barrage.text, x, y, textPaint
                        )
                        drawCount++
                    }
                    y += yOffset
                }
                for (q in barrageQueue.showBarrageRow) {
                    for (barrage in q) {
                        if (barrage.isLock) {
                            continue
                        }
                        barrage.lastX -= barrage.xOffset
                    }
                }
            } finally {
                if (canvas != null) {
                    mSurfaceHolder?.unlockCanvasAndPost(canvas)
                }
            }
            delay(if (drawCount == 0) delayTime else 1L)
        }
    }

    override fun surfaceCreated(p0: SurfaceHolder) {
        job?.cancel()
        job = scope.launch {
            action {
                isActive
            }
        }
    }

    override fun surfaceChanged(p0: SurfaceHolder, p1: Int, p2: Int, p3: Int) = Unit

    override fun surfaceDestroyed(p0: SurfaceHolder) {
        job?.cancel()
        job = null
    }

    var lastLock: Barrage? = null
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        val e = event ?: return super.onTouchEvent(event)
        when {
            e.action == MotionEvent.ACTION_DOWN && lastLock == null -> {
                val barrage = findDown(e) ?: return false
                barrage.isLock = true
                lastLock = barrage
                // todo
                scope.launch {
                    delay(2 * 1000L)
                    if (lastLock == barrage) {
                        actionBarrage?.invoke(barrage)
                    }
                }
                return true
            }
            e.action == MotionEvent.ACTION_UP || e.action == MotionEvent.ACTION_CANCEL -> {
                lastLock?.isLock = false
                lastLock = null
            }
            e.action == MotionEvent.ACTION_MOVE && lastLock != null -> {
                val barrage = findDown(e) ?: return false
                if (barrage == lastLock) {
                    return true
                }
                lastLock?.isLock = false
                lastLock = null
            }
        }
        return super.onTouchEvent(e)
    }

    private fun findDown(e: MotionEvent): Barrage? {
        var downY = e.y - yStart
        if (downY < 0) {
            return null
        }
        for (q in barrageQueue.showBarrageRow) {
            if (downY < yOffset) {
                val downX = e.x
                for (b in q) {
                    if (b.lastX <= downX && b.lastX + b.textWidth >= downX) {
                        return b
                    }
                }
                break
            } else {
                downY -= yOffset
            }
        }
        return null
    }
}