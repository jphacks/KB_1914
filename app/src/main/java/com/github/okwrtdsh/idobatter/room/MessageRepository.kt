package com.github.okwrtdsh.idobatter.room

import androidx.lifecycle.LiveData

class MessageRepository(private val messageDao: MessageDao) {

    val getAll: LiveData<List<Message>> = messageDao.getAll()

    suspend fun insert(word: Message) {
        messageDao.insert(word)
    }
}