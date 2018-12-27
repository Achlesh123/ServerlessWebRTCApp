package co.netguru.android.chatandroll.webrtc.service

import android.os.Handler
import android.os.Looper
import android.util.Log
import co.netguru.videochatguru.PeerConnectionListener
import co.netguru.videochatguru.WebRtcAnsweringPartyListener
import co.netguru.videochatguru.WebRtcClient
import co.netguru.videochatguru.WebRtcOfferingActionListener
import co.netguru.android.chatandroll.common.extension.ChildEventAdded
import co.netguru.android.chatandroll.common.util.RxUtils
import co.netguru.android.chatandroll.data.firebase.FirebaseIceCandidates
import co.netguru.android.chatandroll.data.firebase.FirebaseIceServers
import co.netguru.android.chatandroll.data.firebase.FirebaseSignalingAnswers
import co.netguru.android.chatandroll.data.firebase.FirebaseSignalingOffers
import co.netguru.android.chatandroll.feature.base.service.BaseServiceController
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import timber.log.Timber
import javax.inject.Inject


class WebRtcServiceController @Inject constructor(
        private val webRtcClient: WebRtcClient,
        private val firebaseSignalingAnswers: FirebaseSignalingAnswers,
        private val firebaseSignalingOffers: FirebaseSignalingOffers,
        private val firebaseIceCandidates: FirebaseIceCandidates,
        private val firebaseIceServers: FirebaseIceServers) : BaseServiceController<WebRtcServiceFacade>() {

    var serviceListener: WebRtcServiceListener? = null
    var remoteUuid: String? = null
    val mainThreadHandler = Handler(Looper.getMainLooper())

    private val disposables = CompositeDisposable()

    private var finishedInitializing = false
    private var shouldCreateOffer = false
    private var isOfferingParty = false

    override fun attachService(service: WebRtcServiceFacade) {
        super.attachService(service)
        //loadIceServers()

//        listenForOffers()
        initializeWebRtc(iceServers)
    }

    val iceServers = listOf(PeerConnection.IceServer("stun:stun.l.google.com:19302"),
            PeerConnection.IceServer("stun:stun1.l.google.com:19302"),
            PeerConnection.IceServer("stun:stun2.l.google.com:19302"),
            PeerConnection.IceServer("stun:stu3.l.google.com:19302"),
            PeerConnection.IceServer("stun:stun4.l.google.com:19302"))


    override fun detachService() {
        super.detachService()
        disposables.dispose()
        webRtcClient.detachViews()
        webRtcClient.dispose()
    }

    fun offerDevice() {
        isOfferingParty = true
        if (finishedInitializing)
            webRtcClient.createOffer()
        else
            shouldCreateOffer = true
    }

    fun attachRemoteView(remoteView: SurfaceViewRenderer) {
        webRtcClient.attachRemoteView(remoteView)
    }

    fun attachLocalView(localView: SurfaceViewRenderer) {
        webRtcClient.attachLocalView(localView)
    }

    fun detachViews() {
        webRtcClient.detachViews()
    }

    fun switchCamera() = webRtcClient.switchCamera()

    fun enableCamera(isEnabled: Boolean) {
        webRtcClient.cameraEnabled = isEnabled
    }

    fun isCameraEnabled() = webRtcClient.cameraEnabled

    fun enableMicrophone(enabled: Boolean) {
        webRtcClient.microphoneEnabled = enabled
    }

    fun isMicrophoneEnabled() = webRtcClient.microphoneEnabled

    private fun loadIceServers() {
        disposables += firebaseIceServers.getIceServers()
                .subscribeBy(
                        onSuccess = {
//                            listenForOffers()
//                            initializeWebRtc(it)
                        },
                        onError = {
                            handleCriticalException(it)
                        }
                )
    }

    private fun initializeWebRtc(iceServers: List<PeerConnection.IceServer>) {
        webRtcClient.initializePeerConnection(iceServers,
                peerConnectionListener = object : PeerConnectionListener {
                    override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState) {
                        if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED && isOfferingParty) {
                            webRtcClient.restart()
                        }
                        mainThreadHandler.post {
                            serviceListener?.connectionStateChange(iceConnectionState)
                        }

                    }

                    override fun onIceCandidate(iceCandidate: IceCandidate) {
                        sendIceCandidate(iceCandidate)
                    }

                    override fun onIceCandidatesRemoved(iceCandidates: Array<IceCandidate>) {
                        removeIceCandidates(iceCandidates)
                    }

                },
                webRtcOfferingActionListener = object : WebRtcOfferingActionListener {
                    override fun onError(error: String) {
                        Timber.e("Error in offering party: $error")
                    }

                    override fun onOfferRemoteDescription(localSessionDescription: SessionDescription) {
//                        listenForAnswers()
                        sendOffer(localSessionDescription)
                    }

                },
                webRtcAnsweringPartyListener = object : WebRtcAnsweringPartyListener {
                    override fun onError(error: String) {
                        Timber.e("Error in answering party: $error")
                    }

                    override fun onSuccess(localSessionDescription: SessionDescription) {
                        sendAnswer(localSessionDescription)
                    }
                })
//        if (shouldCreateOffer)
//            webRtcClient.createOffer()
        finishedInitializing = true
    }

    private fun sendIceCandidate(iceCandidate: IceCandidate) {
        webRtcClient.addRemoteIceCandidate(iceCandidate)
    }

    private fun removeIceCandidates(iceCandidates: Array<IceCandidate>) {
        webRtcClient.removeRemoteIceCandidate(iceCandidates)
    }

    private fun sendOffer(localDescription: SessionDescription) {

        var createdOfferString = sessionDescriptionToJSON(localDescription).toString()
        Log.d("CreatedOffer", createdOfferString);
        serviceListener?.showCreatedOffer(createdOfferString)
    }

    private val JSON_TYPE = "type"
    private val JSON_MESSAGE = "message"
    private val JSON_SDP = "sdp"

    private fun sessionDescriptionToJSON(sessDesc: SessionDescription): JSONObject {
        val json = JSONObject()
        json.put(JSON_TYPE, sessDesc.type.canonicalForm())
        json.put(JSON_SDP, sessDesc.description)
        return json
    }

    fun handleRemoteOffer(sessionDescription : SessionDescription) {
        webRtcClient.handleRemoteOffer(sessionDescription)

    }

    fun handleRemoteAnswer(sessionDescription: SessionDescription) {
        webRtcClient.handleRemoteAnswer(sessionDescription)
    }

    private fun sendAnswer(localDescription: SessionDescription) {
        var createdAnswerString = sessionDescriptionToJSON(localDescription).toString()
        Log.d("CreatedAnswer", createdAnswerString);
        serviceListener?.showCreatedAnswer(createdAnswerString)
    }

    private fun handleCriticalException(throwable: Throwable) {
        serviceListener?.criticalWebRTCServiceException(throwable)
        getService()?.stop()
    }
}
