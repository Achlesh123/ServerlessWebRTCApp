package co.netguru.android.chatandroll.feature.main.video

import android.Manifest
import android.animation.TimeInterpolator
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.IBinder
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.Snackbar
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.view.animation.OvershootInterpolator
import co.netguru.android.chatandroll.R
import co.netguru.android.chatandroll.app.App
import co.netguru.android.chatandroll.common.extension.areAllPermissionsGranted
import co.netguru.android.chatandroll.common.extension.startAppSettings
import co.netguru.android.chatandroll.feature.base.BaseMvpFragment
import co.netguru.android.chatandroll.feature.main.RecyclerViewConsole
import co.netguru.android.chatandroll.webrtc.service.WebRtcService
import co.netguru.android.chatandroll.webrtc.service.WebRtcServiceListener
import kotlinx.android.synthetic.main.fragment_video.*
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import timber.log.Timber


class VideoFragment : BaseMvpFragment<VideoFragmentView, VideoFragmentPresenter>(), VideoFragmentView, WebRtcServiceListener {

    companion object {
        val TAG: String = VideoFragment::class.java.name

        fun newInstance() = VideoFragment()

        private const val KEY_IN_CHAT = "key:in_chat"
        private const val CHECK_PERMISSIONS_AND_CONNECT_REQUEST_CODE = 1
        private val NECESSARY_PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        private const val CONNECT_BUTTON_ANIMATION_DURATION_MS = 500L
    }

    var state: State = State.WAITING_FOR_OFFER
    lateinit var console: RecyclerViewConsole


    private lateinit var serviceConnection: ServiceConnection

    override fun getLayoutId() = R.layout.fragment_video

    override fun retrievePresenter() = App.getApplicationComponent(context).videoFragmentComponent().videoFragmentPresenter()

    var service: WebRtcService? = null

    override val remoteUuid
        get() = service?.getRemoteUuid()

    override fun showCreatedOffer(offer: String) {

        activity.runOnUiThread {
            console.bluef("Your offer is:")
            console.greenf(offer)
            state = State.WAITING_FOR_ANSWER
            edEnterArea.setHint(state.name)
            progressBar.visibility = View.GONE
        }
    }

    override fun showCreatedAnswer(answer: String) {
        activity.runOnUiThread {
            console.bluef("Your answer is:")
            console.greenf(answer)
            state = State.WAITING_TO_CONNECT
            edEnterArea.setHint(state.name)
            progressBar.visibility = View.GONE
        }
    }

    private fun sendMessage() {
        val newText = edEnterArea.text.toString().trim()
        if(newText.isEmpty()) {
            console.redf("Invalid Json")
            return
        }

        when (state) {
            State.WAITING_FOR_OFFER -> {

                var sdp = this!!.convertOfferToSdpObject(newText)

                if(sdp != null) {
                    service?.handleRemoteOffer(sdp)
                }
                edEnterArea.setText("")
            }

            State.WAITING_FOR_ANSWER -> {

                var sdp = this!!.convertAnswerToSdpObject(newText)

                if(sdp != null) {
                    service?.handleRemoteAnswer(sdp)
                }

                edEnterArea.setText("")
            }

            State.CHAT_ESTABLISHED -> {

            }
            else -> if (newText.isNotBlank())
                console.redf("Invalid Json")
        }
        edEnterArea.setText("")
    }

    private val JSON_TYPE = "type"
    private val JSON_MESSAGE = "message"
    private val JSON_SDP = "sdp"

    /**
     * Process offer that was entered by user (this is called getAnswer() in JavaScript example)
     */
    private fun convertAnswerToSdpObject(sdpJSON: String) : SessionDescription? {
        try {
            val json = JSONObject(sdpJSON)
            val type = json.getString(JSON_TYPE)
            val sdp = json.getString(JSON_SDP)
            state = State.WAITING_TO_CONNECT
            if (type != null && sdp != null && type == "answer") {
                return SessionDescription(SessionDescription.Type.ANSWER, sdp)
            } else {
                console.redf("Invalid or unsupported answer.")
                state = State.WAITING_FOR_ANSWER
                return null
            }
        } catch (e: JSONException) {
            console.redf("bad json")
            state = State.WAITING_FOR_ANSWER
        }
        return null;
    }

