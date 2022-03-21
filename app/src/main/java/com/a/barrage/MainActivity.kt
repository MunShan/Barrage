package com.a.barrage

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.a.barrage.weight.Barrage
import com.a.barrage.weight.BarrageQueue
import com.a.barrage.weight.BarrageView

class MainActivity : AppCompatActivity() {
    private val testT = 60 * 60 * 1000L
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()
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