package co.netguru.android.chatandroll.webrtc.service;

import org.webrtc.PeerConnection;

import java.util.ArrayList;
import java.util.List;

public class CustomIceServer {

    private static CustomIceServer mInstance;
    private List<PeerConnection.IceServer> iceServers;

    public CustomIceServer() {
        iceServers = new ArrayList<>();
    }

    public static synchronized CustomIceServer  getIceServerInstance() {

        if(mInstance == null) {
            mInstance = new CustomIceServer();
        }

        return mInstance;
    }

    public List<PeerConnection.IceServer> getIceServers() {
        return iceServers;
    }

    public void addIceServer(PeerConnection.IceServer iceServers) {
        this.iceServers.add(iceServers);
    }

    public void clearIceServer() {
        iceServers.clear();
    }

    public boolean isIceServerAvailable(){
        if(iceServers.size()>0)
            return true;
        else
            return false;
    }
}