    /**
     * Process offer that was entered by user (this is called getAnswer() in JavaScript example)
     */
    private fun convertOfferToSdpObject(sdpJSON: String) : SessionDescription? {
        try {
            val json = JSONObject(sdpJSON)
            val type = json.getString(JSON_TYPE)
            val sdp = json.getString(JSON_SDP)
            state = State.WAITING_TO_CONNECT
            if (type != null && sdp != null && type == "offer") {
                return SessionDescription(SessionDescription.Type.OFFER, sdp)
            } else {
                console.redf("Invalid or unsupported answer.")
                state = State.WAITING_FOR_ANSWER
                return null
            }
        } catch (e: JSONException) {
            console.redf("bad json")
            state = State.WAITING_FOR_ANSWER
        }
        return null;
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (buttonPanel.layoutParams as CoordinatorLayout.LayoutParams).behavior = MoveUpBehavior()
        (localVideoView.layoutParams as CoordinatorLayout.LayoutParams).behavior = MoveUpBehavior()
        activity.volumeControlStream = AudioManager.STREAM_VOICE_CALL

        val layoutManager = LinearLayoutManager(activity)
        recyclerView.layoutManager = layoutManager

        layoutManager.stackFromEnd = true
        console = RecyclerViewConsole(recyclerView)
        console.initialize(savedInstanceState)
        recyclerView.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (bottom < oldBottom) {
                recyclerView.postDelayed({ recyclerView.smoothScrollToPosition(console.lines.size) }, 100)
            }
        }

        edEnterArea.hint = State.PASTE_YOUR_OFFER_HERE.name
        progressBar.visibility = View.GONE
        if (savedInstanceState?.getBoolean(KEY_IN_CHAT) == true) {
            initAlreadyRunningConnection()
        }

        checkPermissionsAndConnect()

        btSubmit.setOnClickListener {
            sendMessage()
        }

        btnCreateOffer.setOnClickListener {
            //            checkPermissionsAndConnect()
            state = State.CREATING_OFFER
            edEnterArea.setHint(state.name)
            service?.offerDevice()
            btnCreateOffer.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
        }

        disconnectButton.setOnClickListener {
            getPresenter().disconnectByUser()
        }

        switchCameraButton.setOnClickListener {
            service?.switchCamera()
        }

        cameraEnabledToggle.setOnCheckedChangeListener { _, enabled ->
            service?.enableCamera(enabled)
        }

