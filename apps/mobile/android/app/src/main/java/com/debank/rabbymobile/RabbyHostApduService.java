package com.debank.rabbymobile;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.util.Log;
import android.content.SharedPreferences;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;

import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.Arrays;

public class RabbyHostApduService extends HostApduService {
    private static final String TAG = "RabbyHCE";
    private static final String CHANNEL_ID = "RabbyNFC";
    private static final int NOTIFICATION_ID = 9999;
    
    // AID for Rabby
    private static final String SELECT_AID = "00A40400";
    private static final String AID = "F05241424259";
    
    // Status words
    private static final byte[] SW_SUCCESS = {(byte) 0x90, 0x00};
    private static final byte[] SW_FILE_NOT_FOUND = {(byte) 0x6A, (byte) 0x82};
    private static final byte[] SW_COMMAND_NOT_SUPPORTED = {(byte) 0x6D, 0x00};
    private static final byte[] SW_WRONG_DATA = {(byte) 0x6A, (byte) 0x80};
    
    // PAYMENT command prefix -> 80 CF 00 00 (4 bytes)
    private static final byte[] PAYMENT_CMD_PREFIX = {(byte) 0x80, (byte) 0xCF, (byte) 0x00, (byte) 0x00};
    
    private boolean aidSelected = false;
    private boolean walletAddressSent = false;
    private NotificationManager notificationManager;
    private String currentWalletAddress = null;
    
    private BroadcastReceiver walletAddressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.debank.rabbymobile.WALLET_ADDRESS_UPDATE".equals(intent.getAction())) {
                String address = intent.getStringExtra("walletAddress");
                if (address != null && !address.isEmpty()) {
                    currentWalletAddress = address;
                    Log.d(TAG, "Received wallet address via broadcast: " + address);
                    // showDebugNotification("Address Updated", "Via broadcast: " + address.substring(0, 20) + "...");
                    
                    // Also store in SharedPreferences as backup
                    getSharedPreferences("RabbyNFC", MODE_PRIVATE)
                        .edit()
                        .putString("walletAddress", address)
                        .apply();
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "RabbyHostApduService onCreate - Service created");
        Log.d(TAG, "RabbyHostApduService onCreate - AID: " + AID);
        
        // Initialize notification manager
        // TEMPORARILY DISABLED to prevent crashes
        // notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // createNotificationChannel();
        
        // Test SharedPreferences access with a test value first
        SharedPreferences prefs = getSharedPreferences("RabbyNFC", MODE_PRIVATE);
        prefs.edit().putString("testKey", "testValue-" + System.currentTimeMillis()).apply();
        String testValue = prefs.getString("testKey", "notFound");
        Log.d(TAG, "SharedPreferences test - wrote and read: " + testValue);
        
        // Log current stored address when service starts
        String storedAddress = prefs.getString("walletAddress", "none");
        Log.d(TAG, "RabbyHostApduService onCreate - Current stored address: " + storedAddress);
        
        // Also try getting from Context.MODE_MULTI_PROCESS (deprecated but might help)
        try {
            // Try accessing SharedPreferences with different modes
            SharedPreferences prefs2 = getApplicationContext().getSharedPreferences("RabbyNFC", MODE_PRIVATE);
            String address2 = prefs2.getString("walletAddress", "none-from-app-context");
            Log.d(TAG, "From application context: " + address2);
        } catch (Exception e) {
            Log.e(TAG, "Error accessing SharedPreferences from app context", e);
        }
        
        // Show notification for debugging
        // TEMPORARILY DISABLED
        // showDebugNotification("HCE Service Started", "Stored address: " + storedAddress);
        
