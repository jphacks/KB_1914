package com.github.okwrtdsh.idobatter

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
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
        val holder = MessageViewHolder(itemView)
        holder.itemView.setOnClickListener {
            val message = messages[holder.adapterPosition]
            val intent = Intent(it.context, MapsActivity::class.java)
            intent.putExtra("UUID", message.uuid)
            it.context.startActivity(intent)
        }
        return holder
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

    override fun getItemCount()= messages.size
}


class MainActivity : AppCompatActivity() {

    private val newMessageActivityRequestCode = 1
    private lateinit var messageViewModel: MessageViewModel
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var cm: ConnectivityManager
    private var isConnected = false
    private lateinit var mAWSAppSyncClient: AWSAppSyncClient
    private var isActive: Boolean = false
    private lateinit var mConnectionsClient: ConnectionsClient
    private var mRemoteEndpointId: String? = null
    private var mRemoteClientStatus: Int = STATUS_READY
    private var mMyClientStatus: Int = STATUS_READY


    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 1
        private const val TAG: String = "idobatter"
        private const val SERVICE_ID: String = "com.github.okwrtdsh.idobatter"
        private const val STATUS_DONE = 1
        private const val STATUS_IN_PROGRESS = 2
        private const val STATUS_READY = 3
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
        onOff.setOnCheckedChangeListener { _, isChecked ->
            isActive = isChecked
            if (isActive) {
                startAdvertising()
                startDiscovery()
            } else {
                stopAdvertising()
                stopDiscovery()
                mConnectionsClient.stopAllEndpoints()
                disconnectFromEndpoint()
            }
        }
        mConnectionsClient = Nearby.getConnectionsClient(this)

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
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        messageViewModel.create(
                            it.getStringExtra(NewMessageActivity.EXTRA_REPLY),
                            current_lat = location.latitude,
                            current_lng = location.longitude
                        )
                    }
                }
            }
        } else {
            Toast.makeText(
                applicationContext,
                R.string.empty_not_saved,
                Toast.LENGTH_LONG
            ).show()
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
            if (messageViewModel.uploadable().map { message ->
                    mutation.input(
                        CreateCoordinateInput.builder()
                            .uuid(message.uuid)
                            .lat(message.lat)
                            .lng(message.lng)
                            .time(message.created.toInt())
                            .build()
                    )
                    message.isUploaded = true
                    messageViewModel.update(message.uuid)
                }.isNotEmpty()) runMutate(mutation.build())
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


    override fun onStop() {
        super.onStop()
        mConnectionsClient.stopAdvertising()
        mConnectionsClient.stopDiscovery()
        mConnectionsClient.stopAllEndpoints()
    }

    private fun startAdvertising() {
        mConnectionsClient.startAdvertising(
            getNickName(),
            SERVICE_ID,
            mConnectionLifecycleCallback,
            AdvertisingOptions(Strategy.P2P_CLUSTER)
        )
            .addOnSuccessListener {
                debug("Success startAdvertising: $it")
            }
            .addOnFailureListener {
                debug("Failure startDiscovery: $it")
            }
    }

    private fun stopAdvertising() {
        mConnectionsClient.stopAdvertising()
        debug("Success stopAdvertising")
    }

    private fun startDiscovery() {
        mConnectionsClient.startDiscovery(
            packageName,
            mEndpointDiscoveryCallback,
            DiscoveryOptions(Strategy.P2P_CLUSTER)
        )
            .addOnSuccessListener {
                debug("Success startDiscovery: $it")
            }
            .addOnFailureListener {
                debug("Failure startDiscovery: $it")
            }
    }

    private fun stopDiscovery() {
        mConnectionsClient.stopDiscovery()
        debug("Success stopDiscovery")
    }

    private fun sendString(content: String) =
        mConnectionsClient.sendPayload(
            mRemoteEndpointId!!,
            Payload.fromBytes(content.toByteArray(Charsets.UTF_8))
        )

    private fun disconnectFromEndpoint() {
        if (mRemoteEndpointId != null) {
            mConnectionsClient.disconnectFromEndpoint(mRemoteEndpointId!!)
            mRemoteEndpointId = null
            mRemoteClientStatus = STATUS_READY
            mMyClientStatus = STATUS_READY
        }
    }

    private val mEndpointDiscoveryCallback = object : EndpointDiscoveryCallback() {

        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // An endpoint was found. We request a connection to it.
            debug("mEndpointDiscoveryCallback.onEndpointFound $endpointId:$info:${info.endpointName}")

            mConnectionsClient.requestConnection(
                getNickName(),
                endpointId,
                mConnectionLifecycleCallback
            )
                .addOnSuccessListener {
                    // We successfully requested a connection. Now both sides
                    // must accept before the connection is established.
                    debug("Success requestConnection: $it")
                }
                .addOnFailureListener {
                    // Nearby Connections failed to request the connection.
                    // TODO: retry
                    debug("Failure requestConnection: $it")
                }
        }

        override fun onEndpointLost(endpointId: String) {
            // A previously discovered endpoint has gone away.
            debug("mEndpointDiscoveryCallback.onEndpointLost $endpointId")
        }
    }

    private val mConnectionLifecycleCallback = object : ConnectionLifecycleCallback() {

        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            debug("mConnectionLifecycleCallback.onConnectionInitiated $endpointId:${connectionInfo.endpointName}")

            // Automatically accept the connection on both sides.
            mConnectionsClient.acceptConnection(endpointId, mPayloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            debug("mConnectionLifecycleCallback.onConnectionResult $endpointId")

            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    debug("onnectionsStatusCodes.STATUS_OK $endpointId")
                    // We're connected! Can now start sending and receiving data.
                    mRemoteEndpointId = endpointId

                    mMyClientStatus = STATUS_IN_PROGRESS
                    mRemoteClientStatus = STATUS_IN_PROGRESS

                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        if (location != null) {
                            Log.d(
                                "onConnectionResult.addOnSuccessListener",
                                "${location.latitude}, ${location.longitude}"
                            )
                            messageViewModel.enabled { messages ->
                                sendString(
                                    P2PData(
                                        "MSG",
                                        messages.toGsonString()
                                    ).toGsonString()
                                )
                                sendString(P2PData("FIN", "").toGsonString())
                                mMyClientStatus = STATUS_DONE
                            }

                        } else {
                            Log.d("onConnectionResult.addOnSuccessListener", "no GPS")
                        }
                    }
                        .addOnFailureListener {
                            Log.d("onConnectionResult.addOnFailureListener", it.toString())
                        }
                        .addOnCanceledListener {
                            Log.d("onConnectionResult.addOnCanceledListener", "Canceled")
                        }
                }

                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    // The connection was rejected by one or both sides.
                    debug("onnectionsStatusCodes.STATUS_CONNECTION_REJECTED $endpointId")
                }

                ConnectionsStatusCodes.STATUS_ERROR -> {
                    // The connection broke before it was able to be accepted.
                    debug("onnectionsStatusCodes.STATUS_ERROR $endpointId")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            // We've been disconnected from this endpoint. No more data can be
            // sent or received.
            debug("mConnectionLifecycleCallback.onDisconnected $endpointId")
            disconnectFromEndpoint()
        }

    }


    private val mPayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            debug("mPayloadCallback.onPayloadReceived $endpointId")

            when (payload.type) {
                Payload.Type.BYTES -> {
                    val data = payload.asBytes()!!
                    debug("Payload.Type.BYTES: ${data.toString(Charsets.UTF_8)}")

                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        if (location != null) {
                            communicationHandler(
                                data.toString(Charsets.UTF_8).toP2PData(),
                                location.latitude, location.longitude
                            )

                        } else {
                            Log.d("onConnectionResult.addOnSuccessListener", "no GPS")
                        }
                    }
                        .addOnFailureListener {
                            Log.d("onConnectionResult.addOnFailureListener", it.toString())
                        }
                        .addOnCanceledListener {
                            Log.d("onConnectionResult.addOnCanceledListener", "Canceled")
                        }

                }
                Payload.Type.FILE -> {
                    val file = payload.asFile()!!
                    debug("Payload.Type.FILE: size: ${file.size}")
                    // TODO:

                }
                Payload.Type.STREAM -> {
                    val stream = payload.asStream()!!
                    debug("Payload.Type.STREAM")
                    // TODO:
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // debug("mPayloadCallback.onPayloadTransferUpdate $endpointId")
        }
    }

    private fun communicationHandler(data: P2PData, current_lat: Double?, current_lng: Double?) {
        when (data.type) {
            "MSG" -> {
                data.body.toMessagesGson().messages.map {
                    messageViewModel.create(
                        it.content,
                        it.hops + 1,
                        false,
                        false,
                        false,
                        it.limitDist,
                        it.limitHops,
                        it.limitTime,
                        current_lat!!,
                        current_lng!!
                    )
                }
            }
            "FIN" -> {
                mRemoteClientStatus = STATUS_DONE
                if (mMyClientStatus == STATUS_DONE) {
                    sendString(P2PData("ACK", "").toGsonString())
                    disconnectFromEndpoint()
                }
            }
            "ACK" -> {
                disconnectFromEndpoint()
            }
        }
    }


    private fun debug(message: String) {
        Log.d(TAG, message)
    }

    private fun getNickName() = UUID.randomUUID().toString()
}