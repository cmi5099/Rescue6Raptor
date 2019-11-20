package org.tensorflow.lite.examples.classification.fragments;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.CountDownTimer;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.tensorflow.lite.examples.classification.model.SensorDataObject;
import org.tensorflow.lite.examples.classification.servicenow.AsyncTaskTableTes4;
import org.tensorflow.lite.examples.classification.sqlite.SensorReaderDbHelper;

import static android.content.Context.BATTERY_SERVICE;

public class HomeViewModel extends AndroidViewModel implements SensorEventListener {

    private MutableLiveData<String> mBattery;
    private MutableLiveData<String> mWifi;
    private MutableLiveData<SensorDataObject> dataObjectMutableLiveData;
    private MutableLiveData<Boolean> isMission;

    private CountDownTimer missionTimer;
    private SensorDataObject sensorDO;
    private SensorReaderDbHelper dbHelper;

    // capture sensor details when there is an active mission
    private SensorManager manager;
    private Sensor accSensor;
    private Sensor lightSensor;
    private Sensor pressureSensor;
    private Sensor ambientTempSensor;
    private Sensor relativeHumiditySensor;

    public HomeViewModel(Application context) {
        super(context);
        mBattery = new MutableLiveData<>();
        mWifi = new MutableLiveData<>();
        dataObjectMutableLiveData = new MutableLiveData<>();
        isMission = new MutableLiveData<>();

        dbHelper = new SensorReaderDbHelper(context);

        BatteryManager bm = (BatteryManager) context.getSystemService(BATTERY_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int percentage = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            mBattery.setValue("Battery Percentage: " + percentage + "%");
        }

        ConnectivityManager connectionManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiCheck = connectionManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

        if (wifiCheck.isConnected()) {
            mWifi.setValue("WiFi: Connected");
        } else {
            mWifi.setValue("Wifi: Not Connected");
        }
    }

    public LiveData<String> getBattery() {
        return mBattery;
    }

    public LiveData<String> getWifi() {
        return mWifi;
    }

    public LiveData<SensorDataObject> getSensorDO() {
        return dataObjectMutableLiveData;
    }

    public MutableLiveData<Boolean> getIsMission() {
        return isMission;
    }

    public void activatePhoneSensors() {
        manager = (SensorManager) getApplication().getSystemService(Context.SENSOR_SERVICE);

        accSensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        lightSensor = manager.getDefaultSensor(Sensor.TYPE_LIGHT);
        pressureSensor = manager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        relativeHumiditySensor = manager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY);
        ambientTempSensor = manager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);

        sensorDO = new SensorDataObject();

        boolean isavailable = manager.registerListener(this, accSensor, SensorManager.SENSOR_DELAY_NORMAL);
        Log.i(HomeViewModel.class.getSimpleName(), "Accelerometer "+isavailable);
        isavailable = manager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        Log.i(HomeViewModel.class.getSimpleName(), "Light Sensor "+isavailable);
        isavailable = manager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL);
        Log.i(HomeViewModel.class.getSimpleName(), "Pressure "+isavailable);
        isavailable = manager.registerListener(this, relativeHumiditySensor, SensorManager.SENSOR_DELAY_NORMAL);
        Log.i(HomeViewModel.class.getSimpleName(), "Relative Humidity "+isavailable);
        isavailable = manager.registerListener(this, ambientTempSensor, SensorManager.SENSOR_DELAY_NORMAL);
        Log.i(HomeViewModel.class.getSimpleName(), "Ambient Temperature "+isavailable);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        // update the sensor object. the object carries latest sensor value by the time it saves to the database.
        float[] values = sensorEvent.values;
        switch (sensorEvent.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                sensorDO.setAccx(values[0]);
                sensorDO.setAccy(values[1]);
                sensorDO.setAccz(values[2]);
                break;
            case Sensor.TYPE_LIGHT:
                sensorDO.setLight(values[0]);
                break;
            case Sensor.TYPE_PRESSURE:
                sensorDO.setPressure(values[0]);
                break;
            case Sensor.TYPE_AMBIENT_TEMPERATURE:
                sensorDO.setAmbient_temp(values[0]);
                break;
            case Sensor.TYPE_RELATIVE_HUMIDITY:
                sensorDO.setRelativeHumidity(values[0]);
                break;
        }
        dataObjectMutableLiveData.postValue(sensorDO);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    public void stopPhoneSensors() {
        if (manager != null) {
            manager.unregisterListener(this, accSensor);
            manager.unregisterListener(this, lightSensor);
            manager.unregisterListener(this, pressureSensor);
            manager.unregisterListener(this, ambientTempSensor);
            manager.unregisterListener(this, relativeHumiditySensor);
        }
    }

    public void onStartMission(int minutes, int mLength, int mWidth) {
        missionTimer = new CountDownTimer(minutes * 60 * 1000, 30 * 1000) {

            @Override
            public void onTick(long l) {
                // will be called every interval
                // save to SQLite

                sensorDO.setU_length(mLength);
                sensorDO.setU_width(mWidth);
                SensorDataObject sdo = sensorDO;
                sensorDO = new SensorDataObject();

                long id = dbHelper.insertSensorData(sdo);
                Log.i(HomeViewModel.class.getSimpleName(), "Save sensor data " + sdo.toString());

                // send to service now
                new AsyncTaskTableTes4(sdo).execute();
            }

            @Override
            public void onFinish() {
                missionTimer = null;
                isMission.postValue(false);
            }
        };
        missionTimer.start();
        isMission.postValue(true);
    }

    public void onDetached() {
        if(missionTimer!=null){
            missionTimer.cancel();
            missionTimer=null;
        }
    }

}