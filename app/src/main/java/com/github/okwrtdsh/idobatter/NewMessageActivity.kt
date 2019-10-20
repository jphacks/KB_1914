package com.github.okwrtdsh.idobatter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_new_message.*

class NewMessageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_message)


        button_save.setOnClickListener {
            val replyIntent = Intent()
            if (TextUtils.isEmpty(content.text)) {
                setResult(Activity.RESULT_CANCELED, replyIntent)
            }
            else {
                replyIntent.putExtra(EXTRA_REPLY, content.text.toString())
                setResult(Activity.RESULT_OK, replyIntent)
            }
            finish()
        }
    }

    companion object {
        const val EXTRA_REPLY = "com.github.okwrtdsh.idobatter.messagelistsql.REPLY"
    }
}