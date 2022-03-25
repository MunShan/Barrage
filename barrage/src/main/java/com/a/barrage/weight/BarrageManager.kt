package com.a.barrage.weight

import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentLinkedQueue


class Barrage(
    var text: String,
    var barId: Long = -1,
    var startShowTime: Int
) {
    var textWidth: Float = 0f

    var setCount: HashSet<Long>? = null
        get() {
            if (field != null) {
                return field
            }
            val set = hashSetOf<Long>()
            field = set
            return set
        }
    var lastX = 0
    var xOffset = 1
}

private val barrageScope = CoroutineScope(Dispatchers.IO)

class BarrageQueue {
    var keepBarTime: Int = 4 * 1000
    var rowCount: Int = 1

    /**
     * lastShowBar
     * [barSortList] is sortSet
     */
    @Volatile
    private var lastShowBarrage: Barrage? = null

    /**
     * 所有barrage
     */
    private var barSortList = sortedSetOf(Comparator<Barrage> { p0, p1 ->
        if (p0.startShowTime == p1.startShowTime) {
            p0.barId.compareTo(p1.barId)
        } else {
            p0.startShowTime.compareTo(p1.startShowTime)
        }
    })
    var getShowTime: (() -> Long)? = null
    var getMeasuredWidth: (() -> Int) = { 0 }

    private var lastJob: Job? = null

    /**
     * 当前在展示的弹幕
     */
    var showBarrageRow = ConcurrentLinkedQueue<ConcurrentLinkedDeque<Barrage>>()

    /**
     * 重置行数
     */
    fun resetRow(rowCount: Int) {
        if (rowCount >= this.rowCount) {
            this.rowCount = rowCount
            return
        }
        this.rowCount = rowCount
        resetData()
        // dealBarrageStream 根据当前时间重新入队
        dealBarrageStream()
    }

    private fun resetData() {
        showBarrageRow.clear()
        showBarrageRow = ConcurrentLinkedQueue()
        lastShowBarrage = null
        Log.d("----------", "resetData: reset data lastShowBarrage")
    }

    fun addBarrageData(data: List<Barrage>) {
        barSortList.addAll(data)
        // todo varSortList.headSet
        Log.d("------------", "setBarrageData: barSortList ${barSortList.size}")
        dealBarrageStream()
    }

    fun setBarrageData(data: List<Barrage>) {
        resetData()
        barSortList.clear()
        barSortList.addAll(data)
        Log.d("------------", "setBarrageData: data ${data.size}")
        Log.d("------------", "setBarrageData: barSortList ${barSortList.size}")
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
            val delayError = 4L
            val findB = Barrage(
                text = "",
                startShowTime = 0,
                barId = 0
            )
            var errorCount = 0
            while (isActive) {
                removeLeftBarrage()
                val currentTime = getShowTime?.invoke()
                if (currentTime == null) {
                    // getShowTime 未初始化
                    delay(delayTime)
                    continue
                }
                val isHadLast = lastShowBarrage != null
                if (lastShowBarrage == null) {
                    findB.startShowTime = (currentTime - keepBarTime).toInt().coerceAtLeast(0)
                    lastShowBarrage = barSortList.higher(findB)
                }
                val lastShowBar = lastShowBarrage
                if (lastShowBar == null) {
                    // currentTime 没有满足 barrage
                    delay(delayTime)
                    continue
                }
                var currentBarrage = if (isHadLast) barSortList.higher(lastShowBar) else lastShowBar
                if (currentBarrage != null && errorCount > 2) {
                    errorCount = 0
                    lastShowBarrage = currentBarrage
                    currentBarrage = barSortList.higher(currentBarrage)
                }
                if (currentBarrage == null || currentBarrage.startShowTime > currentTime) {
                    // barrage 不满足
                    delay(delayTime)
                    continue
                }
                if (addBar2barRow(currentBarrage)) {
                    lastShowBarrage = currentBarrage
                } else {
                    errorCount++
                    delay(delayError)
                    continue
                }
                val nextBarrage = barSortList.higher(currentBarrage)
                if (nextBarrage == null) {
                    delay(delayTime)
                    continue
                }
                if (nextBarrage.startShowTime > currentTime) {
                    delayTime = nextBarrage.startShowTime - currentTime
                    delay(delayTime)
                }
            }
        }
    }

    private fun addBar2barRow(barrage: Barrage): Boolean {
        for (queue in showBarrageRow) {
            val lastT = queue?.lastOrNull()
            if (lastT == null ||
                lastT.lastX + lastT.textWidth + 100 + kotlin.math.abs(
                    barrage.xOffset - lastT.xOffset
                ) * 130 < getMeasuredWidth()
            ) {
                queue.addLast(barrage)
                return true
            }
        }
        if (showBarrageRow.size <= rowCount) {
            val newQ = ConcurrentLinkedDeque<Barrage>()
            if (showBarrageRow.add(newQ)) {
                newQ.addLast(barrage)
                return true
            }
        }
        return false
    }

    /**
     * if [Barrage] on the [showBarrageRow] and left the screen, remove it
     */
    private fun removeLeftBarrage() {
        // (lastShowBar.startShowTime) currentTime
        for (queue in showBarrageRow) {
            var f = queue?.firstOrNull()
            while (f != null) {
                if (f.lastX + f.textWidth > 0) {
                    // (currentTime - keepBarTime) (f.startShowTime) (currentTime)
                    break
                }
                queue.poll()
                f = queue.peek()
            }
        }
    }

}