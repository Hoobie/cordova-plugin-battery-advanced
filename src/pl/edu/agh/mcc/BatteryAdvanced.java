package pl.edu.agh.mcc;

import android.content.Context;
import android.os.BatteryManager;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class BatteryAdvanced extends CordovaPlugin {

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("check")) {
            this.checkBatteryStatus(callbackContext);
            return true;
        }
        return false;
    }

    private void checkBatteryStatus(CallbackContext callbackContext) {
        BatteryManager bm = (BatteryManager) webView.getContext().getSystemService(Context.BATTERY_SERVICE);

        JSONObject obj = new JSONObject();
        try {
            obj.put("remainingCapacityMAh", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER));
            obj.put("capacityMAh", getBatteryCapacity());
        } catch (Exception e) {
            callbackContext.error(e.getMessage());
        }
        callbackContext.success(obj);
    }

    private double getBatteryCapacity() throws Exception {
        final String POWER_PROFILE_CLASS = "com.android.internal.os.PowerProfile";

        Object mPowerProfile_ = Class.forName(POWER_PROFILE_CLASS)
                .getConstructor(Context.class).newInstance(webView.getContext());

        return (double) (Double) Class
                .forName(POWER_PROFILE_CLASS)
                .getMethod("getAveragePower", String.class)
                .invoke(mPowerProfile_, "battery.capacity");
    }
}