        microphoneEnabledToggle.setOnCheckedChangeListener { _, enabled ->
            service?.enableMicrophone(enabled)
        }
    }

    override fun onStart() {
        super.onStart()
        service?.hideBackgroundWorkWarning()
    }

    override fun onStop() {
        super.onStop()
        if (!activity.isChangingConfigurations) {
            service?.showBackgroundWorkWarning()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        service?.let {
            it.detachViews()
            unbindService()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (remoteVideoView.visibility == View.VISIBLE) {
            outState.putBoolean(KEY_IN_CHAT, true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!activity.isChangingConfigurations) disconnect()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) = when (requestCode) {
        CHECK_PERMISSIONS_AND_CONNECT_REQUEST_CODE -> {
            val grantResult = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (grantResult) {
                checkPermissionsAndConnect()
            } else {
                showNoPermissionsSnackbar()
            }
        }
        else -> {
            error("Unknown permission request code $requestCode")
        }
    }

    override fun attachService() {
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
                onWebRtcServiceConnected((iBinder as (WebRtcService.LocalBinder)).service)
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                onWebRtcServiceDisconnected()
            }
        }
        startAndBindWebRTCService(serviceConnection)
    }

    @SuppressLint("Range")
    override fun criticalWebRTCServiceException(throwable: Throwable) {
        unbindService()
        showSnackbarMessage(R.string.error_web_rtc_error, Snackbar.LENGTH_LONG)
        Timber.e(throwable, "Critical WebRTC service error")
    }

    override fun connectionStateChange(iceConnectionState: PeerConnection.IceConnectionState) {
        getPresenter().connectionStateChange(iceConnectionState)
    }

    override fun connectTo(uuid: String) {
//        service?.offerDevice(uuid)
    }

    override fun disconnect() {
        service?.let {
            it.stopSelf()
            unbindService()
        }
    }

    private fun unbindService() {
        service?.let {
            it.detachServiceActionsListener()
            context.unbindService(serviceConnection)
            service = null
        }
    }

    override fun showCamViews() {
        buttonPanel.visibility = View.VISIBLE
        remoteVideoView.visibility = View.VISIBLE
        localVideoView.visibility = View.VISIBLE
        connectButton.visibility = View.GONE
    }

    override fun showStartRouletteView() {
        buttonPanel.visibility = View.GONE
        remoteVideoView.visibility = View.GONE
        localVideoView.visibility = View.GONE
        connectButton.visibility = View.VISIBLE
    }

    @SuppressLint("Range")
    override fun showErrorWhileChoosingRandom() {
        showSnackbarMessage(R.string.error_choosing_random_partner, Snackbar.LENGTH_LONG)
    }

    @SuppressLint("Range")
    override fun showNoOneAvailable() {
        showSnackbarMessage(R.string.msg_no_one_available, Snackbar.LENGTH_LONG)
    }

    @SuppressLint("Range")
    override fun showLookingForPartnerMessage() {
        showSnackbarMessage(R.string.msg_looking_for_partner, Snackbar.LENGTH_SHORT)
//        state = State.CREATING_OFFER
    }

    override fun hideConnectButtonWithAnimation() {
        connectButton.animate().scaleX(0f).scaleY(0f)
                .setInterpolator(OvershootInterpolator() as TimeInterpolator?)
                .setDuration(CONNECT_BUTTON_ANIMATION_DURATION_MS)
                .withStartAction { connectButton.isClickable = false }
                .withEndAction {
                    connectButton.isClickable = true
                    connectButton.visibility = View.GONE
                    connectButton.scaleX = 1f
                    connectButton.scaleY = 1f
                }
                .start()
    }

    @SuppressLint("Range")
    override fun showOtherPartyFinished() {
        showSnackbarMessage(R.string.msg_other_party_finished, Snackbar.LENGTH_SHORT)
    }

    @SuppressLint("Range")
    override fun showConnectedMsg() {
        showSnackbarMessage(R.string.msg_connected_to_other_party, Snackbar.LENGTH_LONG)
    }

    @SuppressLint("Range")
    override fun showWillTryToRestartMsg() {
        showSnackbarMessage(R.string.msg_will_try_to_restart_msg, Snackbar.LENGTH_LONG)
    }

    private fun initAlreadyRunningConnection() {
        showCamViews()
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
                onWebRtcServiceConnected((iBinder as (WebRtcService.LocalBinder)).service)
                getPresenter().listenForDisconnectOrders()
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                onWebRtcServiceDisconnected()
            }
        }
        startAndBindWebRTCService(serviceConnection)
    }

    private fun startAndBindWebRTCService(serviceConnection: ServiceConnection) {
        WebRtcService.startService(context)
        WebRtcService.bindService(context, serviceConnection)
    }

    private fun checkPermissionsAndConnect() {
        if (context.areAllPermissionsGranted(*NECESSARY_PERMISSIONS)) {
            getPresenter().connect()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), CHECK_PERMISSIONS_AND_CONNECT_REQUEST_CODE)
        }
    }

    @SuppressLint("Range")
    private fun showNoPermissionsSnackbar() {
        view?.let {
            Snackbar.make(it, R.string.msg_permissions, Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_settings) {
                        try {
                            context.startAppSettings()
                        } catch (e: ActivityNotFoundException) {
                            showSnackbarMessage(R.string.error_permissions_couldnt_start_settings, Snackbar.LENGTH_LONG)
                        }
                    }
                    .show()
        }
    }

    private fun onWebRtcServiceConnected(service: WebRtcService) {
        Timber.d("Service connected")
        this.service = service
        service.attachLocalView(localVideoView)
        service.attachRemoteView(remoteVideoView)
        syncButtonsState(service)
        service.attachServiceActionsListener(webRtcServiceListener = this)
    }

    private fun syncButtonsState(service: WebRtcService) {
        cameraEnabledToggle.isChecked = service.isCameraEnabled()
        microphoneEnabledToggle.isChecked = service.isMicrophoneEnabled()
    }

    private fun onWebRtcServiceDisconnected() {
        Timber.d("Service disconnected")
    }
}