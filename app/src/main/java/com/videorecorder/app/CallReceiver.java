package com.videorecorder.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * Broadcast Receiver for Incoming Calls
 * 
 * This receiver detects incoming phone calls and handles recording interruption.
 * 
 * Why this is needed:
 * - Android system behavior may interrupt recording during calls
 * - Proper handling ensures graceful recording termination
 * - Prevents corrupted video files
 * - Provides better user experience
 */
public class CallReceiver extends BroadcastReceiver {
    
    private static final String TAG = "CallReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        
        if (intent.getAction().equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            
            if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                Log.d(TAG, "Incoming call detected");
                handleIncomingCall(context);
            } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                Log.d(TAG, "Call ended");
                handleCallEnded(context);
            }
        }
    }
    
    /**
     * Handle incoming call
     */
    private void handleIncomingCall(Context context) {
        // Stop recording if it's active
        if (RecordingForegroundService.isRecordingActive()) {
            Log.d(TAG, "Stopping recording due to incoming call");
            Intent serviceIntent = new Intent(context, RecordingForegroundService.class);
            serviceIntent.setAction(RecordingForegroundService.ACTION_STOP_RECORDING);
            context.startService(serviceIntent);
            
            // Handle the error through ErrorHandler
            ErrorHandler.handleIncomingCall(context, new RecordingController.RecordingCallback() {
                @Override
                public void onRecordingStarted() {}
                
                @Override
                public void onRecordingStopped(String filePath) {}
                
                @Override
                public void onRecordingError(String error) {
                    // Log the error for debugging
                    Log.d(TAG, "Recording stopped due to incoming call: " + error);
                }
                
                @Override
                public void onDurationUpdate(long durationMs) {}
            });
        }
    }
    
    /**
     * Handle call ended
     */
    private void handleCallEnded(Context context) {
        Log.d(TAG, "Call ended, recording can be resumed by user");
        // User can manually restart recording after call ends
    }
}
