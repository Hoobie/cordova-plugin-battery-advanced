package pl.edu.agh.mcc;

import android.content.Context;
import android.net.TrafficStats;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BatteryAdvanced extends CordovaPlugin {

    private static final int SECONDS_PER_HOUR = 3600;
    private static final int SECOND_HUNDREDS_PER_HOUR = 360000;
    private static final Map<String, Double> COMPONENTS_DRAIN_MAH = new HashMap<String, Double>();
    private static final ScheduledExecutorService EXECUTOR_SERVICE = Executors.newSingleThreadScheduledExecutor();

    private static CpuInfo previousCpuInfo;
    private static TransferInfo previousTransferInfo;
    private static long previousMeasurementMillis;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("start")) {
            this.startMeasurements(callbackContext);
            return true;
        } else if (action.equals("stop")) {
            this.stopMeasurements(callbackContext);
        }
        return false;
    }

    private void startMeasurements(CallbackContext callbackContext) {
        try {
            COMPONENTS_DRAIN_MAH.put("wifi", 0.0);
            COMPONENTS_DRAIN_MAH.put("mobile", 0.0);

            EXECUTOR_SERVICE.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    measureTransferDrains(1.0);
                }
            }, 1000, 1000, TimeUnit.MILLISECONDS);

            previousTransferInfo = new TransferInfo();
            previousCpuInfo = readCpuInfo();
            previousMeasurementMillis = System.currentTimeMillis();
        } catch (Exception e) {
            callbackContext.error(e.getMessage());
        }
        callbackContext.success();
    }

    private void stopMeasurements(CallbackContext callbackContext) {
        JSONObject obj = new JSONObject();
        try {
            CpuInfo nextCpuInfo = readCpuInfo();
            long cpuActiveTime = nextCpuInfo.activeTime - previousCpuInfo.activeTime;
            long cpuIdleTime = nextCpuInfo.idleTime - previousCpuInfo.idleTime;
            double cpuDrainMAh = getAveragePower("cpu.idle") * cpuIdleTime / SECOND_HUNDREDS_PER_HOUR
                    + getAveragePower("cpu.active") * cpuActiveTime / SECOND_HUNDREDS_PER_HOUR
                    + getAveragePower("cpu.idle") * cpuActiveTime / SECOND_HUNDREDS_PER_HOUR;
            previousCpuInfo = nextCpuInfo;

            long measurementInterval = System.currentTimeMillis() - previousMeasurementMillis;
            if (measurementInterval > 100 && measurementInterval < 900) {
                measureTransferDrains(measurementInterval / 1000);
            }

            obj.put("cpu", cpuDrainMAh);
            obj.put("wifi", COMPONENTS_DRAIN_MAH.get("wifi"));
            obj.put("mobile", COMPONENTS_DRAIN_MAH.get("mobile"));
            double total = cpuDrainMAh + COMPONENTS_DRAIN_MAH.get("wifi") + COMPONENTS_DRAIN_MAH.get("mobile");
            obj.put("total", total);
            obj.put("total%", total / getAveragePower("battery.capacity"));
        } catch (Exception e) {
            callbackContext.error(e.getCause() + ": " + e.getMessage());
        }
        callbackContext.success(obj);
    }

    private void measureTransferDrains(double scale) {
        TransferInfo nextTransferInfo = new TransferInfo();
        double wifiDrainMAh = COMPONENTS_DRAIN_MAH.get("wifi");
        double mobileDrainMAh = COMPONENTS_DRAIN_MAH.get("mobile");
        try {
            if (previousTransferInfo.wasWifiReceiving(nextTransferInfo)) {
                wifiDrainMAh += getAveragePower("wifi.controller.rx") / SECONDS_PER_HOUR * scale;
            }
            if (previousTransferInfo.wasWifiTransmitting(nextTransferInfo)) {
                wifiDrainMAh += getAveragePower("wifi.controller.tx") / SECONDS_PER_HOUR * scale;
            }
            if (previousTransferInfo.wasMobileReceiving(nextTransferInfo)
                    || previousTransferInfo.wasMobileTransmitting(nextTransferInfo)) {
                mobileDrainMAh += getAveragePower("radio.active") / SECONDS_PER_HOUR * scale;
            }
        } catch (Exception ignore) {
        }
        COMPONENTS_DRAIN_MAH.put("wifi", wifiDrainMAh);
        COMPONENTS_DRAIN_MAH.put("mobile", mobileDrainMAh);
        previousMeasurementMillis = System.currentTimeMillis();
    }

    double getAveragePower(String componentState) throws Exception {
        return invokePowerProfileMethod("getAveragePower", Double.class,
                new Class[]{String.class}, new Object[]{componentState});
    }

    private <T> T invokePowerProfileMethod(String methodName, Class<T> returnType, Class<?>[] argTypes, Object[] args)
            throws Exception {
        final String powerProfileClass = "com.android.internal.os.PowerProfile";

        Object powerProfile = Class.forName(powerProfileClass)
                .getConstructor(Context.class).newInstance(webView.getContext());

        return returnType.cast(Class.forName(powerProfileClass)
                .getMethod(methodName, argTypes)
                .invoke(powerProfile, args));
    }

    CpuInfo readCpuInfo() throws Exception {
        RandomAccessFile reader = new RandomAccessFile("/proc/stat", "r");
        String load = reader.readLine();
        reader.close();

        String[] split = load.split("\\s+");

        long activeTime = Long.parseLong(split[1]) + Long.parseLong(split[2]) + Long.parseLong(split[3]) +
                Long.parseLong(split[5]) + Long.parseLong(split[6]) + Long.parseLong(split[7]);
        long idleTime = Long.parseLong(split[4]);

        return new CpuInfo(activeTime, idleTime);
    }

    class CpuInfo {
        long activeTime;
        long idleTime;

        CpuInfo(long activeTime, long idleTime) {
            this.activeTime = activeTime;
            this.idleTime = idleTime;
        }
    }

    class TransferInfo {
        long wifiRxBytes;
        long wifiTxBytes;
        long mobileRxBytes;
        long mobileTxBytes;

        TransferInfo() {
            mobileRxBytes = TrafficStats.getMobileRxBytes();
            mobileTxBytes = TrafficStats.getMobileTxBytes();
            wifiRxBytes = TrafficStats.getTotalRxBytes() - mobileRxBytes;
            wifiTxBytes = TrafficStats.getTotalTxBytes() - mobileTxBytes;
        }

        boolean wasWifiReceiving(TransferInfo nextInfo) {
            return nextInfo.wifiRxBytes - wifiRxBytes > 0;
        }

        boolean wasWifiTransmitting(TransferInfo nextInfo) {
            return nextInfo.wifiTxBytes - wifiTxBytes > 0;
        }

        boolean wasMobileReceiving(TransferInfo nextInfo) {
            return nextInfo.mobileRxBytes - mobileRxBytes > 0;
        }

        boolean wasMobileTransmitting(TransferInfo nextInfo) {
            return nextInfo.mobileTxBytes - mobileTxBytes > 0;
        }
    }
}
