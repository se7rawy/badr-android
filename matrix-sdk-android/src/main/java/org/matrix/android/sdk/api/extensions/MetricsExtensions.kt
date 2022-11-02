/*
 * Copyright 2022 The Matrix.org Foundation C.I.C.
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

package org.matrix.android.sdk.api.extensions

import org.matrix.android.sdk.api.metrics.MetricPlugin
import org.matrix.android.sdk.api.metrics.SpannableMetricPlugin
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Executes the given [block] while measuring the transaction.
 *
 * @param metricMeasurementPlugins Relevant plugins used for tracking.
 * @param block Action/Task to be executed within this span.
 */
@OptIn(ExperimentalContracts::class)
inline fun measureMetric(metricMeasurementPlugins: List<MetricPlugin>, block: () -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    try {
        metricMeasurementPlugins.forEach { plugin -> plugin.startTransaction() } // Start the transaction.
        block()
    } catch (throwable: Throwable) {
        metricMeasurementPlugins.forEach { plugin -> plugin.onError(throwable) } // Capture if there is any exception thrown.
        throw throwable
    } finally {
        metricMeasurementPlugins.forEach { plugin -> plugin.finishTransaction() } // Finally, finish this transaction.
    }
}

/**
 * Executes the given [block] while measuring a span.
 *
 * @param metricMeasurementPlugins Relevant plugins used for tracking.
 * @param operation Name of the new span.
 * @param description Description of the new span.
 * @param block Action/Task to be executed within this span.
 */
@OptIn(ExperimentalContracts::class)
inline fun measureSpan(metricMeasurementPlugins: List<SpannableMetricPlugin>, operation: String, description: String, block: () -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    try {
        metricMeasurementPlugins.forEach { plugin -> plugin.startSpan(operation, description) } // Start the transaction.
        block()
    } catch (throwable: Throwable) {
        metricMeasurementPlugins.forEach { plugin -> plugin.onError(throwable) } // Capture if there is any exception thrown.
        throw throwable
    } finally {
        metricMeasurementPlugins.forEach { plugin -> plugin.finishSpan() } // Finally, finish this transaction.
    }
}
