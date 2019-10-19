package com.github.okwrtdsh.idobatter

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.okwrtdsh.idobatter.room.Message
import com.github.okwrtdsh.idobatter.room.MessageViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.*

class MessageListAdapter internal constructor(
    context: Context
) : RecyclerView.Adapter<MessageListAdapter.MessageViewHolder>() {

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private var messages = emptyList<Message>() // Cached copy of messages

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageItemView: TextView = itemView.findViewById(R.id.textView)
        val messageItemViewDate: TextView = itemView.findViewById(R.id.textViewDate)

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val itemView = inflater.inflate(R.layout.recyclerview_item, parent, false)
        return MessageViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val current = messages[position]
        holder.messageItemView.text = current.content
        holder.messageItemViewDate.text = SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
            .format(Date(current.created))
    }

    internal fun setMessages(messages: List<Message>) {
        this.messages = messages
        notifyDataSetChanged()
    }

    override fun getItemCount() = messages.size
}


class MainActivity : AppCompatActivity() {

    private val newMessageActivityRequestCode = 1
    private lateinit var messageViewModel: MessageViewModel
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 1
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val adapter = MessageListAdapter(this)
        recyclerview.adapter = adapter
        recyclerview.layoutManager = LinearLayoutManager(this)

        messageViewModel = ViewModelProvider(this).get(MessageViewModel::class.java)
        messageViewModel.allMessages.observe(this, Observer { item ->
            item?.let { adapter.setMessages(it) }
        })
        fab.setOnClickListener {
            val intent = Intent(this@MainActivity, NewMessageActivity::class.java)
            startActivityForResult(intent, newMessageActivityRequestCode)
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
            REQUEST_CODE_PERMISSIONS
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == newMessageActivityRequestCode && resultCode == Activity.RESULT_OK) {
            data?.let {
                messageViewModel.create(
                    it.getStringExtra(NewMessageActivity.EXTRA_REPLY),
                    fusedLocationClient=fusedLocationClient
                )
            }
        }
        else {
            Toast.makeText(
                applicationContext,
                R.string.empty_not_saved,
                Toast.LENGTH_LONG).show()
        }
    }
}
