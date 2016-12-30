package com.example.androidthings.doorbell;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.util.Log;

import com.ardic.android.iotignite.callbacks.ConnectionCallback;
import com.ardic.android.iotignite.enumerations.NodeType;
import com.ardic.android.iotignite.enumerations.ThingCategory;
import com.ardic.android.iotignite.enumerations.ThingDataType;
import com.ardic.android.iotignite.exceptions.UnsupportedVersionException;
import com.ardic.android.iotignite.exceptions.UnsupportedVersionExceptionType;
import com.ardic.android.iotignite.listeners.NodeListener;
import com.ardic.android.iotignite.listeners.ThingListener;
import com.ardic.android.iotignite.nodes.IotIgniteManager;
import com.ardic.android.iotignite.nodes.Node;
import com.ardic.android.iotignite.things.Thing;
import com.ardic.android.iotignite.things.ThingActionData;
import com.ardic.android.iotignite.things.ThingData;
import com.ardic.android.iotignite.things.ThingType;

import java.util.Timer;
import java.util.TimerTask;


/**
 * Created by yavuz.erzurumlu on 12/28/16.
 */

public class IotIgniteHandler implements ConnectionCallback, NodeListener, ThingListener {

    private static final String TAG = "IoT Ignite Handler";
    private static final String NODE_ID = "Android Things Doorbell Companion";
    private static final String DOOR_KEY_ID = "Door Key";
    private static final String LOCK = "0";
    private static final String UNLOCK = "1";

    private IotIgniteManager mIotIgniteManager;
    private Node myNode;
    private Thing mDoorKeyThing;
    private ThingType mDoorKeyThingType = new ThingType("Key", "Smart Doorbell", ThingDataType.STRING);
    private boolean igniteConnected = false;
    private boolean versionError = false;
    private Context applicationContext;
    private static final long IGNITE_TIMER_PERIOD = 5000L;
    private Timer igniteTimer = new Timer();
    private IgniteWatchDog igniteWatchDog = new IgniteWatchDog();

    private Bitmap lastTakenImage;

    private static class InstanceHolder {
        private static IotIgniteHandler INSTANCE = null;

        private InstanceHolder() {
        }

        public static IotIgniteHandler getInstance(Context appContext) {
            if (INSTANCE == null) {
                INSTANCE = new IotIgniteHandler(appContext);
            }
            return INSTANCE;
        }
    }

    public static IotIgniteHandler getInstance(Context appContext) {
        return InstanceHolder.getInstance(appContext);
    }

    @Override
    public void onNodeUnregistered(String nodeId) {
        /**
         * When node has unregistered. Info message arrives here.
         */

        Log.i(TAG, nodeId + " has unregistered.");
    }

    @Override
    public void onConfigurationReceived(Thing thing) {
        /**
         * Configuration messages arrive here.
         * We use same listener for two things.
         * Handle different thing configurations in same callback.
         */

        if (DOOR_KEY_ID.equals(thing.getThingID())) {
            Log.i(TAG, "Config received for " + DOOR_KEY_ID + "Read Freq : " + mDoorKeyThing.getThingConfiguration().getDataReadingFrequency());
        }
    }

    @Override
    public void onActionReceived(String nodeId, String thingId, ThingActionData thingActionData) {
        /**
         * Handle action messages here.
         * If created thing's set as an actuator it's action messages arrives here when happened.
         * In this case we have only one actuator as ALARM.
         */
    }

    @Override
    public void onThingUnregistered(String nodeId, String thingId) {

        /**
         * When thing has unregistered. Info message arrives here.
         */

        Log.i(TAG, thingId + " has unregistered.");

    }

    // Handle ignite connection with timer task
    private class IgniteWatchDog extends TimerTask {
        @Override
        public void run() {
            if (!igniteConnected && !versionError) {
                Log.i(TAG, "Rebuild Ignite...");
                start();
            }
        }
    }

    public IotIgniteHandler(Context appContext) {
        this.applicationContext = appContext;
    }

    public void start() {
        // Build Ignite Manager
        try {
            mIotIgniteManager = new IotIgniteManager.Builder()
                    .setContext(this.applicationContext)
                    .setConnectionListener(this)
                    .build();
        } catch (UnsupportedVersionException e) {
            Log.e(TAG, e.toString());
            versionError = true;
            if (UnsupportedVersionExceptionType.UNSUPPORTED_IOTIGNITE_AGENT_VERSION.toString().equals(e.getMessage())) {
                Log.e(TAG, "UNSUPPORTED_IOTIGNITE_AGENT_VERSION");
            } else {
                Log.e(TAG, "UNSUPPORTED_IOTIGNITE_SDK_VERSION");
            }
        }
        cancelAndScheduleIgniteTimer();
    }

    @Override
    public void onConnected() {
        Log.i(TAG, "Ignite Connected!");
        igniteConnected = true;
        initIgniteVariables();
        cancelAndScheduleIgniteTimer();
    }

    @Override
    public void onDisconnected() {
        Log.i(TAG, "Ignite Disconnected!");
        igniteConnected = false;
        cancelAndScheduleIgniteTimer();
    }

    /**
     * Create node and thing instances. Register them to IotIgnite.
     */
    private void initIgniteVariables() {

        myNode = IotIgniteManager.NodeFactory.createNode(NODE_ID, NODE_ID, NodeType.RASPBERRY_PI_3, null, this);

        // Register node if not registered and set connection.
        if (!myNode.isRegistered() && myNode.register()) {
            myNode.setConnected(true, myNode.getNodeID() + " is online");
            Log.d(TAG, myNode.getNodeID() + " is successfully registered!");
        } else {
            myNode.setConnected(true, myNode.getNodeID() + " is online");
            Log.d(TAG, myNode.getNodeID() + " is already registered!");
        }
        if (myNode.isRegistered()) {
            mDoorKeyThing = myNode.createThing(DOOR_KEY_ID, mDoorKeyThingType, ThingCategory.EXTERNAL, false, this, null);
            registerThingIfNotRegistered(mDoorKeyThing);
        }
    }

    private void registerThingIfNotRegistered(Thing t) {
        if (!t.isRegistered() && t.register()) {
            t.setConnected(true, t.getThingID() + " connected");
            Log.i(TAG, t.getThingID() + " is successfully registered!");
        } else {
            t.setConnected(true, t.getThingID() + " connected");
            Log.i(TAG, t.getThingID() + " is already registered!");
        }
    }

    private void cancelAndScheduleIgniteTimer() {
        igniteTimer.cancel();
        igniteWatchDog.cancel();
        igniteWatchDog = new IgniteWatchDog();
        igniteTimer = new Timer();
        igniteTimer.schedule(igniteWatchDog, IGNITE_TIMER_PERIOD);
    }

    public void sendData(boolean unlock) {
        if (igniteConnected) {
            ThingData data = new ThingData();
            if(unlock){
                data.addData(UNLOCK);
            }else{
                data.addData(LOCK);
            }

            if (mDoorKeyThing.sendData(data)) {
                Log.i(TAG, "DATA SENT SUCCESSFULLY");
            } else {
                Log.e(TAG, "DATA SENT FAILURE");
            }
        }
    }

    public void startDoorLockActivity(){
        Log.d(TAG,"Starting doorlock activity...");
        Intent intent = new Intent(applicationContext, DoorLockActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        applicationContext.startActivity(intent);
    }


    public Bitmap getLastTakenImage() {
        return lastTakenImage;
    }

    public void setLastTakenImage(Bitmap lastTakenImage) {
        this.lastTakenImage = lastTakenImage;
    }


}
