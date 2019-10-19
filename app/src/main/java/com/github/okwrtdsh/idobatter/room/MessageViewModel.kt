package com.github.okwrtdsh.idobatter.room

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
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

    fun update(uuid: String) = repository.update(uuid)


    fun create(
        content: String,
        hops: Int = 0,
        isFab: Boolean = false,
        isAuther: Boolean = true,
        isUploade: Boolean = false,
        limitDist: Int = 0,
        limitHops: Int = 0,
        limitTime: Int = 0,
        current_lat: Double = 0.0,
        current_lng: Double = 0.0
    ) {

        val message = Message(
            UUID.randomUUID().toString(),
            Date().time,
            content,
            current_lat,
            current_lng,
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

//    fun enabled(
//        // current_lat: Double, current_lng: Double, limit: Int = 10
//    ) = repository.enabled(
//        // current_lat, current_lng, limit
//    )
    fun enabled(f: (List<Message>)-> Unit) = viewModelScope.launch {
        f(repository.enabled())
    }

}