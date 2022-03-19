package com.a.barrage.weight

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlinx.coroutines.*

class BarrageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {
    private val scope = MainScope()
    private val barrageQueue = BarrageQueue().apply {
        rowCount = 3
        getShowTime = this@BarrageView::getCurrentTime
    }
    private val paint = Paint().apply {
        color = Color.BLUE
        textSize = 14f
    }
    private val mSurfaceHolder: SurfaceHolder by lazy {
        holder.apply {
            addCallback(this@BarrageView)
        }
    }
    var job : Job? = null
    init {
        isFocusable = true
        keepScreenOn = true
        isFocusableInTouchMode = true
    }

    // todo test
    private fun getCurrentTime() = System.currentTimeMillis()


    private suspend fun action(isActive : ()->Boolean) {
        while (isActive()) {
            var canvas: Canvas? = null
            try {
                canvas = mSurfaceHolder.lockCanvas()
                var y = 0f
                for (q in barrageQueue.showBarrageRow) {
                    for (barrage in q) {
                        canvas?.drawText(
                            barrage.text, barrage.getShowX(
                                barrageQueue.keepBarTime,
                                getCurrentTime(),
                                width
                            ), y, paint
                        )
                    }
                    y += 100
                }
            } finally {
                if (canvas != null) {
                    mSurfaceHolder.unlockCanvasAndPost(canvas)
                }
            }
            delay(32)
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
    }

}