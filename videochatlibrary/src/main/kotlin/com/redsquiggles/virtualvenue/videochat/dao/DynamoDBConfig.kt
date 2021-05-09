package com.redsquiggles.virtualvenue.videochat.dao

/*
 * Copyright (c) 2020. Red Squiggles Software Inc. or its affiliates. All rights reversed.
 *
 *  This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR  CONDITIONS OF ANY KIND, either express or implied.
 *
 */

import com.redsquiggles.virtualvenue.dynamodb.DynamoDbParameters

data class DynamoDbConfig(
    val userTable : DynamoDbParameters,

    val userScanLimit: Int
)
