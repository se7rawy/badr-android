/*
 * Copyright 2019 New Vector Ltd
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

package im.vector.riotx.features.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.forEachIndexed
import androidx.lifecycle.Observer
import com.airbnb.mvrx.activityViewModel
import com.airbnb.mvrx.fragmentViewModel
import com.airbnb.mvrx.withState
import com.google.android.material.bottomnavigation.BottomNavigationItemView
import com.google.android.material.bottomnavigation.BottomNavigationMenuView
import im.vector.matrix.android.api.session.crypto.keysbackup.KeysBackupState
import im.vector.matrix.android.api.session.group.model.GroupSummary
import im.vector.matrix.android.api.util.toMatrixItem
import im.vector.matrix.android.internal.crypto.model.rest.DeviceInfo
import im.vector.riotx.R
import im.vector.riotx.core.extensions.commitTransactionNow
import im.vector.riotx.core.glide.GlideApp
import im.vector.riotx.core.platform.ToolbarConfigurable
import im.vector.riotx.core.platform.VectorBaseActivity
import im.vector.riotx.core.platform.VectorBaseFragment
import im.vector.riotx.core.resources.ColorProvider
import im.vector.riotx.core.ui.views.KeysBackupBanner
import im.vector.riotx.features.home.room.list.RoomListFragment
import im.vector.riotx.features.home.room.list.RoomListParams
import im.vector.riotx.features.home.room.list.UnreadCounterBadgeView
import im.vector.riotx.features.popup.PopupAlertManager
import im.vector.riotx.features.popup.VerificationVectorAlert
import im.vector.riotx.features.settings.VectorSettingsActivity.Companion.EXTRA_DIRECT_ACCESS_SECURITY_PRIVACY_MANAGE_SESSIONS
import im.vector.riotx.features.workers.signout.SignOutViewModel
import kotlinx.android.synthetic.main.fragment_home_detail.*
import timber.log.Timber
import javax.inject.Inject

private const val INDEX_CATCHUP = 0
private const val INDEX_PEOPLE = 1
private const val INDEX_ROOMS = 2

class HomeDetailFragment @Inject constructor(
        val homeDetailViewModelFactory: HomeDetailViewModel.Factory,
        private val avatarRenderer: AvatarRenderer,
        private val alertManager: PopupAlertManager
) : VectorBaseFragment(), KeysBackupBanner.Delegate {

    private val unreadCounterBadgeViews = arrayListOf<UnreadCounterBadgeView>()

    private val viewModel: HomeDetailViewModel by fragmentViewModel()
    private val unknownDeviceDetectorSharedViewModel: UnknownDeviceDetectorSharedViewModel by activityViewModel()

    private lateinit var sharedActionViewModel: HomeSharedActionViewModel

    override fun getLayoutResId() = R.layout.fragment_home_detail

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sharedActionViewModel = activityViewModelProvider.get(HomeSharedActionViewModel::class.java)

        setupBottomNavigationView()
        setupToolbar()
        setupKeysBackupBanner()

        withState(viewModel) {
            // Update the navigation view if needed (for when we restore the tabs)
            bottomNavigationView.selectedItemId = it.displayMode.toMenuId()
            bottomNavigationView.visibility = if (bottomNavigationView.selectedItemId != R.id.bottom_action_home) View.VISIBLE else View.GONE
        }

        viewModel.selectSubscribe(this, HomeDetailViewState::groupSummary) { groupSummary ->
            onGroupChange(groupSummary.orNull())
        }
        viewModel.selectSubscribe(this, HomeDetailViewState::displayMode) { displayMode ->
            switchDisplayMode(displayMode)
        }

        unknownDeviceDetectorSharedViewModel.subscribe { state ->
            state.unknownSessions.invoke()?.let { unknownDevices ->
//                Timber.v("## Detector Triggerred in fragment - ${unknownDevices.firstOrNull()}")
                if (unknownDevices.firstOrNull()?.currentSessionTrust == true) {
                    val uid = "review_login"
                    alertManager.cancelAlert(uid)
                    val olderUnverified = unknownDevices.filter { !it.isNew }
                    val newest = unknownDevices.firstOrNull { it.isNew }?.deviceInfo
                    if (newest != null) {
                        promptForNewUnknownDevices(uid, state, newest)
                    } else if (olderUnverified.isNotEmpty()) {
                        // In this case we prompt to go to settings to review logins
                        promptToReviewChanges(uid, state, olderUnverified.map { it.deviceInfo })
                    }
                }
            }
        }
    }

    private fun promptForNewUnknownDevices(uid: String, state: UnknownDevicesState, newest: DeviceInfo) {
        val user = state.myMatrixItem
        alertManager.postVectorAlert(
                VerificationVectorAlert(
                        uid = uid,
                        title = getString(R.string.new_session),
                        description = getString(R.string.verify_this_session, newest.displayName ?: newest.deviceId ?: ""),
                        iconId = R.drawable.ic_shield_warning
                ).apply {
                    matrixItem = user
                    colorInt = ColorProvider(requireActivity()).getColor(R.color.riotx_accent)
                    contentAction = Runnable {
                        (weakCurrentActivity?.get() as? VectorBaseActivity)
                                ?.navigator
                                ?.requestSessionVerification(requireContext(), newest.deviceId ?: "")
                        unknownDeviceDetectorSharedViewModel.handle(
                                UnknownDeviceDetectorSharedViewModel.Action.IgnoreDevice(newest.deviceId?.let { listOf(it) }.orEmpty())
                        )
                    }
                    dismissedAction = Runnable {
                        unknownDeviceDetectorSharedViewModel.handle(
                                UnknownDeviceDetectorSharedViewModel.Action.IgnoreDevice(newest.deviceId?.let { listOf(it) }.orEmpty())
                        )
                    }
                }
        )
    }

    private fun promptToReviewChanges(uid: String, state: UnknownDevicesState, oldUnverified: List<DeviceInfo>) {
        val user = state.myMatrixItem
        alertManager.postVectorAlert(
                VerificationVectorAlert(
                        uid = uid,
                        title = getString(R.string.review_logins),
                        description = getString(R.string.verify_other_sessions),
                        iconId = R.drawable.ic_shield_warning
                ).apply {
                    matrixItem = user
                    colorInt = ColorProvider(requireActivity()).getColor(R.color.riotx_accent)
                    contentAction = Runnable {
                        (weakCurrentActivity?.get() as? VectorBaseActivity)?.let {
                            // mark as ignored to avoid showing it again
                            unknownDeviceDetectorSharedViewModel.handle(
                                    UnknownDeviceDetectorSharedViewModel.Action.IgnoreDevice(oldUnverified.mapNotNull { it.deviceId })
                            )
                            it.navigator.openSettings(it, EXTRA_DIRECT_ACCESS_SECURITY_PRIVACY_MANAGE_SESSIONS)
                        }
                    }
                    dismissedAction = Runnable {
                        unknownDeviceDetectorSharedViewModel.handle(
                                UnknownDeviceDetectorSharedViewModel.Action.IgnoreDevice(oldUnverified.mapNotNull { it.deviceId })
                        )
                    }
                }
        )
    }

    private fun onGroupChange(groupSummary: GroupSummary?) {
        groupSummary?.let {
            // Use GlideApp with activity context to avoid the glideRequests to be paused
            avatarRenderer.render(it.toMatrixItem(), groupToolbarAvatarImageView, GlideApp.with(requireActivity()))
        }
    }

    private fun setupKeysBackupBanner() {
        // Keys backup banner
        // Use the SignOutViewModel, it observe the keys backup state and this is what we need here
        val model = fragmentViewModelProvider.get(SignOutViewModel::class.java)

        model.keysBackupState.observe(viewLifecycleOwner, Observer { keysBackupState ->
            when (keysBackupState) {
                null                               ->
                    homeKeysBackupBanner.render(KeysBackupBanner.State.Hidden, false)
                KeysBackupState.Disabled           ->
                    homeKeysBackupBanner.render(KeysBackupBanner.State.Setup(model.getNumberOfKeysToBackup()), false)
                KeysBackupState.NotTrusted,
                KeysBackupState.WrongBackUpVersion ->
                    // In this case, getCurrentBackupVersion() should not return ""
                    homeKeysBackupBanner.render(KeysBackupBanner.State.Recover(model.getCurrentBackupVersion()), false)
                KeysBackupState.WillBackUp,
                KeysBackupState.BackingUp          ->
                    homeKeysBackupBanner.render(KeysBackupBanner.State.BackingUp, false)
                KeysBackupState.ReadyToBackUp      ->
                    if (model.canRestoreKeys()) {
                        homeKeysBackupBanner.render(KeysBackupBanner.State.Update(model.getCurrentBackupVersion()), false)
                    } else {
                        homeKeysBackupBanner.render(KeysBackupBanner.State.Hidden, false)
                    }
                else                               ->
                    homeKeysBackupBanner.render(KeysBackupBanner.State.Hidden, false)
            }
        })

        homeKeysBackupBanner.delegate = this
    }

    private fun setupToolbar() {
        val parentActivity = vectorBaseActivity
        if (parentActivity is ToolbarConfigurable) {
            parentActivity.configure(groupToolbar)
        }
        groupToolbar.title = ""
        groupToolbarAvatarImageView.debouncedClicks {
            sharedActionViewModel.post(HomeActivitySharedAction.OpenDrawer)
        }
    }

    private fun setupBottomNavigationView() {
        bottomNavigationView.setOnNavigationItemSelectedListener {
            val displayMode = when (it.itemId) {
                R.id.bottom_action_people -> RoomListDisplayMode.PEOPLE
                R.id.bottom_action_rooms  -> RoomListDisplayMode.ROOMS
                else                      -> RoomListDisplayMode.HOME
            }
            viewModel.handle(HomeDetailAction.SwitchDisplayMode(displayMode))
            true
        }

        val menuView = bottomNavigationView.getChildAt(0) as BottomNavigationMenuView
        menuView.forEachIndexed { index, view ->
            val itemView = view as BottomNavigationItemView
            val badgeLayout = LayoutInflater.from(requireContext()).inflate(R.layout.vector_home_badge_unread_layout, menuView, false)
            val unreadCounterBadgeView: UnreadCounterBadgeView = badgeLayout.findViewById(R.id.actionUnreadCounterBadgeView)
            itemView.addView(badgeLayout)
            unreadCounterBadgeViews.add(index, unreadCounterBadgeView)
        }
    }

    private fun switchDisplayMode(displayMode: RoomListDisplayMode) {
        groupToolbarTitleView.setText(displayMode.titleRes)
        updateSelectedFragment(displayMode)
    }

    private fun updateSelectedFragment(displayMode: RoomListDisplayMode) {
        val fragmentTag = "FRAGMENT_TAG_${displayMode.name}"
        val fragmentToShow = childFragmentManager.findFragmentByTag(fragmentTag)
        childFragmentManager.commitTransactionNow {
            childFragmentManager.fragments
                    .filter { it != fragmentToShow }
                    .forEach {
                        detach(it)
                    }
            if (fragmentToShow == null) {
                val params = RoomListParams(displayMode)
                add(R.id.roomListContainer, RoomListFragment::class.java, params.toMvRxBundle(), fragmentTag)
            } else {
                attach(fragmentToShow)
            }
        }
    }

    /* ==========================================================================================
     * KeysBackupBanner Listener
     * ========================================================================================== */

    override fun setupKeysBackup() {
        navigator.openKeysBackupSetup(requireActivity(), false)
    }

    override fun recoverKeysBackup() {
        navigator.openKeysBackupManager(requireActivity())
    }

    override fun invalidate() = withState(viewModel) {
        Timber.v(it.toString())
        unreadCounterBadgeViews[INDEX_CATCHUP].render(UnreadCounterBadgeView.State(it.notificationCountCatchup, it.notificationHighlightCatchup))
        unreadCounterBadgeViews[INDEX_PEOPLE].render(UnreadCounterBadgeView.State(it.notificationCountPeople, it.notificationHighlightPeople))
        unreadCounterBadgeViews[INDEX_ROOMS].render(UnreadCounterBadgeView.State(it.notificationCountRooms, it.notificationHighlightRooms))
        syncStateView.render(it.syncState)
    }

    private fun RoomListDisplayMode.toMenuId() = when (this) {
        RoomListDisplayMode.PEOPLE -> R.id.bottom_action_people
        RoomListDisplayMode.ROOMS  -> R.id.bottom_action_rooms
        else                       -> R.id.bottom_action_home
    }
}
