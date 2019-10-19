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
}