/*
 * Copyright (c) 2022 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.matrix.android.sdk.api.metrics

/**
 * A plugin that tracks span along with transactions.
 */
interface SpannableMetricPlugin : MetricPlugin {

    /**
     * Starts the span for a sub-task.
     *
     * @param operation Name of the new span.
     * @param description Description of the new span.
     */
    fun startSpan(operation: String, description: String)

    /**
     * Finish the span when sub-task is completed.
     */
    fun finishSpan()
}
