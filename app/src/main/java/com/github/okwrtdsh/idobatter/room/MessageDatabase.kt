package com.github.okwrtdsh.idobatter.room

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.CoroutineScope

@Database(entities = arrayOf(Message::class), version = 1)
abstract class MessageRoomDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao

    private class MessageDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {

//        override fun onOpen(db: SupportSQLiteDatabase) {
//            super.onOpen(db)
//            INSTANCE?.let { database ->
//                scope.launch {
//                    var messageDao = database.messageDao()
//
//                    // Delete all content here.
////                     messageDao.deleteAll()
//
////                    var mesage = Message("Hello")
////                    messageDao.insert(mesage)
////                    mesage = Message("World!")
////                    messageDao.insert(mesage)
//                }
//            }
//        }
    }

    companion object {
        @Volatile
        private var INSTANCE: MessageRoomDatabase? = null

        fun getDatabase(
            context: Context,
            scope: CoroutineScope
        ): MessageRoomDatabase {
            // if the INSTANCE is not null, then return it,
            // if it is, then create the database
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MessageRoomDatabase::class.java,
                    "message_database"
                )
                    .addCallback(MessageDatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                // return instance
                instance
            }
        }
    }}