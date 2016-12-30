package com.example.androidthings.doorbell;

import android.content.Context;
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
import com.example.androidthings.doorbell.listener.DoorActionListener;
import com.google.gson.JsonObject;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Timer;
import java.util.TimerTask;


/**
 * Created by yavuz.erzurumlu on 12/28/16.
 */

public class IotIgniteHandler implements ConnectionCallback, NodeListener, ThingListener {

    private static final String TAG = "IoT Ignite Handler";
    private static final String NODE_ID = "Android Things Doorbell";
    private static final String DOORBELL_ID = "Door Bell";
    private static final String ALARM_ID = "Alarm Buzzer";
    private static final String doorbellData = "RINGING";
    private static final String LOCK = "0";
    private static final String UNLOCK = "1";

    private Node myNode;
    private Thing mDoorBellButtonThing, mAlarmThing;
    private ThingType mDoorBellThingType = new ThingType("Button", "Smart Doorbell", ThingDataType.STRING);
    private ThingType mAlarmThingType = new ThingType("Buzzer", "Buzzer Alarm", ThingDataType.STRING);
    private boolean igniteConnected = false;
    private boolean versionError = false;
    private Context applicationContext;
    private static final long IGNITE_TIMER_PERIOD = 5000L;
    private Timer igniteTimer = new Timer();
    private IgniteWatchDog igniteWatchDog = new IgniteWatchDog();

    private DoorActionListener mDoorActionListener;

    private IotIgniteHandler(Context appContext) {
        this.applicationContext = appContext;
    }

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

        if (DOORBELL_ID.equals(thing.getThingID())) {
            Log.i(TAG, "Config received for " + DOORBELL_ID + "Read Freq : " + mDoorBellButtonThing.getThingConfiguration().getDataReadingFrequency());
        } else if (ALARM_ID.equals(thing.getThingID())) {
            Log.i(TAG, "Config received for " + ALARM_ID + "Read Freq : " + mAlarmThing.getThingConfiguration().getDataReadingFrequency());
        }
    }

    @Override
    public void onActionReceived(String nodeId, String thingId, ThingActionData thingActionData) {
        /**
         * Handle action messages here.
         * If created thing's set as an actuator it's action messages arrives here when happened.
         * In this case we have only one actuator as ALARM.
         */

        Log.i(TAG, "Action received for " + thingId);
        if (ALARM_ID.equals(thingId)) {
            String state;

            try {
                JSONObject action = new JSONObject(thingActionData.getMessage());
                state = action.getString("state");

                if (LOCK.equals(state)) {
                    // KEEP DOOR LOCKED
                    if (mDoorActionListener != null) {
                        mDoorActionListener.onActionReceived(false);
                        Log.i(TAG, "Keeping door locked.");
                    }
                } else if (UNLOCK.equals(state)) {
                    // UNLOCK DOOR

                    if (mDoorActionListener != null) {
                        mDoorActionListener.onActionReceived(true);
                        Log.i(TAG, "Unlocking door...");
                    }
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }


        }
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


    public void start() {
        // Build Ignite Manager
        try {
            new IotIgniteManager.Builder()
                    .setContext(this.applicationContext)
                    .setConnectionListener(this)
                    .build();
        } catch (UnsupportedVersionException e) {
            Log.e(TAG, e.getMessage());
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
            mDoorBellButtonThing = myNode.createThing(DOORBELL_ID, mDoorBellThingType, ThingCategory.EXTERNAL, false, this, null);
            mAlarmThing = myNode.createThing(ALARM_ID, mAlarmThingType, ThingCategory.EXTERNAL, true, this, null);
            registerThingIfNotRegistered(mDoorBellButtonThing);
            registerThingIfNotRegistered(mAlarmThing);
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

    public void sendData() {
        if (igniteConnected) {
            ThingData data = new ThingData();
            data.addData(doorbellData);
            if (mDoorBellButtonThing.sendData(data)) {
                Log.i(TAG, "DATA SENT SUCCESSFULLY");
            } else {
                Log.e(TAG, "DATA SENT FAILURE");
            }
        }
    }

    public void setDoorActionListener(DoorActionListener mDoorActionListener) {
        this.mDoorActionListener = mDoorActionListener;
    }

}
