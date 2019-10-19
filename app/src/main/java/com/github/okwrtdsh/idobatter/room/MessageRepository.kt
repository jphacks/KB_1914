package com.github.okwrtdsh.idobatter.room

import androidx.lifecycle.LiveData

class MessageRepository(private val messageDao: MessageDao) {

    val getAll: LiveData<List<Message>> = messageDao.getAll()

    suspend fun insert(message: Message) {
        messageDao.insert(message)
    }

    fun update(uuid: String) {
        messageDao.update(uuid)
    }

    fun uploadable() = messageDao.uploadable()

    suspend fun enabled(
        // current_lat: Double, current_lng: Double, limit: Int = 10
    ) = messageDao.enabled(
        // Date().time, current_lat, current_lng, limit
    )
}