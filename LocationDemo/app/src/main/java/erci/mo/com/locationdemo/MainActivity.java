package erci.mo.com.locationdemo;

import android.content.Context;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.yanzhenjie.permission.AndPermission;
import com.yanzhenjie.permission.runtime.Permission;

import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {
    private static final String TAG = "MainActivity";
    LocationManager locationManager;
    boolean hasAddTestProvider = false;
    boolean canMockPosition = false;
    private static final String[] location = {"公司", "郑州", "南宁安吉", "坪洲"};
    private ArrayList<LocationBean> locationBeans = new ArrayList<>();
    private LocationBean currentLocation;
    private Spinner spinner;
    private TextView tv_state;
    private boolean running = true;
    private ArrayAdapter<String> adapter;
    private ExecutorService singThreadPool = Executors.newSingleThreadExecutor ();


    @Override

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        spinner = findViewById(R.id.Spinner);
        tv_state = findViewById(R.id.tv_state);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        checkCanMockPosition();
        initView();
        initData();
        init();
        refreshStateUI(false,"未开启虚拟定位");
    }

    public void refreshStateUI(boolean state,String msg) {
        tv_state.setText(msg);
        tv_state.setTextColor(state?Color.GREEN:Color.RED);
    }

    public boolean checkCanMockPosition() {
        canMockPosition = (Settings.Secure.getInt(getContentResolver(), Settings.Secure.ALLOW_MOCK_LOCATION, 0) != 0)
                || Build.VERSION.SDK_INT > 22;
        return canMockPosition;
    }
    private void initData() {
        locationBeans.add(new LocationBean(22.5054850534, 113.9160209412));//公司
        locationBeans.add(new LocationBean(34.8181943300, 113.5297140800));//郑州
        locationBeans.add(new LocationBean(22.8815702500, 108.2952590700));//南宁安吉
        locationBeans.add(new LocationBean(22.5719273162, 113.8659289487));//坪洲
        currentLocation = locationBeans.get(0);
    }

    private void initView() {
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, location);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);
    }

    private void init() {
        if (canMockPosition && hasAddTestProvider == false) {
            try {
                String providerStr = LocationManager.GPS_PROVIDER;
                LocationProvider provider = locationManager.getProvider(providerStr);
                if (provider != null) {
                    locationManager.addTestProvider(
                            provider.getName()
                            , provider.requiresNetwork()
                            , provider.requiresSatellite()
                            , provider.requiresCell()
                            , provider.hasMonetaryCost()
                            , provider.supportsAltitude()
                            , provider.supportsSpeed()
                            , provider.supportsBearing()
                            , provider.getPowerRequirement()
                            , provider.getAccuracy());
                } else {
                    locationManager.addTestProvider(
                            providerStr
                            , true, true, false, false, true, true, true
                            , Criteria.POWER_HIGH, Criteria.ACCURACY_FINE);
                }
                locationManager.setTestProviderEnabled(providerStr, true);
                locationManager.setTestProviderStatus(providerStr, LocationProvider.AVAILABLE, null, System.currentTimeMillis());

                // 模拟位置可用
                hasAddTestProvider = true;
                canMockPosition = true;
            } catch (SecurityException e) {
                canMockPosition = false;
            }
        }
    }

    /**
     * 停止模拟位置，以免启用模拟数据后无法还原使用系统位置
     * 若模拟位置未开启，则removeTestProvider将会抛出异常；
     * 若已addTestProvider后，关闭模拟位置，未removeTestProvider将导致系统GPS无数据更新；
     */
    public void stopMockLocation(View v) {
        stop();
    }


    public void stop() {
        if (hasAddTestProvider) {
            try {
                locationManager.removeTestProvider(LocationManager.GPS_PROVIDER);
            } catch (Exception ex) {
                // 若未成功addTestProvider，或者系统模拟位置已关闭则必然会出错
            }
            running = false;
            hasAddTestProvider = false;
            refreshStateUI(false,"模拟定位关闭");
        }
    }

    public void startMockLocation(View v) {
        AndPermission.with(this)
                .runtime()
                .permission(Permission.Group.LOCATION)
                .onGranted(permissions -> {
                    running = true;
                    init();
                    singThreadPool.submit(new RunnableMockLocation());
                })
                .onDenied(permissions -> {
                    Toast.makeText(this, "请授予定位权限", Toast.LENGTH_SHORT).show();
                })
                .start();

    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        currentLocation = locationBeans.get(position);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }


    private Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 1:
                    refreshStateUI(true,"模拟定位成功");
                    break;
                case 2:
                    refreshStateUI(false,"模拟定位失败");
                    break;
            }
        }
    };
    class RunnableMockLocation implements Runnable {
        @Override
        public void run() {
            while (running) {
                try {
                    Thread.sleep(100);
                    if (hasAddTestProvider == false || currentLocation == null) {
                        continue;
                    }
                    try {
                        // 模拟位置（addTestProvider成功的前提下）
                        String providerStr = LocationManager.GPS_PROVIDER;
                        Location mockLocation = new Location(providerStr);
                        mockLocation.setLatitude(currentLocation.latitude);   // 纬度（度）
                        mockLocation.setLongitude(currentLocation.longitude);  // 经度（度）
//                        mockLocation.setAltitude(30);    // 高程（米）
//                        mockLocation.setBearing(180);   // 方向（度）
//                        mockLocation.setSpeed(10);    //速度（米/秒）
                        mockLocation.setAccuracy(0.1f);   // 精度（米）
                        mockLocation.setTime(new Date().getTime());   // 本地时间
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                            mockLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
                        }
                        locationManager.setTestProviderLocation(providerStr, mockLocation);
                        Message message = handler.obtainMessage();
                        message.what = 1;
                        handler.sendMessage(message);
                    } catch (Exception e) {
                        // 防止用户在软件运行过程中关闭模拟位置或选择其他应用
                        Message message = handler.obtainMessage();
                        message.what = 2;
                        handler.sendMessage(message);
                        stop();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

}