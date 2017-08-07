package fitme.ai.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;


/**
 * Created by AA on 2017/3/23.
 */
public abstract class HotSpotBroadcaseReceiver extends BroadcastReceiver {

    public static final String ACTION_HOTSPOT_STATE_CHANGED = "android.net.wifi.WIFI_AP_STATE_CHANGED";


    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        L.i("getActiongetActiongetActiongetAction");
        if(action.equals(ACTION_HOTSPOT_STATE_CHANGED)) {
            //便携热点状态监听
            L.i("便携热点状态监听");
            int state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0);
                L.i("便携热点可用");
                //便携热点可用
                onHotSpotEnabled();
        }
    }

    /**
     * 便携热点可用
     */
    public abstract void onHotSpotEnabled();
}
