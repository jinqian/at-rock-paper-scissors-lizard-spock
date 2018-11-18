package fr.xebia.athandgame

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        startButton.setOnClickListener {
            startActivity(Intent(this, ImageClassifierActivity::class.java))
        }
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}
