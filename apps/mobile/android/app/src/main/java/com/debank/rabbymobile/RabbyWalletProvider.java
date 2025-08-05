package com.debank.rabbymobile;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;

public class RabbyWalletProvider extends ContentProvider {
    private static final String TAG = "RabbyWalletProvider";
    // Note: In debug builds, the authority includes ".debug" suffix
    private static final String AUTHORITY = "com.debank.rabbymobile.walletprovider";
    
    // Store the current wallet address statically so it can be accessed from anywhere
    private static String currentWalletAddress = null;
    
    public static void setWalletAddress(String address) {
        currentWalletAddress = address;
        Log.d(TAG, "Wallet address updated: " + address);
    }

    @Override
    public boolean onCreate() {
        Log.d(TAG, "ContentProvider created");
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Log.d(TAG, "Query received for URI: " + uri);
        
        if (uri.getPath().equals("/wallet_address")) {
            MatrixCursor cursor = new MatrixCursor(new String[]{"address"});
            
            // If we have a cached address, return it
            if (currentWalletAddress != null && !currentWalletAddress.isEmpty()) {
                cursor.addRow(new Object[]{currentWalletAddress});
                Log.d(TAG, "Returning cached address: " + currentWalletAddress);
            } else {
                // Return null address
                cursor.addRow(new Object[]{"eip155:1:0x0000000000000000000000000000000000000000"});
                Log.d(TAG, "No wallet address available, returning default");
            }
            
            return cursor;
        }
        
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "text/plain";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}