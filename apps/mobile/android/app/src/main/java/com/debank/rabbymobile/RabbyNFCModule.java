package com.debank.rabbymobile;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.cardemulation.CardEmulation;
import android.os.Build;
import android.util.Log;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.Arrays;

public class RabbyNFCModule extends ReactContextBaseJavaModule implements ActivityEventListener, LifecycleEventListener {
    private static final String TAG = "RabbyNFC";
    private static final String MODULE_NAME = "RabbyNFC";
    
    // AID for RabbyPay
    private static final byte[] SELECT_AID_COMMAND = {
        (byte) 0x00, // CLA
        (byte) 0xA4, // INS
        (byte) 0x04, // P1
        (byte) 0x00, // P2
        (byte) 0x08, // Length of AID
        (byte) 0xF0, (byte) 0x46, (byte) 0x52, (byte) 0x45, 
        (byte) 0x45, (byte) 0x50, (byte) 0x41, (byte) 0x59, // AID: F046524545504159
        (byte) 0x00  // Le
    };
    
    private NfcAdapter nfcAdapter;
    private Promise tagPromise;
    private boolean isListening = false;

    public RabbyNFCModule(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addActivityEventListener(this);
        reactContext.addLifecycleEventListener(this);
        nfcAdapter = NfcAdapter.getDefaultAdapter(reactContext);
    }

    @Override
    public String getName() {
        return MODULE_NAME;
    }

    @ReactMethod
    public void isSupported(Promise promise) {
        try {
            boolean supported = nfcAdapter != null;
            promise.resolve(supported);
        } catch (Exception e) {
            promise.reject("NFC_ERROR", "Failed to check NFC support", e);
        }
    }

    @ReactMethod
    public void isEnabled(Promise promise) {
        try {
            boolean enabled = nfcAdapter != null && nfcAdapter.isEnabled();
            promise.resolve(enabled);
        } catch (Exception e) {
            promise.reject("NFC_ERROR", "Failed to check NFC status", e);
        }
    }

    @ReactMethod
    public void start(Promise promise) {
        try {
            if (nfcAdapter == null) {
                promise.reject("NFC_NOT_SUPPORTED", "NFC is not supported on this device");
                return;
            }
            promise.resolve(null);
        } catch (Exception e) {
            promise.reject("NFC_ERROR", "Failed to start NFC", e);
        }
    }

    @ReactMethod
    public void startHCE(String walletAddress, Promise promise) {
        try {
            Activity activity = getCurrentActivity();
            if (activity == null) {
                promise.reject("NO_ACTIVITY", "No activity available");
                return;
            }

            if (nfcAdapter == null) {
                promise.reject("NFC_NOT_SUPPORTED", "NFC is not supported");
                return;
            }

            if (!nfcAdapter.isEnabled()) {
                promise.reject("NFC_DISABLED", "NFC is disabled");
                return;
            }
            
            // Store wallet address in shared preferences for HCE service
            Log.d(TAG, "startHCE - Received wallet address: " + walletAddress);
            Log.d(TAG, "startHCE - Address is null: " + (walletAddress == null));
            Log.d(TAG, "startHCE - Address is empty: " + (walletAddress != null && walletAddress.isEmpty()));
            
            if (walletAddress != null && !walletAddress.isEmpty()) {
                Log.d(TAG, "startHCE - Storing wallet address in SharedPreferences");
                activity.getSharedPreferences("RabbyNFC", Activity.MODE_PRIVATE)
                    .edit()
                    .putString("walletAddress", walletAddress)
                    .apply();
                Log.d(TAG, "startHCE - Stored wallet address: " + walletAddress);
                
                // Verify it was stored
                String verifyAddress = activity.getSharedPreferences("RabbyNFC", Activity.MODE_PRIVATE)
                    .getString("walletAddress", "not_found");
                Log.d(TAG, "startHCE - Verification read: " + verifyAddress);
                
                // Also send via broadcast as backup method
                Intent broadcastIntent = new Intent("com.debank.rabbymobile.WALLET_ADDRESS_UPDATE");
                broadcastIntent.putExtra("walletAddress", walletAddress);
                activity.sendBroadcast(broadcastIntent);
                Log.d(TAG, "startHCE - Sent wallet address via broadcast");
            } else {
                Log.e(TAG, "startHCE - WARNING: No wallet address provided, HCE will use default");
            }

            // Check if our HCE service is the default
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                CardEmulation cardEmulation = CardEmulation.getInstance(nfcAdapter);
                ComponentName hceService = new ComponentName(activity, RabbyHostApduService.class);
                
                // Check if we're the default service for the "other" category
                boolean isDefault = cardEmulation.isDefaultServiceForCategory(hceService, CardEmulation.CATEGORY_OTHER);
                
                if (!isDefault) {
                    // Try to set as default
                    Log.d(TAG, "Setting RabbyHostApduService as default for CATEGORY_OTHER");
                    Intent intent = new Intent(CardEmulation.ACTION_CHANGE_DEFAULT);
                    intent.putExtra(CardEmulation.EXTRA_CATEGORY, CardEmulation.CATEGORY_OTHER);
                    intent.putExtra(CardEmulation.EXTRA_SERVICE_COMPONENT, hceService);
                    activity.startActivity(intent);
                }
                
                Log.d(TAG, "HCE Service is default: " + isDefault);
            }

            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("NFC_ERROR", "Failed to start HCE", e);
        }
    }

    @ReactMethod
    public void stopHCE(Promise promise) {
        try {
            // HCE stops automatically when the app goes to background
            // or when NFC is disabled
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("NFC_ERROR", "Failed to stop HCE", e);
        }
    }
    
    @ReactMethod
    public void setWalletAddress(String walletAddress, Promise promise) {
        try {
            Activity activity = getCurrentActivity();
            if (activity == null) {
                promise.reject("NO_ACTIVITY", "No activity available");
                return;
            }
            
            Log.d(TAG, "setWalletAddress - Storing wallet address: " + walletAddress);
            
            if (walletAddress != null && !walletAddress.isEmpty()) {
                // Update the ContentProvider's static storage
                RabbyWalletProvider.setWalletAddress(walletAddress);
                
                // Store in SharedPreferences
                activity.getSharedPreferences("RabbyNFC", Activity.MODE_PRIVATE)
                    .edit()
                    .putString("walletAddress", walletAddress)
                    .apply();
                
                // Also send via broadcast
                Intent broadcastIntent = new Intent("com.debank.rabbymobile.WALLET_ADDRESS_UPDATE");
                broadcastIntent.putExtra("walletAddress", walletAddress);
                activity.sendBroadcast(broadcastIntent);
                
                Log.d(TAG, "setWalletAddress - Successfully stored and broadcast wallet address");
                promise.resolve(true);
            } else {
                promise.reject("INVALID_ADDRESS", "Wallet address cannot be empty");
            }
        } catch (Exception e) {
            promise.reject("NFC_ERROR", "Failed to set wallet address", e);
        }
    }


    private void sendEvent(String eventName, String data) {
        WritableMap params = Arguments.createMap();
        params.putString("data", data);
        
        getReactApplicationContext()
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(eventName, params);
    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {}

    @Override
    public void onNewIntent(Intent intent) {}

    @Override
    public void onHostResume() {}

    @Override
    public void onHostPause() {
        // HCE is automatically paused when the activity pauses
        isListening = false;
    }

    @Override
    public void onHostDestroy() {}
}