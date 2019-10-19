package com.github.okwrtdsh.idobatter

import com.google.gson.Gson

data class P2PData(
    val type: String,
    val body: String
)

fun P2PData.toGsonString()= Gson().toJson(this)

fun String.toP2PData() = Gson().fromJson(this, P2PData::class.java)