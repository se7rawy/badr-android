/*
 * Copyright 2021 The Matrix.org Foundation C.I.C.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.matrix.android.sdk.internal.network

import org.matrix.android.sdk.internal.network.defaultRequestRetryPolicy as internalDefaultRequestRetryPolicy
import org.matrix.android.sdk.internal.network.executeRequest as internalExecuteRequest

internal interface RequestExecutor {
    suspend fun <DATA> executeRequest(globalErrorReceiver: GlobalErrorReceiver?,
                                      maxDelayBeforeRetry: Long = 32_000L,
                                      maxRetriesCount: Int = 4,
                                      canRetryOnFailure: (Throwable) -> Boolean = internalDefaultRequestRetryPolicy,
                                      requestBlock: suspend () -> DATA): DATA
}

internal object DefaultRequestExecutor : RequestExecutor {
    override suspend fun <DATA> executeRequest(globalErrorReceiver: GlobalErrorReceiver?,
                                               maxDelayBeforeRetry: Long,
                                               maxRetriesCount: Int,
                                               canRetryOnFailure: (Throwable) -> Boolean,
                                               requestBlock: suspend () -> DATA): DATA {
        return internalExecuteRequest(globalErrorReceiver, maxDelayBeforeRetry, maxRetriesCount, canRetryOnFailure, requestBlock)
    }
}
