package com.kylecorry.luna.coroutines

import kotlinx.coroutines.*

suspend fun <T> onMain(block: suspend CoroutineScope.() -> T): T = withContext(Dispatchers.Main, block)

suspend fun <T> onDefault(block: suspend CoroutineScope.() -> T): T = withContext(Dispatchers.Default, block)

suspend fun <T> onIO(block: suspend CoroutineScope.() -> T): T = withContext(Dispatchers.IO, block)

suspend fun <T> onUnconfined(block: suspend CoroutineScope.() -> T): T = withContext(Dispatchers.Unconfined, block)