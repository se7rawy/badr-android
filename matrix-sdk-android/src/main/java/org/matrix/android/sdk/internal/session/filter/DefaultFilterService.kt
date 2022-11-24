/*
 * Copyright 2020 The Matrix.org Foundation C.I.C.
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

package org.matrix.android.sdk.internal.session.filter

import org.matrix.android.sdk.api.session.sync.FilterService
import org.matrix.android.sdk.internal.session.homeserver.HomeServerCapabilitiesDataSource
import org.matrix.android.sdk.internal.task.TaskExecutor
import org.matrix.android.sdk.internal.task.configureWith
import javax.inject.Inject

internal class DefaultFilterService @Inject constructor(
        private val saveFilterTask: SaveFilterTask,
        private val taskExecutor: TaskExecutor,
        private val filterRepository: FilterRepository,
        private val homeServerCapabilitiesDataSource: HomeServerCapabilitiesDataSource,
) : FilterService {

    // TODO Pass a list of support events instead
    override suspend fun setSyncFilter(filterBuilder: SyncFilterBuilder) {
        filterRepository.storeFilterParams(filterBuilder.extractParams())

        // don't upload/store filter until homeserver capabilities are fetched
        homeServerCapabilitiesDataSource.getHomeServerCapabilities()?.let { homeServerCapabilities ->
            saveFilterTask
                    .configureWith(
                            SaveFilterTask.Params(
                                    filter = filterBuilder.build(homeServerCapabilities)
                            )
                    )
                    .executeBy(taskExecutor)
        }
    }
}
