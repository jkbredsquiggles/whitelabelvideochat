package com.redsquiggles.virtualvenue.videochat.dao

import com.redsquiggles.virtualvenue.videochat.model.User
import com.redsquiggles.virtualvenue.videochat.model.UserPaginationKey

interface Dao {
    fun createUser(user: User): User
    fun getUser(id: String): User?
    fun getUsers(lastKey: UserPaginationKey?): Pair<UserPaginationKey?, List<User>>
    fun upsertUser(user: User): User?
    fun deleteUser(id: String): User?
}