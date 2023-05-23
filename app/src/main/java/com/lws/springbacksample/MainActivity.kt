package com.lws.springbacksample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val list: MutableList<Model> = ArrayList()
        for (i in 0..14) {
            list.add(Model())
        }
        setContentView(R.layout.activity_main)
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        val adapter = MainAdapter()
        recyclerView.adapter = adapter
        adapter.setData(list)
    }
}