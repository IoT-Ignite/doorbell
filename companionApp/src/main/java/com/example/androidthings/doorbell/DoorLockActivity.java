package com.example.androidthings.doorbell;

import android.app.Dialog;
import android.media.Image;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

public class DoorLockActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = DoorLockActivity.class.getSimpleName();
    private IotIgniteHandler mIotIgniteHandler;
    private Dialog mDialog;
    private Button acceptButton,rejectButton;
    private ImageView lastTakenImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_door_lock);

        mIotIgniteHandler = IotIgniteHandler.getInstance(getApplicationContext());
        Log.d(TAG,"Doorlock activity started.");

        mDialog = new Dialog(DoorLockActivity.this);

        mDialog.setContentView(R.layout.doorlock_dialog);
        mDialog.setTitle(R.string.dialogTitle);

        lastTakenImage = (ImageView) mDialog.findViewById(R.id.imageDialog);
        lastTakenImage.setImageBitmap(mIotIgniteHandler.getLastTakenImage());

        acceptButton = (Button) mDialog.findViewById(R.id.acceptButton);
        rejectButton = (Button) mDialog.findViewById(R.id.declineButton);

        acceptButton.setOnClickListener(this);
        rejectButton.setOnClickListener(this);

        mDialog.show();
    }

    @Override
    public void onClick(View v) {

        if(v.equals(acceptButton)){
            mIotIgniteHandler.sendData(true);
        }else if(v.equals(rejectButton)){
            mIotIgniteHandler.sendData(false);
        }
        mDialog.dismiss();

        finish();
    }
}
