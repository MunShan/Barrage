package com.a.barrage.weight

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlinx.coroutines.*

class BarrageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {
    var getShowTime: (() -> Long)? = null
    private val scope = MainScope()
    private val barrageQueue = BarrageQueue().apply {
        rowCount = 7
        getShowTime = this@BarrageView::getCurrentTime
    }
    private val textPaint = Paint().apply {
        color = Color.BLUE
        textSize = 36f
    }
    private val mSurfaceHolder: SurfaceHolder?
        get() = holder
    private var job: Job? = null

    init {
        holder.apply {
            addCallback(this@BarrageView)
            setFormat(PixelFormat.TRANSPARENT)
            setZOrderOnTop(true)
        }
        isFocusable = true
        keepScreenOn = true
        isFocusableInTouchMode = true
    }

    fun setBarrageData(data: List<Barrage>) {
        val measuredWidth = measuredWidth.toFloat()
        if (measuredWidth <= 0) {
            // todo lifecycleScope
            scope.launch {
                delay(128)
                setBarrageData(data)
            }
            return
        }
        data.forEach {
            it.textWidth = textPaint.measureText(it.text)
            // ([textWidth] / view.width) * [BarrageQueue.keepBarTime] + [startShowTime]
            val rate = it.textWidth / measuredWidth
            it.completeShowTime =
                (rate * barrageQueue.keepBarTime + it.startShowTime).toInt()
        }
        barrageQueue.setBarrageData(data)
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
                var y = 36f
                for (q in barrageQueue.showBarrageRow) {
                    for (barrage in q) {
                        val x = barrage.getShowX(
                            barrageQueue.keepBarTime,
                            getCurrentTime(),
                            measuredWidth
                        )
                        canvas?.drawText(
                            barrage.text, x, y, textPaint
                        )
                        drawCount++
                    }
                    y += 48
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

}