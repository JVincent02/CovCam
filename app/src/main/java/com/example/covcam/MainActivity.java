package com.example.covcam;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;


import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioRecord;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import com.app.progresviews.ProgressLine;
import com.google.android.material.slider.Slider;

import org.tensorflow.lite.support.audio.TensorAudio;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.audio.classifier.AudioClassifier;
import org.tensorflow.lite.task.audio.classifier.Classifications;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    Button connectBtn;
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
    ProgressLine coughProgress;

    private String [] permissions = {Manifest.permission.RECORD_AUDIO};
    private String [] permissions2 = {Manifest.permission.ACCESS_FINE_LOCATION };
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final int REQUEST_LOCATION = 300;
    private boolean permissionToRecordAccepted = false;

    String modelPath = "lite-model_yamnet_classification_tflite_1.tflite";
    float probabilityThreshold =0.3f;
    int REQUEST_RECORD_AUDIO = 1337;
    AudioRecord audioRecord;
    AudioClassifier audioClassifier;
    TensorAudio tensorAudio;
    TensorAudio.TensorAudioFormat tensorAudioFormat;

    TextView dispTxt;

    Slider oxySlider;
    Slider oxy2Slider;
    Slider countSlider;
    Slider coughSlider;
    TextView oxyTxt;
    TextView oxy2Txt;
    TextView countTxt;
    TextView coughTxt;

    int oxyWarnThres = 90;
    int oxyEmerThres = 70;
    int coughThres=5;
    int countThres=30;
    boolean isWarnThresChecked=true;
    boolean isEmerThresChecked=true;
    boolean isCountThresChecked=true;
    CheckBox oxyCheck;
    CheckBox oxy2Check;
    CheckBox countCheck;

    View settingBtn;
    View saveBtn;
    View settingsFragment;

    int connectionState=0 ;

    Date currentTime;
    Date readTime;
    int coughCount=0;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        setContentView(R.layout.activity_main);
        currentTime = Calendar.getInstance().getTime();
        Log.e("date",currentTime.toString());

        SharedPreferences prefs = getSharedPreferences("data", MODE_PRIVATE);
        oxyWarnThres = prefs.getInt("oxyWarnThres", 90);
        oxyEmerThres = prefs.getInt("oxyEmerThres", 70);
        countThres = prefs.getInt("countThres", 30);
        coughThres = prefs.getInt("coughThres", 5);
        isWarnThresChecked = prefs.getBoolean("isWarnThresChecked", true);
        isEmerThresChecked = prefs.getBoolean("isEmerThresChecked", true);
        isCountThresChecked = prefs.getBoolean("isCountThresChecked", true);
        readTime = new Date(prefs.getString("readTime",currentTime.toString()));
        coughCount = prefs.getInt("coughCount",0);
        //Log.e("read",readTime.toString());
        createNotificationChannel();

        dispTxt = findViewById(R.id.dispTxt);
        settingBtn = findViewById(R.id.settingBtn);
        saveBtn = findViewById(R.id.saveBtn);
        oxySlider = findViewById(R.id.oxySlider);
        oxyTxt = findViewById(R.id.oxyTxt);
        oxy2Slider = findViewById(R.id.oxy2Slider);
        oxy2Txt = findViewById(R.id.oxy2Txt);
        oxyCheck = findViewById(R.id.oxyCheck);
        oxy2Check = findViewById(R.id.oxy2Check);
        countCheck = findViewById(R.id.countCheck);
        countTxt = findViewById(R.id.countTxt);
        countSlider = findViewById(R.id.countSlider);
        coughTxt = findViewById(R.id.coughTxt);
        coughSlider = findViewById(R.id.coughSlider);

        oxySlider.setValue(oxyWarnThres);
        oxy2Slider.setValue(oxyEmerThres);
        oxyTxt.setText(oxyWarnThres+" %");
        oxy2Txt.setText(oxyEmerThres+" %");
        coughTxt.setText(coughThres+" min");
        coughSlider.setValue(coughThres);
        countTxt.setText(countThres+"");
        countSlider.setValue(countThres);
        settingsFragment = findViewById(R.id.settingsFragment);
        oxyCheck.setChecked(isWarnThresChecked);
        oxy2Check.setChecked(isEmerThresChecked);
        countCheck.setChecked(isCountThresChecked);
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                settingsFragment.setVisibility(View.GONE);
                SharedPreferences.Editor editor = getSharedPreferences("data", MODE_PRIVATE).edit();
                editor.putInt("oxyWarnThres", oxyWarnThres);
                editor.putInt("oxyEmerThres", oxyEmerThres);
                editor.putInt("countThres", countThres);
                editor.putInt("coughThres", coughThres);
                editor.putInt("coughCount", 0);
                editor.putBoolean("isWarnThresChecked",oxyCheck.isChecked());
                editor.putBoolean("isEmerThresChecked",oxy2Check.isChecked());
                editor.putBoolean("isCountThresChecked",countCheck.isChecked());
                editor.putString("readTime",Calendar.getInstance().getTime().toString());
                editor.apply();
            }
        });
        settingBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                settingsFragment.setVisibility(View.VISIBLE);
            }
        });
        oxySlider.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
                oxyWarnThres = (int)slider.getValue();
                oxyTxt.setText(oxyWarnThres +" %");
                if(oxyEmerThres>=oxyWarnThres){
                    oxyEmerThres=oxyWarnThres-1;
                    oxy2Txt.setText(oxyEmerThres+" %");
                    oxy2Slider.setValue(oxyEmerThres);
                }
                oxy2Slider.setValueTo(oxyWarnThres-1);
            }
        });
        oxy2Slider.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
                oxyEmerThres = (int)slider.getValue();
                oxy2Txt.setText(oxyEmerThres+" %");
            }
        });
        countSlider.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
                countThres = (int)slider.getValue();
                countTxt.setText(countThres+"");
            }
        });
        coughSlider.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
                coughThres = (int)slider.getValue();
                coughTxt.setText(coughThres+" min");
            }
        });

        startAudio();
        connectBtn = findViewById(R.id.connectBtn);
        connectBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (connectionState==0) {
                    if (ContextCompat.checkSelfPermission(
                            MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                            PackageManager.PERMISSION_GRANTED) {
                        // You can use the API that requires the permission.
                        startScan();
                    }else{
                        ActivityCompat.requestPermissions(MainActivity.this, permissions2, REQUEST_LOCATION);
                    }
                }
            }
        });
        oxyProgress = findViewById(R.id.oxyProgress);
        pulseProgress = findViewById(R.id.pulseProgress);
        coughProgress = findViewById(R.id.coughProgress);
        startBLE();
    }
    private void startScan(){
        if (mBluetoothAdapter != null || !mBluetoothAdapter.isEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, BLUETOOTH_REQUEST_ID);
        }
        connectBtn.setText("Finding Oxymeter...");
        connectionState=1;
        BluetoothScanService.startScan(MainActivity.this);
        Log.wtf("deg", "scan started");
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
            case REQUEST_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission Granted
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION ) == PackageManager.PERMISSION_GRANTED) {
                        startScan();
                    }
                } else {
                    // Permission Denied
                }
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
                //outputString+=(filtered.getLabel()+" -> "+filtered.getScore()+"\n");
                if(filtered.getLabel().equals("Cough")){
                    //outputString+=(filtered.getLabel()+" -> "+filtered.getScore()+"\n");
                    coughCount++;
                    outputString=coughCount+"";
                    updateCough();
                }
            }
            //Log.e("result",outputString);
            dispTxt.setText(outputString);
            currentTime=Calendar.getInstance().getTime();
            //Log.e("diff",currentTime.toString()+" ---->current");
            //Log.e("diff",readTime.toString()+" ---->read");
            int milliDiff = (int)(currentTime.getTime()-readTime.getTime());
            int thres = coughThres*60*1000;
            if(milliDiff>thres){
                readTime=currentTime;
                coughCount=0;
                updateCough();
                SharedPreferences.Editor editor = getSharedPreferences("data", MODE_PRIVATE).edit();
                editor.putInt("coughCount",coughCount);
                editor.putString("readTime",readTime.toString());
                editor.apply();
            }else{
                if(coughCount>=countThres){
                    displayPushNotif("Cough Alert",coughThres+" cough/s was detected within "+coughThres+" minute/s");
                    Log.e("triggered",coughCount+"");
                    coughCount=0;
                    updateCough();
                    SharedPreferences.Editor editor = getSharedPreferences("data", MODE_PRIVATE).edit();
                    editor.putInt("coughCount",coughCount);
                    editor.apply();
                }
            }
        }
    }
