package com.redsquiggles.virtualvenue.videochat.dao

/*
 * Copyright (c) 2020. Red Squiggles Software Inc. or its affiliates. All rights reversed.
 *
 *  This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR  CONDITIONS OF ANY KIND, either express or implied.
 *
 */


/*
 * An implementation of the chat DAO that uses the AWS Dynnamo DB
 */


import com.redsquiggles.squiggleprise.logging.EventLevel
import com.redsquiggles.squiggleprise.logging.InstrumentedEventType
import com.redsquiggles.squiggleprise.logging.LogEvent
import com.redsquiggles.squiggleprise.logging.Logger
import com.redsquiggles.utilities.paginate
import com.redsquiggles.virtualvenue.dynamodb.buildDynamoDbClient
import com.redsquiggles.virtualvenue.videochat.model.User
import com.redsquiggles.virtualvenue.videochat.model.UserPaginationKey
import software.amazon.awssdk.services.dynamodb.model.*
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.HashMap

fun String?.toAttributeValue(): AttributeValue {
    return if (this == null)
        AttributeValue.builder().nul(true).build()
    else
        AttributeValue.builder().s(this).build()
}

fun Set<String>.toAttributeValue() : AttributeValue {
    return AttributeValue.builder().ss(this).build()
}

fun Instant.toAttributeValue(): AttributeValue {
    return this.epochSecond.toString().toAttributeValue()
}

fun Boolean.asDynamoDBString() : String {
    return if (this)  "true" else "false"
}

fun String.toBoolean() : Boolean {
    return this.toLowerCase() == "true"
}

fun String.toInstant(): Instant {
    return Instant.ofEpochSecond(this.toLong())
}



fun Map<String,AttributeValue>.toUser() : User {

    return User(this[DynamoDbDao.USER_ID_FIELD]!!.s(),
        this[DynamoDbDao.USER_NAME_FIELD]!!.s(),
        this[DynamoDbDao.USER_CREATED_FIELD]!!.s().toInstant(),
        this[DynamoDbDao.USER_LAST_CONNECTED_FIELD]!!.s().toInstant(),
        this[DynamoDbDao.USER_CONNECTION_ID_FIELD]?.s() // can be null
      //  this[DynamoDbDao.USER_LOCATIONS_FIELD]!!.ss().toSet()
    )
}

fun User.toAttributeValues(): Map<String,AttributeValue> {
    return mapOf(
        DynamoDbDao.USER_ID_FIELD to this.id.toAttributeValue(),
        DynamoDbDao.USER_NAME_FIELD to this.name.toAttributeValue(),
        DynamoDbDao.USER_CREATED_FIELD to this.created.toAttributeValue(),
        DynamoDbDao.USER_LAST_CONNECTED_FIELD to this.lastConnected.toAttributeValue(),
        DynamoDbDao.USER_CONNECTION_ID_FIELD to this.connectionId.toAttributeValue()
       // DynamoDbDao.USER_LOCATIONS_FIELD to this.locations.toAttributeValue()
    )
}

fun Map<String,AttributeValue>.toUserPaginationKey() : UserPaginationKey {
    return UserPaginationKey(this[DynamoDbDao.USER_ID_FIELD]!!.s())
}
fun UserPaginationKey.toAttributeValue():  Map<String,AttributeValue> {
    return mapOf(DynamoDbDao.USER_ID_FIELD to this.id.toAttributeValue())
}


class DynamoDbDao(private val dbConfig: DynamoDbConfig, val logger: Logger) : Dao {

    companion object {
        const val USER_ID_FIELD = "id"
        const val USER_NAME_FIELD = "name"
        //        const val USER_ACTIVE_FIELD = "active"
        const val USER_CREATED_FIELD = "created"
        const val USER_LAST_CONNECTED_FIELD = "lastConnected"
        const val USER_CONNECTION_ID_FIELD = "connectionId"
        //const val USER_LOCATIONS_FIELD = "locations"


    }

    override fun createUser(user: User): User {

        val client = dbConfig.userTable.buildDynamoDbClient()
        val itemValues = user.toAttributeValues()

        logger.log(LogEvent(EventLevel.Info, mapOf("user" to user, "attributes" to itemValues),InstrumentedEventType.Event,
        "create user","CreateUser","mapOfUserAndAttributes"))

        val request = PutItemRequest.builder()
            .tableName(dbConfig.userTable.tableName)
            .item(itemValues)
            .conditionExpression("attribute_not_exists(${USER_ID_FIELD})")
            .build()


        try {
            client.putItem(request)
            return user // putItem returns nothing; have to return input if we don't want to change the interface
        } catch (e : ConditionalCheckFailedException) {
            throw UserExistsException()
        }
    }

    override fun getUser(id: String): User? {
        val client = dbConfig.userTable.buildDynamoDbClient()

        val keyToGet = HashMap<String, AttributeValue>()
        keyToGet[USER_ID_FIELD] = AttributeValue.builder()
            .s(id).build()

        val request = GetItemRequest.builder()
            .key(keyToGet)
            .tableName(dbConfig.userTable.tableName) // I wonder why we need to specify the table name a second time here? `client` already knows
            .build()

        val response = client.getItem(request)
        return response.item()?.toUser()

    }

    override fun getUsers(lastKey: UserPaginationKey?): Pair<UserPaginationKey?,List<User>> {

        val client = dbConfig.userTable.buildDynamoDbClient()
        val scanRequestBuilder = ScanRequest.builder()
            .tableName(dbConfig.userTable.tableName)
            .limit(dbConfig.userScanLimit)

        if (lastKey != null) {
            scanRequestBuilder.exclusiveStartKey(lastKey.toAttributeValue())
        }
        val scanRequest = scanRequestBuilder.build()

        val response: ScanResponse = client.scan(scanRequest)
        val users = response.items().map { record ->
            record.toUser()
        }
        val lastUser = if( response.hasLastEvaluatedKey()) {
            response.lastEvaluatedKey()?.toUserPaginationKey()
        } else {
            null
        }
        return lastUser to users

    }


    override fun upsertUser(user: User) : User? {
        val client = dbConfig.userTable.buildDynamoDbClient()
        val itemValues = user.toAttributeValues()
        //.attributes.map { i -> i.key to AttributeValue.builder().s(i.value).build() }.toMap()

        val request = PutItemRequest.builder()
            .tableName(dbConfig.userTable.tableName)
            .item(itemValues)
            .build()

        client.putItem(request)
        return user // putItem returns nothing; have to return input if we don't want to change the interface
    }

    override fun deleteUser(id: String) : User {
        val client = dbConfig.userTable.buildDynamoDbClient()

        val keyToGet =
            HashMap<String, AttributeValue>()

        keyToGet[USER_ID_FIELD] = AttributeValue.builder()
            .s(id)
            .build()

        val deleteReq = DeleteItemRequest.builder()
            .tableName(dbConfig.userTable.tableName)
            .key(keyToGet)
            .build()

        val rawResponse = client.deleteItem(deleteReq)
        return rawResponse.attributes().toUser()
    }

}
