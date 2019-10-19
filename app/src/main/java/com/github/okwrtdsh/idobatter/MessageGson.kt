package com.github.okwrtdsh.idobatter

import com.github.okwrtdsh.idobatter.room.Message
import com.google.gson.Gson

data class MessageGson(
    val uuid: String,
    val created: Long,
    val content: String,
    val lat: Double,
    val lng: Double,
    val hops: Int,
    val limitDist: Int,
    val limitTime: Int,
    val limitHops: Int
)

data class MessagesGson(
    val messages: List<MessageGson>
)

fun Message.toGson() = MessageGson(
    this.uuid,
    this.created,
    this.content,
    this.lat,
    this.lng,
    this.hops,
    this.limitDist,
    this.limitTime,
    this.limitHops
)

fun List<Message>.toGson() = MessagesGson(this.map{ it.toGson()})

fun Message.toGsonString()= Gson().toJson(this.toGson())
fun List<Message>.toGsonString()= Gson().toJson(this.toGson())

fun String.toMessageGson()= Gson().fromJson(this, MessageGson::class.java)
fun String.toMessagesGson() = Gson().fromJson(this, MessagesGson::class.java)