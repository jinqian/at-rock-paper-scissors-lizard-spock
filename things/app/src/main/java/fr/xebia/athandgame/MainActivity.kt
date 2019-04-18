package fr.xebia.athandgame

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        debugMenu.setOnClickListener {
            startActivity(Intent(this, DebugActivity::class.java))
        }

        threeMode.setOnClickListener {
            val intent = Intent(this, ImageClassifierActivity::class.java)
            intent.putExtra(ImageClassifierActivity.EXTRA_FULL_MODE, false)
            startActivity(intent)
        }

        fiveMode.setOnClickListener {
            val intent = Intent(this, ImageClassifierActivity::class.java)
            intent.putExtra(ImageClassifierActivity.EXTRA_FULL_MODE, true)
            startActivity(intent)
        }
    }
}