        // Register broadcast receiver for wallet address updates
        IntentFilter filter = new IntentFilter("com.debank.rabbymobile.WALLET_ADDRESS_UPDATE");
        if (Build.VERSION.SDK_INT >= 33) {
            // For Android 13+ (API 33+), specify RECEIVER_NOT_EXPORTED since this is internal
            ContextCompat.registerReceiver(this, walletAddressReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(walletAddressReceiver, filter);
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(walletAddressReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver", e);
        }
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Rabby NFC Debug",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Debug notifications for NFC operations");
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    private void showDebugNotification(String title, String text) {
        try {
            Intent intent = new Intent();
            intent.setClassName(getPackageName(), "com.debank.rabbymobile.MainActivity");
            PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
                
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        } catch (Exception e) {
            Log.e(TAG, "Failed to show notification: " + title + " - " + text, e);
        }
    }
    
    private String getWalletAddress() {
        Log.d(TAG, "getWalletAddress - Starting address retrieval");
        
        // First try the broadcast receiver value
        if (currentWalletAddress != null && !currentWalletAddress.isEmpty()) {
            Log.d(TAG, "getWalletAddress - Using broadcast receiver address: " + currentWalletAddress);
            return currentWalletAddress;
        }
        
        // Try to get from ContentProvider
        try {
            // Use the package name to build the correct authority (handles debug/release)
            String authority = getPackageName() + ".walletprovider";
            Uri uri = Uri.parse("content://" + authority + "/wallet_address");
            Log.d(TAG, "Querying ContentProvider at: " + uri);
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        String address = cursor.getString(cursor.getColumnIndex("address"));
                        if (address != null && !address.isEmpty() && !address.contains("0x0000")) {
                            Log.d(TAG, "getWalletAddress - Got address from ContentProvider: " + address);
                            currentWalletAddress = address; // Cache it
                            return address;
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying ContentProvider", e);
        }
        
        // Try to read MMKV files directly (since we can't use the MMKV library)
        try {
            // MMKV stores data in files, let's try to find and read them
            java.io.File mmkvDir = new java.io.File(getApplicationInfo().dataDir + "/files/mmkv");
            if (mmkvDir.exists()) {
                Log.d(TAG, "getWalletAddress - MMKV directory exists");
                String[] mmkvFiles = mmkvDir.list();
                Log.d(TAG, "getWalletAddress - MMKV files: " + Arrays.toString(mmkvFiles));
                
                // The preference store would be in a file named "preference" or "mmkv.preference"
                // But MMKV files are memory-mapped and encrypted, we can't read them directly
                // We need to rely on other methods
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking MMKV storage", e);
        }
        
        // Try main app SharedPreferences
        try {
            // Try the main app's SharedPreferences
            SharedPreferences mainPrefs = getApplicationContext().getSharedPreferences("rabby", MODE_PRIVATE);
            String currentAccount = mainPrefs.getString("currentAccount", null);
            
            Log.d(TAG, "getWalletAddress - Main app prefs currentAccount: " + (currentAccount != null ? currentAccount.substring(0, Math.min(currentAccount.length(), 100)) + "..." : "null"));
            
            if (currentAccount != null && currentAccount.contains("address")) {
                // Parse the JSON to extract the address
                int addressStart = currentAccount.indexOf("\"address\":\"");
                if (addressStart != -1) {
                    addressStart += 11;
                    int addressEnd = currentAccount.indexOf("\"", addressStart);
                    if (addressEnd != -1) {
                        String extractedAddress = currentAccount.substring(addressStart, addressEnd);
                        if (extractedAddress.startsWith("0x") && extractedAddress.length() == 42) {
                            String fullAddress = "eip155:1:" + extractedAddress;
                            Log.d(TAG, "getWalletAddress - Extracted address from main prefs: " + fullAddress);
                            
                            // Cache it for future use
                            currentWalletAddress = fullAddress;
                            return fullAddress;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading main app preferences", e);
        }
        
        // Then try our NFC-specific SharedPreferences
        Log.d(TAG, "getWalletAddress - Reading from RabbyNFC SharedPreferences");
        SharedPreferences prefs = getSharedPreferences("RabbyNFC", MODE_PRIVATE);
        String address = prefs.getString("walletAddress", null);
        
        Log.d(TAG, "getWalletAddress - Raw value from SharedPreferences: " + address);
        
        // If no address is stored, return default
        if (address == null || address.isEmpty()) {
            Log.e(TAG, "getWalletAddress - No wallet address found in any storage!");
            
            // List all SharedPreferences files to help debug
            try {
                String[] prefsFiles = new java.io.File(getApplicationInfo().dataDir + "/shared_prefs").list();
                Log.d(TAG, "Available SharedPreferences files: " + Arrays.toString(prefsFiles));
            } catch (Exception e) {
                Log.e(TAG, "Error listing SharedPreferences files", e);
            }
            
            address = "eip155:1:0x0000000000000000000000000000000000000000";
            Log.e(TAG, "getWalletAddress - Using default address: " + address);
        } else {
            Log.d(TAG, "getWalletAddress - Successfully retrieved address: " + address);
        }
        
        return address;
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
                walletAddressSent = false; // Reset state
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

        // Handle PAYMENT command (80CF0000 + NDEFLength + NDEF data)
        if (commandApdu.length >= PAYMENT_CMD_PREFIX.length &&
            Arrays.equals(Arrays.copyOfRange(commandApdu, 0, PAYMENT_CMD_PREFIX.length), PAYMENT_CMD_PREFIX)) {
            Log.d(TAG, "Handling PAYMENT command");
            return handlePaymentCommand(commandApdu);
        }
        
        // Handle wallet:address as raw command
        if (commandApdu.length >= 5) {
            try {
                // Check if this is a simple text command (00 00 00 00 + length + data)
                if (commandApdu[0] == 0x00 && commandApdu[1] == 0x00 && 
                    commandApdu[2] == 0x00 && commandApdu[3] == 0x00) {
                    int length = commandApdu[4] & 0xFF;
                    if (commandApdu.length >= 5 + length) {
                        String data = new String(Arrays.copyOfRange(commandApdu, 5, 5 + length), "UTF-8");
                        Log.d(TAG, "Received raw text command: " + data);
                        
                        if (data.equals("wallet:address")) {
                            Log.i(TAG, "=============== WALLET ADDRESS REQUEST RECEIVED ===============");
                            // showDebugNotification("Wallet Address Request", "Received wallet:address command");
                            
                            String walletAddress = getWalletAddress();
                            Log.i(TAG, "Wallet address to send: " + walletAddress);
                            
                            byte[] addressBytes = walletAddress.getBytes("UTF-8");
                            Log.i(TAG, "Address bytes length: " + addressBytes.length);
                            Log.i(TAG, "Address bytes hex: " + bytesToHex(addressBytes));
                            
                            sendEventToJS("walletAddressSent", walletAddress);
                            walletAddressSent = true;
                            
                            Log.i(TAG, "=============== RETURNING WALLET ADDRESS ===============");
                            // showDebugNotification("Address Sent", walletAddress.substring(0, 30) + "...");
                            
                            // Return just the address bytes without status bytes
                            return addressBytes;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling raw command", e);
            }
        }

        // Handle other commands (wallet:address)
        try {
            String command = parseCommand(commandApdu);
            
            if (command != null && command.contains("wallet:address")) {
                Log.i(TAG, "=============== WALLET ADDRESS COMMAND (parseCommand) ===============");
                if (!walletAddressSent) {
                    String walletAddress = getWalletAddress();
                    Log.i(TAG, "Wallet address to send: " + walletAddress);
                    
                    byte[] addressBytes = walletAddress.getBytes("UTF-8");
                    Log.i(TAG, "Address bytes length: " + addressBytes.length);
                    
                    sendEventToJS("walletAddressSent", walletAddress);
                    walletAddressSent = true;
                    
                    Log.i(TAG, "=============== RETURNING WALLET ADDRESS (parseCommand) ===============");
                    // Return just the address bytes without status bytes
                    return addressBytes;
                } else {
                    Log.w(TAG, "Wallet address already sent - rejecting duplicate request");
                    return SW_COMMAND_NOT_SUPPORTED;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing command", e);
        }

        Log.d(TAG, "Unknown command, returning SW_COMMAND_NOT_SUPPORTED");
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
                Log.d(TAG, "Sending event to JS: " + eventName + " with data: " + data);
                reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit(eventName, data);
            } else {
                Log.e(TAG, "React context not available for event: " + eventName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send event to JS: " + eventName, e);
        }
    }

    private String parseCommand(byte[] commandApdu) {
        try {
            // Skip the CLA, INS, P1, P2 bytes (first 4 bytes)
            if (commandApdu.length <= 4) {
                return null;
            }

            // Check if there's a length byte (Lc)
            int dataStartIndex = 4;
            int dataLength = commandApdu.length - dataStartIndex;

            // If there's a length byte at position 4, skip it
            if (commandApdu.length > 5) {
                int lc = commandApdu[4] & 0xFF;
                if (lc > 0 && lc <= (commandApdu.length - 5)) {
                    dataStartIndex = 5;
                    dataLength = lc;
                }
            }

            // Extract the data portion
            byte[] dataBytes = Arrays.copyOfRange(commandApdu, dataStartIndex, dataStartIndex + dataLength);
            String command = new String(dataBytes, "UTF-8").trim();
            
            Log.d(TAG, "Parsed command: " + command);
            return command;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing command", e);
            return null;
        }
    }

    private byte[] handlePaymentCommand(byte[] commandApdu) {
        try {
            // Log the raw command for debugging
            Log.d(TAG, "Raw PAYMENT command: " + bytesToHex(commandApdu));
            
            // The payment terminal might be sending wallet:address in the PAYMENT command
            // Let's check what's in the NDEF data first
            if (commandApdu.length >= 5) {
                int ndefLength = commandApdu[4] & 0xFF;
                if (commandApdu.length >= 5 + ndefLength) {
                    byte[] ndefData = Arrays.copyOfRange(commandApdu, 5, 5 + ndefLength);
                    
                    // Try to parse the NDEF data
                    String paymentData = null;
                    try {
                        paymentData = parseNdefUri(ndefData);
                    } catch (Exception e) {
                        // If NDEF parsing fails, try as plain text
                        paymentData = new String(ndefData, "UTF-8").trim();
                    }
                    
                    Log.d(TAG, "Payment data: " + paymentData);
                    
                    // If this is a wallet:address request, respond with the address
                    if (paymentData != null && paymentData.equals("wallet:address")) {
                        String walletAddress = getWalletAddress();
                        byte[] addressBytes = walletAddress.getBytes("UTF-8");
                        
                        Log.d(TAG, "PAYMENT command contains wallet:address request");
                        Log.d(TAG, "Sending wallet address: " + walletAddress);
                        sendEventToJS("walletAddressSent", walletAddress);
                        walletAddressSent = true;
                        
                        // Return just the address bytes without status bytes
                        return addressBytes;
                    }
                }
            }
            
            // Check if wallet address was sent
            if (!walletAddressSent) {
                Log.d(TAG, "Payment command received before wallet address was sent");
                return SW_COMMAND_NOT_SUPPORTED;
            }

            // Extract NDEF data
            // Format: 80CF0000 + Length + NDEF data
            if (commandApdu.length < 5) {
                Log.d(TAG, "Payment command too short: " + commandApdu.length);
                return SW_WRONG_DATA;
            }

            int ndefLength = commandApdu[4] & 0xFF;
            Log.d(TAG, "NDEF length: " + ndefLength);
            
            if (commandApdu.length < 5 + ndefLength) {
                Log.d(TAG, "Payment command length mismatch. Expected: " + (5 + ndefLength) + ", Got: " + commandApdu.length);
                return SW_WRONG_DATA;
            }

            // Extract NDEF payload
            byte[] ndefData = Arrays.copyOfRange(commandApdu, 5, 5 + ndefLength);
            Log.d(TAG, "NDEF data: " + bytesToHex(ndefData));
            
            // Try to parse as plain text first (in case it's not NDEF formatted)
            String paymentUri = null;
            try {
                // First try parsing as NDEF
                paymentUri = parseNdefUri(ndefData);
            } catch (Exception e) {
                Log.d(TAG, "Failed to parse as NDEF, trying as plain text");
                // If NDEF parsing fails, try as plain text
                paymentUri = new String(ndefData, "UTF-8").trim();
            }
            
            if (paymentUri == null || paymentUri.isEmpty()) {
                Log.d(TAG, "Failed to extract payment URI");
                return SW_WRONG_DATA;
            }

            Log.d(TAG, "Received payment URI: " + paymentUri);
            
            // Send payment URI to JavaScript layer
            sendEventToJS("paymentRequest", paymentUri);
            
            // Return success
            return SW_SUCCESS;
        } catch (Exception e) {
            Log.e(TAG, "Error handling payment command", e);
            return SW_WRONG_DATA;
        }
    }

    private String parseNdefUri(byte[] ndefData) {
        try {
            // Basic NDEF parsing for URI records
            // This is a simplified implementation - a full NDEF parser would be more complex
            
            // Check for NDEF message format
            if (ndefData.length < 3) {
                return null;
            }

            int index = 0;
            
            // Parse NDEF record header
            byte header = ndefData[index++];
            boolean mb = (header & 0x80) != 0; // Message Begin
            boolean me = (header & 0x40) != 0; // Message End
            boolean cf = (header & 0x20) != 0; // Chunk Flag
            boolean sr = (header & 0x10) != 0; // Short Record
            boolean il = (header & 0x08) != 0; // ID Length present
            int tnf = header & 0x07; // Type Name Format

            // Check if this is a URI record (TNF = 0x01)
            if (tnf != 0x01) {
                Log.d(TAG, "Not a URI record, TNF: " + tnf);
                return null;
            }

            // Type length
            int typeLength = ndefData[index++] & 0xFF;
            
            // Payload length (short record format)
            int payloadLength;
            if (sr) {
                payloadLength = ndefData[index++] & 0xFF;
            } else {
                // Long record format (4 bytes)
                payloadLength = ((ndefData[index++] & 0xFF) << 24) |
                               ((ndefData[index++] & 0xFF) << 16) |
                               ((ndefData[index++] & 0xFF) << 8) |
                               (ndefData[index++] & 0xFF);
            }

            // ID length (if present)
            int idLength = 0;
            if (il) {
                idLength = ndefData[index++] & 0xFF;
            }

            // Skip type field
            index += typeLength;

            // Skip ID field (if present)
            if (idLength > 0) {
                index += idLength;
            }

            // Extract payload
            if (index + payloadLength > ndefData.length) {
                Log.d(TAG, "Payload length exceeds data length");
                return null;
            }

            byte[] payload = Arrays.copyOfRange(ndefData, index, index + payloadLength);

            // For URI records, first byte is the URI prefix code
            if (payload.length < 2) {
                return null;
            }

            byte uriPrefix = payload[0];
            String prefix = getUriPrefix(uriPrefix);
            
            // Get the rest of the URI
            String uriSuffix = new String(Arrays.copyOfRange(payload, 1, payload.length), "UTF-8");
            
            return prefix + uriSuffix;
        } catch (Exception e) {
            Log.e(TAG, "Error parsing NDEF URI", e);
            return null;
        }
    }

    private String getUriPrefix(byte prefixByte) {
        // NDEF URI prefix codes
        switch (prefixByte) {
            case 0x00: return "";
            case 0x01: return "http://www.";
            case 0x02: return "https://www.";
            case 0x03: return "http://";
            case 0x04: return "https://";
            case 0x05: return "tel:";
            case 0x06: return "mailto:";
            case 0x07: return "ftp://anonymous:anonymous@";
            case 0x08: return "ftp://ftp.";
            case 0x09: return "ftps://";
            case 0x0A: return "sftp://";
            case 0x0B: return "smb://";
            case 0x0C: return "nfs://";
            case 0x0D: return "ftp://";
            case 0x0E: return "dav://";
            case 0x0F: return "news:";
            case 0x10: return "telnet://";
            case 0x11: return "imap:";
            case 0x12: return "rtsp://";
            case 0x13: return "urn:";
            case 0x14: return "pop:";
            case 0x15: return "sip:";
            case 0x16: return "sips:";
            case 0x17: return "tftp:";
            case 0x18: return "btspp://";
            case 0x19: return "btl2cap://";
            case 0x1A: return "btgoep://";
            case 0x1B: return "tcpobex://";
            case 0x1C: return "irdaobex://";
            case 0x1D: return "file://";
            case 0x1E: return "urn:epc:id:";
            case 0x1F: return "urn:epc:tag:";
            case 0x20: return "urn:epc:pat:";
            case 0x21: return "urn:epc:raw:";
            case 0x22: return "urn:epc:";
            case 0x23: return "urn:nfc:";
            default: return "";
        }
    }
}