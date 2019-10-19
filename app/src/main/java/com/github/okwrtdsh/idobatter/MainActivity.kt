package com.github.okwrtdsh.idobatter

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
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
import com.amazonaws.amplify.generated.graphql.CreateCoordinateMutation
import com.amazonaws.mobile.config.AWSConfiguration
import com.amazonaws.mobileconnectors.appsync.AWSAppSyncClient
import com.apollographql.apollo.GraphQLCall
import com.apollographql.apollo.api.Response
import com.apollographql.apollo.exception.ApolloException
import com.github.okwrtdsh.idobatter.room.Message
import com.github.okwrtdsh.idobatter.room.MessageViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.android.synthetic.main.activity_main.*
import type.CreateCoordinateInput
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
    private lateinit var cm: ConnectivityManager
    private var isConnected = false
    private lateinit var mAWSAppSyncClient: AWSAppSyncClient


    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 1
//        private const val TAG: String = "idobatter"
//        private const val channelId = "com.github.okwrtdsh.idobatter"
//        private const val channelDescription = "Description"
//        private const val channelName = "idobatter"
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

        // amplify
        mAWSAppSyncClient = AWSAppSyncClient.builder()
            .context(this)
            .awsConfiguration(AWSConfiguration(this))
            .build()

        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_NETWORK_STATE
            ),
            REQUEST_CODE_PERMISSIONS
        )

        // watch net state
        cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        NetworkRequest
            .Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
            .run {
                cm.registerNetworkCallback(this, mNetworkCallback)
            }
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

    private val mNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network?) {
            updateConnectionStatus()
            uploadCoordinateData()
        }
        override fun onLost(network: Network?) {
            updateConnectionStatus()
            uploadCoordinateData()
        }
    }

    private fun updateConnectionStatus() {
        val activeNetworks = cm.allNetworks.mapNotNull {
            cm.getNetworkCapabilities(it)
        }.filter {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
        isConnected = activeNetworks.isNotEmpty()
    }

    private fun uploadCoordinateData() {
        if (isConnected) {
            val mutation = CreateCoordinateMutation.builder()
            messageViewModel.uploadable().map { message ->
                Log.d("#########", message.content)
                mutation.input(
                    CreateCoordinateInput.builder()
                        .uuid(message.uuid)
                        .lat(message.lat)
                        .lng(message.lng)
                        .time(message.created.toInt())
                        .build()
                )
                Log.d("#########2", message.content)
                message.isUploaded = true
                messageViewModel.update(message.uuid)
            }
            runMutate(mutation.build())
        }

    }
    private fun runMutate(mutation: CreateCoordinateMutation) {
        mAWSAppSyncClient.mutate(mutation).enqueue(mutationCallback)
    }

    private val mutationCallback = object : GraphQLCall.Callback<CreateCoordinateMutation.Data>() {
        override fun onResponse(response: Response<CreateCoordinateMutation.Data>) {
            Log.d("Results", "Added")
        }

        override fun onFailure(e: ApolloException) {
            Log.e("Error", e.toString())
        }
    }
}
