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

    var setCount : HashSet<Long>? = null
        get() {
            if (field != null) {
                return field
            }
            val set = hashSetOf<Long>()
            field = set
            return set
        }

    /**
     * [text] 相对于 [startShowTime] 在右边完整显示时间
     * ([textWidth] / view.width) * [BarrageQueue.keepBarTime] + [startShowTime]
     */
    var completeShowTime: Int = startShowTime
    fun getShowX(keepTime: Int, currentTime: Long, viewWidth: Int): Float {
        if (currentTime < startShowTime) {
            return viewWidth.toFloat()
        }
        val showTime = currentTime - startShowTime
        val rate = showTime.toFloat() / keepTime.toFloat()
        val xPlace = (1f - rate) * viewWidth
        return xPlace
    }
}

private val barrageScope = CoroutineScope(Dispatchers.IO)

class BarrageQueue {
    var keepBarTime: Int = 4 * 1000
    var intervals: Int = 32
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

    private var lastJob: Job? = null

    /**
     * 当前在展示的弹幕
     */
    var showBarrageRow = ConcurrentLinkedQueue<ConcurrentLinkedDeque<Barrage>>()
    private var textMapBar = hashMapOf<String, Barrage>()

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
        textMapBar.clear()
        lastShowBarrage = null
        Log.d("----------", "resetData: reset data lastShowBarrage")
    }

    fun addBarrageData(data: List<Barrage>) {
        barSortList.addAll(data)
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
            val findB = Barrage(
                text = "",
                startShowTime = 0,
                barId = 0
            )
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
                val currentBarrage = if (isHadLast) barSortList.higher(lastShowBar) else lastShowBar
                if (currentBarrage == null || currentBarrage.startShowTime > currentTime) {
                    // barrage 不满足
                    delay(delayTime)
                    continue
                }
                if (addBar2barRow(currentTime, currentBarrage)) {
                    textMapBar.putIfAbsent(currentBarrage.text, currentBarrage)
                    lastShowBarrage = currentBarrage
                } else {
                    delay(delayTime)
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

    private fun addBar2barRow(currentTime: Long, barrage: Barrage): Boolean {
        for (queue in showBarrageRow) {
            val lastT = queue?.lastOrNull()
            if (lastT == null || barrage.startShowTime >= lastT.completeShowTime) {
                Log.d("------------", "addBar2barRow:1 ${barrage.text}")
                queue.addLast(barrage)
                return true
            }
//            val isInCan = lastT.startShowTime<= barrage.startShowTime && barrage.startShowTime <= lastT.completeShowTime &&
            val gap = barrage.completeShowTime - barrage.startShowTime
            val isMoveCan = gap * 0.3 < currentTime - lastT.completeShowTime
            if (isMoveCan) {
                Log.d("------------", "addBar2barRow:1 ${barrage.text}")
                barrage.startShowTime = lastT.completeShowTime + intervals
                barrage.completeShowTime = barrage.startShowTime + gap
                queue.addLast(barrage)
                return true
            }
        }
        val shownBarrage = textMapBar[barrage.text]
        if (shownBarrage != null) {
            Log.d("------------", "addBar2barRow: textMapBar ${barrage.text}")
            shownBarrage.setCount?.add(barrage.barId)
            return true
        }
        if (showBarrageRow.size <= rowCount) {
            val newQ = ConcurrentLinkedDeque<Barrage>()
            if (showBarrageRow.add(newQ)) {
                Log.d("------------", "addBar2barRow: newQ ${barrage.text}")
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
        val currentTime = getShowTime?.invoke() ?: return
        // todo min(queue.fist)
        val lastShowBar = lastShowBarrage ?: return
        if (currentTime < lastShowBar.startShowTime) {

            /*
                表示 显示时间被调整
                currentTime lastShowBar.startShowTime
             */
            showBarrageRow.clear()
            textMapBar.clear()
            this.lastShowBarrage = null
            Log.d("----------", "removeLeftBarrage: reset time lastShowBarrage")
            return
        }
        // (lastShowBar.startShowTime) currentTime
        for (queue in showBarrageRow) {
            var f = queue?.firstOrNull()
            while (f != null) {
                if (currentTime - keepBarTime <= f.completeShowTime && f.startShowTime <= currentTime) {
                    // (currentTime - keepBarTime) (f.startShowTime) (currentTime)
                    break
                }
                textMapBar.remove(f.text)
                Log.d("----------", "removeLeftBarrage: poll ${f.text}")
                queue.poll()
                f = queue.peek()
            }
        }
    }

}