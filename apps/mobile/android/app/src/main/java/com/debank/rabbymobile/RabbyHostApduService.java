package com.debank.rabbymobile;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;

import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.Arrays;

public class RabbyHostApduService extends HostApduService {
    private static final String TAG = "RabbyHCE";
    
    // AID for RabbyPay
    private static final String SELECT_AID = "00A40400";
    private static final String AID = "F046524545504159";
    
    // Status words
    private static final byte[] SW_SUCCESS = {(byte) 0x90, 0x00};
    private static final byte[] SW_FILE_NOT_FOUND = {(byte) 0x6A, (byte) 0x82};
    private static final byte[] SW_COMMAND_NOT_SUPPORTED = {(byte) 0x6D, 0x00};
    
    private boolean aidSelected = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "RabbyHostApduService created - AID: " + AID);
    }

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        Log.d(TAG, "processCommandApdu called");
        
        if (commandApdu == null || commandApdu.length < 4) {
            Log.d(TAG, "Invalid APDU length");
            return SW_COMMAND_NOT_SUPPORTED;
        }

        String hexCommand = bytesToHex(commandApdu);
        Log.d(TAG, "Received APDU: " + hexCommand);

        // Check if this is a SELECT AID command
        if (hexCommand.toUpperCase().startsWith(SELECT_AID)) {
            String receivedAid = extractAidFromSelectCommand(commandApdu);
            if (AID.equalsIgnoreCase(receivedAid)) {
                aidSelected = true;
                Log.d(TAG, "AID selected successfully");
                sendEventToJS("nfcConnected", "AID selected");
                return SW_SUCCESS;
            } else {
                aidSelected = false;
                Log.d(TAG, "Wrong AID: " + receivedAid);
                return SW_FILE_NOT_FOUND;
            }
        }

        // If AID is not selected, reject other commands
        if (!aidSelected) {
            return SW_FILE_NOT_FOUND;
        }

        // Process other commands after AID selection
        try {
            // Log full APDU for debugging
            Log.d(TAG, "Processing non-SELECT APDU: " + hexCommand);
            
            // Try different parsing strategies
            String command = null;
            
            // Strategy 1: Parse as standard APDU with Lc byte
            if (commandApdu.length > 5) {
                int lc = commandApdu[4] & 0xFF;
                if (commandApdu.length >= 5 + lc) {
                    byte[] data = Arrays.copyOfRange(commandApdu, 5, 5 + lc);
                    command = new String(data, "UTF-8").trim();
                    Log.d(TAG, "Strategy 1 - Parsed with Lc: '" + command + "'");
                }
            }
            
            // Strategy 2: Parse without Lc byte (raw data after header)
            if (command == null && commandApdu.length > 4) {
                byte[] data = Arrays.copyOfRange(commandApdu, 4, commandApdu.length);
                String rawCommand = new String(data, "UTF-8").trim();
                Log.d(TAG, "Strategy 2 - Parsed without Lc: '" + rawCommand + "'");
                if (rawCommand.contains("wallet:address")) {
                    command = rawCommand;
                }
            }
            
            // Strategy 3: Look for wallet:address anywhere in the APDU
            String fullApduString = new String(commandApdu, "UTF-8");
            Log.d(TAG, "Strategy 3 - Full APDU as string: '" + fullApduString + "'");
            if (fullApduString.contains("wallet:address")) {
                command = "wallet:address";
                Log.d(TAG, "Found wallet:address in full APDU");
            }
            
            if (command != null) {
                Log.d(TAG, "Final parsed command: '" + command + "'");
                
                if (command.contains("wallet:address")) {
                    String walletAddress = "eip155:1:0x3D3f9852310C5B360737Af841FBb61316534db23";
                    byte[] addressBytes = walletAddress.getBytes("UTF-8");
                    
                    Log.d(TAG, "Sending wallet address: " + walletAddress);
                    Log.d(TAG, "Response length: " + addressBytes.length + ", hex: " + bytesToHex(addressBytes));
                    sendEventToJS("nfcSuccess", walletAddress);
                    
                    // Return just the address bytes without status bytes
                    return addressBytes;
                }
            } else {
                Log.e(TAG, "No valid command found in APDU");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing command", e);
        }

        Log.d(TAG, "Returning SW_COMMAND_NOT_SUPPORTED");
        return SW_COMMAND_NOT_SUPPORTED;
    }

    @Override
    public void onDeactivated(int reason) {
        Log.d(TAG, "HCE deactivated: " + reason);
        aidSelected = false;
        sendEventToJS("nfcDisconnected", "Deactivated: " + reason);
    }

    private String extractAidFromSelectCommand(byte[] apdu) {
        if (apdu.length < 5) return "";
        
        int aidLength = apdu[4] & 0xFF;
        if (apdu.length < 5 + aidLength) return "";
        
        byte[] aidBytes = Arrays.copyOfRange(apdu, 5, 5 + aidLength);
        return bytesToHex(aidBytes);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private void sendEventToJS(String eventName, String data) {
        try {
            ReactApplication reactApplication = (ReactApplication) getApplication();
            ReactInstanceManager reactInstanceManager = reactApplication.getReactNativeHost().getReactInstanceManager();
            ReactContext reactContext = reactInstanceManager.getCurrentReactContext();
            
            if (reactContext != null && reactContext.hasActiveCatalystInstance()) {
                reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit(eventName, data);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send event to JS", e);
        }
    }
}