private void updateCough(){
    runOnUiThread(new Runnable() {
        @Override
        public void run() {
            coughProgress.setmValueText(coughCount);
            coughProgress.setmPercentage(coughCount*100/countThres);
        }
    });
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
                    Log.d(TAG, "found device");
                    if ( mDeviceAddress == null || mDeviceAddress.isEmpty()) {
                        mDeviceAddress = scanResult.getDevice().getAddress();
                        BluetoothScanService.requestStop(MainActivity.this);
                    }
                }
                if(intent.getAction().equals(BluetoothScanService.ON_SCAN_STOP)){
                    Log.e("------>","onmstop");
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
                    connectBtn.setText("Connected");
                    connectionState=2;
                }
                else if ( BloodOximeterDeviceService.EVENT_DATA.equals(intent.getStringExtra(BloodOximeterDeviceService.PARAM_EVENT_TYPE))) {
                    BCIData bciData = intent.getParcelableExtra(BloodOximeterDeviceService.PARAM_DATA);
                    Log.e(TAG, "dataintent" );
                }
                else if ( BloodOximeterDeviceService.EVENT_ERROR.equals(intent.getStringExtra(BloodOximeterDeviceService.PARAM_EVENT_TYPE))) {
                    Log.d(TAG, String.format(Locale.UK, "Error connecting to device %s", intent.getStringExtra(BloodOximeterDeviceService.PARAM_ERROR_MESSAGE)));
                    Log.e(TAG, "errorintent" );
                }
                else if ( BloodOximeterDeviceService.EVENT_DISCONNECTED.equals(intent.getStringExtra(BloodOximeterDeviceService.PARAM_EVENT_TYPE))) {
                    Log.d(TAG, "Device disconnected");
                    connectBtn.setText("Disconnected, Tap to Reconnect");
                    connectionState=0;
                    pulseProgress.setmPercentage(0);
                    pulseProgress.setmValueText(0);
                    oxyProgress.setmPercentage(0);
                    oxyProgress.setmValueText(0);
                }else if ( BloodOximeterDeviceService.EVENT_CHAR.equals(intent.getStringExtra(BloodOximeterDeviceService.PARAM_EVENT_TYPE))) {
                    String val = intent.getStringExtra(BloodOximeterDeviceService.PARAM_DATA);
                    displayData(val);
                }
            }
        };
    }
    private void displayData(String val){
        String s[] = val.split(",");
        if(!s[0].equals("127")) {
            int spo = (Integer.parseInt(s[0]));
            oxyProgress.setmPercentage(spo);
            oxyProgress.setmValueText(s[0]);
            if(spo<=oxyEmerThres){
                oxyProgress.setBackgroundColor(getResources().getColor(R.color.red));
                displayPushNotif("Emergency!!!","SPO2 is very low");
            }else if(spo<=oxyWarnThres){
                oxyProgress.setBackgroundColor(getResources().getColor(R.color.yellow));
                displayPushNotif("Warning!","SPO2 is low");
            }else{
                oxyProgress.setBackgroundColor(getResources().getColor(R.color.white));
            }
            //if(Integer.parseInt(s[0]))
        }else{
            oxyProgress.setmPercentage(0);
            oxyProgress.setmValueText(0);
        }
        if(!s[0].equals("127")) {
            int pulse = (Integer.parseInt(s[1]));
            pulseProgress.setmPercentage((int)(pulse*0.75));
            pulseProgress.setmValueText(s[1]);
/*            if(pulse<=60){
                pulseProgress.setBackgroundColor(getResources().getColor(R.color.red));
            }else if(pulse<=oxyWarnThres){
                pulseProgress.setBackgroundColor(getResources().getColor(R.color.yellow));
            }else{
                pulseProgress.setBackgroundColor(getResources().getColor(R.color.white));
            }*/
        }else{
            pulseProgress.setmPercentage(0);
            pulseProgress.setmValueText(0);
        }
        //pulseProgress.setBackgroundColor(getResources().getColor(R.color.yellow));

        Log.e("data: ",val);
    }
    /*@Override
    protected void onStart() {
        super.onStart();
        if ( mBluetoothAdapter != null || !mBluetoothAdapter.isEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, BLUETOOTH_REQUEST_ID);
        }
    }
    */

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Cov-Cam";
            String description = "Alerts";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel("1", name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    private void displayPushNotif(String title,String content){
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "1")
                .setSmallIcon(R.drawable.settings)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

// notificationId is a unique int for each notification that you must define
        notificationManager.notify(1, builder.build());
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