package com.github.okwrtdsh.idobatter.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "message_table")
class Message(
    @PrimaryKey val uuid: String,
    val created: Long,
    val content: String,
    val lat: Double,
    val lng: Double,
    val hops: Int,
    @ColumnInfo(name = "is_fab") val isFab: Boolean,
    @ColumnInfo(name = "is_auther") val isAuther: Boolean,
    @ColumnInfo(name = "is_uploaded") val isUploaded: Boolean,
    @ColumnInfo(name = "limit_dist") val limitDist: Int,
    @ColumnInfo(name = "limit_time") val limitTime: Int,
    @ColumnInfo(name = "limit_hops") val limitHops: Int
)
