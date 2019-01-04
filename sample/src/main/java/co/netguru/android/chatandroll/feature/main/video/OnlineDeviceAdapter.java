package co.netguru.android.chatandroll.feature.main.video;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import co.netguru.android.chatandroll.R;

public class OnlineDeviceAdapter extends RecyclerView.Adapter<OnlineDeviceAdapter.OnlineDeviceViewHolder> {

    private List<String> mOnlineDeviceList;
    private OnlineDeviceSelectedListener mListener;

    public OnlineDeviceAdapter(OnlineDeviceSelectedListener listener, List<String> mOnlineDeviceList) {
        this.mOnlineDeviceList = mOnlineDeviceList;
        this.mListener = listener;
    }


    @Override
    public OnlineDeviceViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.online_devices_layout_obj, parent, false);
        return new OnlineDeviceViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(final OnlineDeviceViewHolder holder, final int position) {
        final String onlineDeviceName = mOnlineDeviceList.get(position);
        holder.txtDeviceName.setText(onlineDeviceName);
        holder.txtDeviceName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mListener.onOnlineDeviceSelected(onlineDeviceName);
            }
        });
    }

    @Override
    public int getItemCount() {
        return mOnlineDeviceList.size();
    }

    public class OnlineDeviceViewHolder extends  RecyclerView.ViewHolder {

        public TextView txtDeviceName;

        public OnlineDeviceViewHolder(View itemView) {
            super(itemView);
            txtDeviceName = itemView.findViewById(R.id.txtDeviceName);
        }
    }

}
