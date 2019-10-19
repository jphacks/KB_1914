package com.github.okwrtdsh.idobatter.room

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.launch
import java.util.*


class MessageViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MessageRepository
    val allMessages: LiveData<List<Message>>

    init {
        val messageDao = MessageRoomDatabase.getDatabase(application, viewModelScope).messageDao()

        repository = MessageRepository(messageDao)
        allMessages = repository.getAll
    }

    fun insert(message: Message) = viewModelScope.launch {
        repository.insert(message)
    }

    fun uploadable() = repository.uploadable()

    fun update(uuid: String)= repository.update(uuid)


    fun create(
        content: String,
        hops: Int = 0,
        isFab: Boolean = false,
        isAuther: Boolean = true,
        isUploade: Boolean = false,
        limitDist: Int = 0,
        limitHops: Int = 0,
        limitTime: Int = 0,
        fusedLocationClient: FusedLocationProviderClient
    ) {
        Log.d("create", "call")
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    Log.d("create.addOnSuccessListener",
                        "${location.latitude}, ${location.longitude}")
                    val message = Message(
                        UUID.randomUUID().toString(),
                        Date().time,
                        content,
                        location.latitude,
                        location.longitude,
                        hops,
                        isFab,
                        isAuther,
                        isUploade,
                        limitDist,
                        limitHops,
                        limitTime
                    )
                    viewModelScope.launch {
                        repository.insert(message)
                    }
                }
                else {
                    Log.d("create.addOnSuccessListener", "no GPS")
                }
            }
            .addOnFailureListener {
                Log.d("create.addOnFailureListener", it.toString())
            }
            .addOnCanceledListener {
                Log.d("create.addOnCanceledListener", "Canceled")
            }

    }
}