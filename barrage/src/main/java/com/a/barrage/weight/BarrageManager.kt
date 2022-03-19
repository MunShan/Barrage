package com.a.barrage.weight

import android.util.Log
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.min


class Barrage(
    var text: String,
    var count: Int,
    var barId: Long,
    var startShowTime: Long,
    var textWidth: Int,
    /**
     * [text] 相对于 [startShowTime] 在右边完整显示时间
     * ([textWidth] / view.width) * [BarrageQueue.keepBarTime] + [startShowTime]
     */
    var completeShowTime: Long
) {
    var lockX: Int? = null
    fun getShowX(keepTime: Int, currentTime: Long, viewWidth: Int): Float {
        val xPlace = (1f - (currentTime - startShowTime).toFloat() / keepTime.toFloat()) * viewWidth
        Log.d("-----", "getShowX: $xPlace")
        return xPlace
    }
}

private val barrageScope = CoroutineScope(Dispatchers.IO)

class BarrageQueue {
    var keepBarTime: Int = 0
    var rowCount: Int = 1

    /**
     * lastShowBar
     * [barSortList] is sortSet
     */
    private var lastShowBarrage: Barrage? = null
    private var barSortList = sortedSetOf(Comparator<Barrage> { p0, p1 ->
        if (p0.startShowTime == p1.startShowTime) {
            p0.barId.compareTo(p1.barId)
        } else {
            p1.startShowTime.compareTo(p0.startShowTime)
        }
    })
    var getShowTime: (() -> Long)? = null

    private var lastJob: Job? = null

    /**
     * --- --
     * --  -
     */
    var showBarrageRow = ConcurrentLinkedQueue<ConcurrentLinkedDeque<Barrage>>()
    private var textMapBar = hashMapOf<String, Barrage>()
    fun resetRow(rowCount: Int) {
        if (rowCount >= this.rowCount) {
            this.rowCount = rowCount
            return
        }
        this.rowCount = rowCount
        showBarrageRow.clear()
        showBarrageRow = ConcurrentLinkedQueue()
        textMapBar.clear()
        lastShowBarrage = null
        // dealBarrageStream 根据当前时间重新入队
        dealBarrageStream()
    }

    fun resetTime() {
        removeLeftBarrage()
        dealBarrageStream()
    }

    /**
     *  按一定间隔往 发起添加 barrage 事件
     *  如果时间被调整，则中断，reset
     */
    private fun dealBarrageStream() {
        lastJob?.cancel()
        lastJob = barrageScope.launch {
            var delayTime = 128L
            while (isActive) {
                removeLeftBarrage()
                val currentTime = getShowTime?.invoke()
                if (currentTime == null) {
                    // getShowTime 未初始化
                    delay(delayTime)
                    continue
                }
                if (lastShowBarrage == null) {
                    lastShowBarrage = barSortList.first {
                        it.startShowTime >= currentTime
                    }
                }
                val lastShowBar = lastShowBarrage
                if (lastShowBar == null) {
                    // currentTime 没有满足 barrage
                    delay(delayTime)
                    continue
                }
                val currentBarrage = barSortList.higher(lastShowBar)
                if (currentBarrage == null || currentBarrage.startShowTime < currentTime) {
                    // barrage 不满足
                    delay(delayTime)
                    continue
                }
                if (addBar2barRow(currentTime, currentBarrage)) {
                    textMapBar.putIfAbsent(currentBarrage.text, currentBarrage)
                    lastShowBarrage = currentBarrage
                }
                val nextBarrage = barSortList.higher(currentBarrage)
                if (nextBarrage == null) {
                    delay(delayTime)
                    continue
                }
                if (nextBarrage.startShowTime < currentTime) {
                    delayTime = min(128L, currentTime - nextBarrage.startShowTime)
                    delay(delayTime)
                }
            }
        }
    }

    private fun addBar2barRow(currentTime: Long, barrage: Barrage): Boolean {
        for (queue in showBarrageRow) {
            if (queue?.last?.completeShowTime ?: -1 <= currentTime) {
                queue.add(barrage)
                return true
            }
        }
        val shownBarrage = textMapBar[barrage.text]
        if (shownBarrage != null) {
            shownBarrage.count++
            return true
        }
        if (showBarrageRow.size < rowCount) {
            val newQ = ConcurrentLinkedDeque<Barrage>()
            if (showBarrageRow.add(newQ)) {
                newQ.offer(barrage)
            }
        }
        return false
    }

    /**
     * if [Barrage] on the [showBarrageRow] and left the screen, remove it
     */
    private fun removeLeftBarrage() {
        val currentTime = getShowTime?.invoke() ?: return
        val lastShowBar = lastShowBarrage ?: return
        if (currentTime < lastShowBar.startShowTime) {
            // 表示 显示时间被调整
            showBarrageRow.clear()
            textMapBar.clear()
            this.lastShowBarrage = null
            return
        }
        for (queue in showBarrageRow) {
            var f = queue?.first
            while (f != null) {
                if (f.startShowTime + keepBarTime <= currentTime) {
                    break
                }
                textMapBar.remove(f.text)
                queue.poll()
                f = queue.peek()
            }
        }
    }

}