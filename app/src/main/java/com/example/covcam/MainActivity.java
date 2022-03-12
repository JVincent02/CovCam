package com.example.covcam;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;


import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioRecord;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.app.progresviews.ProgressLine;

import org.tensorflow.lite.support.audio.TensorAudio;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.audio.classifier.AudioClassifier;
import org.tensorflow.lite.task.audio.classifier.Classifications;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    View connectBtn;
    private boolean mIsBluetoothEnabled;
    private BluetoothAdapter mBluetoothAdapter;
    private static final int BLUETOOTH_REQUEST_ID = 99;
    private static final String TAG = "MainActivity";

    private BroadcastReceiver mBluetoothScanResultBroadcastReceiver;
    private BroadcastReceiver mBluetoothScanStopBroadcastReceiver;
    private BroadcastReceiver mBloodOximeterBroadcastReceiver;
    private String mDeviceAddress;

    ProgressLine oxyProgress;
    ProgressLine pulseProgress;

    private String [] permissions = {Manifest.permission.RECORD_AUDIO};
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionToRecordAccepted = false;

    String modelPath = "lite-model_yamnet_classification_tflite_1.tflite";
    float probabilityThreshold =0.3f;
    int REQUEST_RECORD_AUDIO = 1337;
    AudioRecord audioRecord;
    AudioClassifier audioClassifier;
    TensorAudio tensorAudio;
    TensorAudio.TensorAudioFormat tensorAudioFormat;

    TextView dispTxt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        setContentView(R.layout.activity_main);
        dispTxt = findViewById(R.id.dispTxt);
        startAudio();
        connectBtn = findViewById(R.id.connectBtn);
        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BluetoothScanService.startScan(MainActivity.this);
                Log.wtf("deg","scan started");
                if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
                    Toast.makeText(MainActivity.this, "BL not supported", Toast.LENGTH_SHORT).show();
                    finish();
                }
// Use this check to determine whether BLE is supported on the device. Then
// you can selectively disable BLE-related features.
                if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                    Toast.makeText(MainActivity.this, "BLE not supported", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
        });
        oxyProgress = findViewById(R.id.oxyProgress);
        pulseProgress = findViewById(R.id.pulseProgress);
        startBLE();
    }

    private void startAudio(){
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_RECORD_AUDIO_PERMISSION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission Granted
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        continueAudio();
                    }
                } else {
                    // Permission Denied
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void continueAudio(){
        try {
            audioClassifier = AudioClassifier.createFromFile(this,modelPath);
            tensorAudio = audioClassifier.createInputTensorAudio();
            tensorAudioFormat = audioClassifier.getRequiredTensorAudioFormat();
            //String recorderSpecs = "Number Of Channels: ${format.channels}\n" +"Sample Rate: ${format.sampleRate}";

            audioRecord = audioClassifier.createAudioRecord();
            audioRecord.startRecording();

            Timer timer = new Timer();
            TimerTask updateTask = new UpdateTask();
            timer.scheduleAtFixedRate(updateTask,1,500);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    class UpdateTask extends TimerTask {

        public void run() {
            int numberOfSamples =tensorAudio.load(audioRecord);
            List<Classifications> classification = audioClassifier.classify(tensorAudio);
            List<Category> filteredCategory = new ArrayList<Category>();
            for(Category category: classification.get(0).getCategories()){
                if(category.getScore()>probabilityThreshold){
                    filteredCategory.add(category);
                }
            }
            String outputString ="";
            for(Category filtered:filteredCategory){
                outputString+=(filtered.getLabel()+" -> "+filtered.getScore()+"\n");
            }
            //Log.e("result",outputString);
            dispTxt.setText(outputString);
        }
    }


    private void startBLE(){
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = bluetoothManager.getAdapter();
        }
        else {
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show();
        }
        mBluetoothScanResultBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ScanResult scanResult = intent.getParcelableExtra(BluetoothScanService.PARAM_SCAN_RESULT);
                if (scanResult.getDevice().getName() != null && scanResult.getDevice().getName().equals("OXIMETER")) {
                    Log.d(TAG, "found BerryMed device");
                    if ( mDeviceAddress == null || mDeviceAddress.isEmpty()) {
                        mDeviceAddress = scanResult.getDevice().getAddress();
                        BluetoothScanService.requestStop(MainActivity.this);
                    }
                }
            }
        };

        mBluetoothScanStopBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ( mDeviceAddress != null && !mDeviceAddress.isEmpty()) {
                    Log.d(TAG, "staring BloodOximeter services");
                    BloodOximeterDeviceService.requestData(MainActivity.this, mDeviceAddress);
                }
            }
        };
        mBloodOximeterBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ( BloodOximeterDeviceService.EVENT_CONNECTED.equals(intent.getStringExtra(BloodOximeterDeviceService.PARAM_EVENT_TYPE))) {
                    Log.d(TAG, "Device connected");
                }
                else if ( BloodOximeterDeviceService.EVENT_DATA.equals(intent.getStringExtra(BloodOximeterDeviceService.PARAM_EVENT_TYPE))) {
                    BCIData bciData = intent.getParcelableExtra(BloodOximeterDeviceService.PARAM_DATA);
                    Log.d(TAG, bciData.toString() );
                }
                else if ( BloodOximeterDeviceService.EVENT_ERROR.equals(intent.getStringExtra(BloodOximeterDeviceService.PARAM_EVENT_TYPE))) {
                    Log.d(TAG, String.format(Locale.UK, "Error connecting to device %s", intent.getStringExtra(BloodOximeterDeviceService.PARAM_ERROR_MESSAGE)));
                }
                else if ( BloodOximeterDeviceService.EVENT_DISCONNECTED.equals(intent.getStringExtra(BloodOximeterDeviceService.PARAM_EVENT_TYPE))) {
                    Log.d(TAG, "Device disconnected");
                }else if ( BloodOximeterDeviceService.EVENT_CHAR.equals(intent.getStringExtra(BloodOximeterDeviceService.PARAM_EVENT_TYPE))) {
                    String val = intent.getStringExtra(BloodOximeterDeviceService.PARAM_DATA);
                    String s[] = val.split(",");
                    oxyProgress.setmPercentage(Integer.parseInt(s[0]));
                    oxyProgress.setmValueText(s[0]);
                    pulseProgress.setmPercentage(Integer.parseInt(s[1]));
                    pulseProgress.setmValueText(s[1]);
                }
            }
        };
    }
    @Override
    protected void onStart() {
        super.onStart();
        if ( mBluetoothAdapter != null || !mBluetoothAdapter.isEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, BLUETOOTH_REQUEST_ID);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
        broadcastManager.registerReceiver(mBluetoothScanResultBroadcastReceiver, new IntentFilter(BluetoothScanService.ON_SCAN_RESULT));
        broadcastManager.registerReceiver(mBluetoothScanStopBroadcastReceiver, new IntentFilter(BluetoothScanService.ON_SCAN_STOP));
        broadcastManager.registerReceiver(mBloodOximeterBroadcastReceiver, new IntentFilter(BloodOximeterDeviceService.ON_EVENT));
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(this);
        broadcastManager.unregisterReceiver(mBluetoothScanResultBroadcastReceiver);
        broadcastManager.unregisterReceiver(mBluetoothScanStopBroadcastReceiver);
        broadcastManager.unregisterReceiver(mBloodOximeterBroadcastReceiver);
    }

/*    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BLUETOOTH_REQUEST_ID) {
            mIsBluetoothEnabled = false;
            if (resultCode == RESULT_OK) {
                mIsBluetoothEnabled = true;
            }
        }
    }*/

}