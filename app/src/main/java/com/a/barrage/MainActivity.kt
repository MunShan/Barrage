package com.a.barrage

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.a.barrage.weight.Barrage
import com.a.barrage.weight.BarrageQueue
import com.a.barrage.weight.BarrageView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : AppCompatActivity() {
    private val testT = 60 * 60 * 1000L
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()
        testStream()
    }

    private fun testStream() {
        val bd = findViewById<BarrageView>(R.id.bv)
        val testTime = System.currentTimeMillis()
        bd.getShowTime = {
            (System.currentTimeMillis() - testTime).coerceAtMost(testT)
        }
        lifecycleScope.launchWhenResumed {
            var barId = 1L
            var sTime = 0
            while (isActive) {
                val textMap = hashMapOf<String, Barrage>()
                bd.setBarrageData(mutableListOf<Barrage>().apply {
                    val range = 100..300
                    for (i in 1..100) {
                        val t = range.random()
                        add(
                            Barrage(
                                text = "测试$t",
                                startShowTime = sTime,
                                barId = barId++
                            )
                        )
                        sTime += t
                    }
                    for (j in 1..16) {
                        add(
                            Barrage(
                                text = "哈哈哈",
                                startShowTime = sTime,
                                barId = barId++
                            )
                        )
                        sTime += j
                    }
                }.mapNotNull {
                    val same = textMap[it.text]
                    if (same == null) {
                        it.setCount?.add(it.barId)
                        textMap[it.text] = it
                        return@mapNotNull it
                    }
                    same.setCount?.add(it.barId)
                    return@mapNotNull null
                }, false)
                delay((1..1000L).random())
            }
        }
    }

    private fun testVideo() {
        val bd = findViewById<BarrageView>(R.id.bv)
        val testTime = System.currentTimeMillis()
        bd.getShowTime = {
            (System.currentTimeMillis() - testTime).coerceAtMost(testT)
        }
        bd.setBarrageData(mutableListOf<Barrage>().apply {
            val range = 1..100000
            for (i in 1..1000) {
                val t = range.random()
                add(
                    Barrage(
                        text = "测试$t",
                        startShowTime = t,
                        barId = i.toLong()
                    )
                )
            }
        })
    }
}