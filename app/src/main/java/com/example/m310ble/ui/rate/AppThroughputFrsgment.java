package com.example.m310ble.ui.rate;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.Html;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;


import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.list.DialogListExtKt;
import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleGattCallback;
import com.clj.fastble.callback.BleMtuChangedCallback;
import com.clj.fastble.callback.BleNotifyCallback;
import com.clj.fastble.callback.BleScanCallback;
import com.clj.fastble.callback.BleWriteCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.utils.HexUtil;
import com.example.m310ble.UtilTool.ResourceManager;
import com.example.m310ble.UtilTool.TextTool;
import com.example.m310ble.ui.OTA.BleApplication;
import com.example.m310ble.ui.OTA.FotaMsg;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import com.example.m310ble.R;

import static com.clj.fastble.utils.HexUtil.encodeHexStr_OneByte;
import static com.example.m310ble.ui.rate.ThroughputMsg.THROUGHPUT_CHARACTERISTICUUID_128BIT_NOTIFY;
import static com.example.m310ble.ui.rate.ThroughputMsg.THROUGHPUT_CHARACTERISTICUUID_NOTIFY;


public class AppThroughputFrsgment extends Fragment {
    private BleApplication app;

    private Handler mMainHandler = new Handler(Looper.getMainLooper());
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private boolean mHandling;

    private TextView tv_packetlen;
    private EditText et_packetlen;
    private TextView tv_datamtulen;
    private EditText et_datamtulen;
    private TextView tv_sent_packet_interval;
    private EditText et_sent_packet_interval;
    private TextView tv_ate_mtu_test_option;
    private EditText et_ate_mtu_test_option;
    private TextView tv_ate_type;
    private Spinner sp_testtype;
    private Spinner sp_atetype;
    private TextView txtlog;
    private TextView txtlogtitle;
    private Button btn_runthroughput;
    private ProgressBar PBar_thp; //進度條

    public boolean THROUGTPUT_DO_AUTO_TEST_COMBINATION = false; //測試所有組合 PHY 1M/2M, MTU 20/244Byte, Connect Interval LOW/BALANCED/HIGH
    public int THROUGTPUT_AUTO_TEST_OPTION_SEL = 0; //0:No ATE, 1:Test All Option, 2:Test PHY 1M/2M Option, 3.Test ConnectInterval Option, 4.Test MTU Len Option

    public int write_one_package_end_falg = 0; //傳輸完成，無論成功或失敗，都會變1
    public int write_success_falg = 1; //傳輸完成後，成功為1或失敗為0

    //Throughput test
    public int Throughput_sel_test_type = 0; //1:App Tx and Device Rx, 2:App Rx and Device Tx
    public int Throughput_sel_packet_length = 1048712; //user input stress data length (default is 1048712 Byte)
    public int Throughput_sel_data_length = 244; //使用者輸入測項每筆資料長度 (預設為244 Byte)
    public int Throughput_sel_sent_packet_interval_ms = 0; //使用者輸入Packet 之間的間格時間，單位ms (default 0ms)

    public int Throughput_stop_test_flag = 0;

    public int Throughput_total_receiver_data_len = 0;
    public int Throughput_total_transferred_data_len = 0;
    Date Throughput_StartDate = new Date(System.currentTimeMillis());
    Date Throughput_StopDate = new Date(System.currentTimeMillis());

    //Record each data package write->successful callback use time.
    Date[] throughput_time_diff = new Date[100000];
    public int throughput_time_diff_index = 0;

    public int Throughput_percentage_val = 0;
    public int Throughput_percentage_preval = 0;

    //Throughput States
    public int Throughput_States = ThroughputMsg.THROUGHPUT_STATE_IDAL;

    //存檔時間計算
    Date throughput_savelog_time_start;
    Date throughput_savelog_time_arrival;
    long throughput_savelog_time_diff;

    class ATEWRHandler extends Handler {

        private final WeakReference<AppThroughputFrsgment> mAppThroughputFrsgment;

        ATEWRHandler(Looper looper, AppThroughputFrsgment appThroughputFrsgment) {
            super(looper);
            mAppThroughputFrsgment = new WeakReference<>(appThroughputFrsgment);
        }

        @Override
        public void handleMessage(Message msg) {
            AppThroughputFrsgment appThroughputFrsgment = mAppThroughputFrsgment.get();

            final StringBuilder write_str_temp = new StringBuilder();
            final StringBuilder write_str_final = new StringBuilder();
            final StringBuilder write_str_remainder = new StringBuilder();

            //final char stress_len = (char) Throughput_sel_data_length;

            if (appThroughputFrsgment != null) {
                switch (msg.what) {
                    case ThroughputMsg.THROUGHPUT_TEST_APPTX_DEVICERX: {

                        final BleDevice HDM_bleDevice = _connectBleDevice; //這裡銜接上藍牙裝置
                        final TextView HDM_txt = (TextView) txtlog;

//                        runOnUiThread(new Runnable() {
//                            @Override
//                            public void run() {
//                                ClearText(HDM_txt);
//                            }
//                        });

                        Throughput_States = ThroughputMsg.THROUGHPUT_STATE_IDAL;

                        //CONNECTION_PRIORITY_BALANCED = 0; CONNECTION_PRIORITY_HIGH = 1; CONNECTION_PRIORITY_LOW_POWER = 2;
                        int physt, physp;
                        int connectionIntervalst, connectionIntervalsp;
                        int mtuIndexst, mtuIndexsp;

                        int[] Throughput_sel_data_length_ate_mtu_opt = new int[0];

                        //public int THROUGTPUT_AUTO_TEST_OPTION_SEL = 0; //0:No ATE, 1:Test All Option, 2:Test PHY 1M/2M Option, 3.Test ConnectInterval Option, 4.Test MTU Len Option

                        if ((THROUGTPUT_DO_AUTO_TEST_COMBINATION) && (!app.getReleaseToCTMR())) {//測試所有組合
                            String[] mtuopt;
                            String str_mtu = et_ate_mtu_test_option.getText().toString();

                            if(THROUGTPUT_AUTO_TEST_OPTION_SEL==1){ //ALL option
                                if (TextUtils.isEmpty(str_mtu)) {
                                    mtuopt = null;
                                } else {
                                    mtuopt = str_mtu.split(",");
                                }
                                Throughput_sel_data_length_ate_mtu_opt = new int[mtuopt.length];
                                for (int mtuoptidx = 0; mtuoptidx < mtuopt.length; mtuoptidx++) {
                                    Throughput_sel_data_length_ate_mtu_opt[mtuoptidx] = Integer.parseInt(mtuopt[mtuoptidx]);
                                }
                                mtuIndexst = 0;
                                //mtuIndexsp = 2;
                                mtuIndexsp = mtuopt.length - 1;
                                physt = 1;
                                physp = 2;
                                connectionIntervalst = 0;
                                connectionIntervalsp = 2;
                            }
                            else if(THROUGTPUT_AUTO_TEST_OPTION_SEL==2){ //PHY
                                mtuIndexst = 1;
                                mtuIndexsp = 1;
                                physt = 1;
                                physp = 2;
                                connectionIntervalst = app.getConnectInterval();
                                connectionIntervalsp = app.getConnectInterval();
                            }
                            else if(THROUGTPUT_AUTO_TEST_OPTION_SEL==3){ //connection interval
                                mtuIndexst = 1;
                                mtuIndexsp = 1;
                                physt = app.getReConnectTxPhy();
                                physp = app.getReConnectTxPhy();
                                connectionIntervalst = 0;
                                connectionIntervalsp = 2;
                            }
                            else{// if(THROUGTPUT_AUTO_TEST_OPTION_SEL==3){ //MTU
                                if (TextUtils.isEmpty(str_mtu)) {
                                    mtuopt = null;
                                } else {
                                    mtuopt = str_mtu.split(",");
                                }
                                Throughput_sel_data_length_ate_mtu_opt = new int[mtuopt.length];
                                for (int mtuoptidx = 0; mtuoptidx < mtuopt.length; mtuoptidx++) {
                                    Throughput_sel_data_length_ate_mtu_opt[mtuoptidx] = Integer.parseInt(mtuopt[mtuoptidx]);
                                }
                                mtuIndexst = 0;
                                //mtuIndexsp = 2;
                                mtuIndexsp = mtuopt.length - 1;
                                physt = app.getReConnectTxPhy();
                                physp = app.getReConnectTxPhy();
                                connectionIntervalst = app.getConnectInterval();
                                connectionIntervalsp = app.getConnectInterval();
                            }

                        } else {
                            mtuIndexst = 1;
                            mtuIndexsp = 1;
                            physt = app.getReConnectTxPhy();
                            physp = app.getReConnectTxPhy();
                            connectionIntervalst = app.getConnectInterval();
                            connectionIntervalsp = app.getConnectInterval();
                        }

                        do{
                            for (int mtuIndex = mtuIndexst; mtuIndex <= mtuIndexsp; mtuIndex++) {
                                for (int testphy = physt; testphy <= physp; testphy++) { //Phy= 1M/2M
                                    for (int testonn = connectionIntervalst; testonn <= connectionIntervalsp; testonn++) { //CONNECTION_PRIORITY_BALANCED = 0; CONNECTION_PRIORITY_HIGH = 1; CONNECTION_PRIORITY_LOW_POWER = 2;
                                        if ((THROUGTPUT_DO_AUTO_TEST_COMBINATION) && (!app.getReleaseToCTMR())) {

                                            if ((THROUGTPUT_AUTO_TEST_OPTION_SEL == 1) || (THROUGTPUT_AUTO_TEST_OPTION_SEL == 4)) {
                                                Throughput_sel_data_length = Throughput_sel_data_length_ate_mtu_opt[mtuIndex];
                                            } else {
                                                Throughput_sel_data_length = Integer.parseInt(et_datamtulen.getText().toString().trim());
                                            }

                                            try {
                                                Thread.sleep(1000);
                                            } catch (InterruptedException e) {
                                                // TODO Auto-generated catch block
                                                e.printStackTrace();
                                            }

                                            //Change PHY
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                BleManager.getInstance().setPreferredPhy(HDM_bleDevice, testphy, testphy, 0);
                                                app.setReConnectTxPhy(testphy);
                                                app.setReConnectRxPhy(testphy);
                                            } else {
                                                //no PHY 2M
                                                Toast.makeText(getActivity(), getString(R.string.only_support_1M), Toast.LENGTH_LONG).show();
                                            }

                                            try {
                                                Thread.sleep(2000);
                                            } catch (InterruptedException e) {
                                                // TODO Auto-generated catch block
                                                e.printStackTrace();
                                            }

                                            //change connect interval
                                            //CONNECTION_PRIORITY_BALANCED = 0; CONNECTION_PRIORITY_HIGH = 1; CONNECTION_PRIORITY_LOW_POWER = 2;
                                            //時間由短至長測試
                                            if (testonn == 0) {
                                                BleManager.getInstance().requestConnectionPriority(HDM_bleDevice, 1);
                                                app.setConnectInterval(1);
                                            } else if (testonn == 1) {
                                                BleManager.getInstance().requestConnectionPriority(HDM_bleDevice, 0);
                                                app.setConnectInterval(0);
                                            } else {
                                                BleManager.getInstance().requestConnectionPriority(HDM_bleDevice, testonn);
                                                app.setConnectInterval(testonn);
                                            }

                                            try {
                                                Thread.sleep(2000);
                                            } catch (InterruptedException e) {
                                                // TODO Auto-generated catch block
                                                e.printStackTrace();
                                            }
                                        }


                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                            BleManager.getInstance().setMtu(HDM_bleDevice, (Throughput_sel_data_length+3), new BleMtuChangedCallback() {
                                                @Override
                                                public void onSetMTUFailure(final BleException exception) {
                                                    // Change MTU Failure
                                                    runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            addText(HDM_txt, "setMTUFailure" + exception.toString(), false);
                                                        }
                                                    });
                                                    Throughput_States = ThroughputMsg.THROUGHPUT_STATE_MTU_CHANGE_FAILURE;
                                                }

                                                @Override
                                                public void onMtuChanged(final int mtu) {
                                                    // Change MTU success，and get the MTU value supported by the current BLE device transmission.
                                                    app.setReConnectMtu(mtu);
                                                    runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            addText(HDM_txt, "MtuChanged" + mtu, false);
                                                        }
                                                    });
                                                    Throughput_States = ThroughputMsg.THROUGHPUT_STATE_MTU_CHANGE_SCCESS;
                                                }
                                            });


                                            while (Throughput_States != ThroughputMsg.THROUGHPUT_STATE_MTU_CHANGE_SCCESS) {
                                                if (Throughput_States == ThroughputMsg.THROUGHPUT_STATE_MTU_CHANGE_FAILURE) {
                                                    //MTU Change failure, APP actively disconnects DUT connection.
                                                    if (BleManager.getInstance().isConnected(HDM_bleDevice)) {
                                                        BleManager.getInstance().disconnect(HDM_bleDevice);
                                                    }
                                                    try {
                                                        Thread.sleep(ThroughputMsg.THROUGHPUT_NEXT_PROCCESS_TIME_MS);
                                                        //Thread.sleep(10000);
                                                    } catch (InterruptedException e) {
                                                        // TODO Auto-generated catch block
                                                        e.printStackTrace();
                                                    }
                                                    break;
                                                }

                                                try {
                                                    Thread.sleep(1);
                                                } catch (InterruptedException e) {
                                                    // TODO Auto-generated catch block
                                                    e.printStackTrace();
                                                }

                                                if ((!BleManager.getInstance().isConnected(HDM_bleDevice)) || (app.getReConnectSuccessFlag())) {
                                                    //disconnect，exit while loop
                                                    try {
                                                        Thread.sleep(ThroughputMsg.THROUGHPUT_DISCONNECTED_WAIT_TIME_IN_WHILE_LOOP);
                                                    } catch (InterruptedException e) {
                                                        // TODO Auto-generated catch block
                                                        e.printStackTrace();
                                                    }
                                                    break;
                                                }
                                            } //while (Throughput_States != ThroughputMsg.STRESS_STATE_MTU_CHANGE_SCCESS)

                                            if (Throughput_States == ThroughputMsg.THROUGHPUT_STATE_MTU_CHANGE_FAILURE) {
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        addText(HDM_txt, "MTU Failure", false);
                                                    }
                                                });
                                                break;
                                            }

                                            try {
                                                Thread.sleep(500);
                                            } catch (InterruptedException e) {
                                                // TODO Auto-generated catch block
                                                e.printStackTrace();
                                            }

                                        }


                                        try {
                                            Thread.sleep(1000);
                                        } catch (InterruptedException e) {
                                            // TODO Auto-generated catch block
                                            e.printStackTrace();
                                        }


                                        // "Throughput App-Tx & Device-Rx 模式" 需先寫入"pRxtest"字串  + Throughput_sel_packet_length 字串
                                        String THROUGHPUT_TEST_aTdR_START_STRING;
                                        THROUGHPUT_TEST_aTdR_START_STRING = ThroughputMsg.THROUGHPUT_TEST_APPTX_DEVICERX_START_STRING + Integer.toString(Throughput_sel_packet_length);
                                        
                                        BleManager.getInstance().write(
                                                HDM_bleDevice,
                                                ThroughputMsg.THROUGHPUT_SERVICEUUID,
                                                ThroughputMsg.THROUGHPUT_CHARACTERISTICUUID_WRITE,
                                                HexUtil.AcsiiStringToBytes(THROUGHPUT_TEST_aTdR_START_STRING),
                                                new BleWriteCallback() {
                                                    @Override
                                                    public void onWriteSuccess(final int current, final int total, final byte[] justWrite) {
                                                        runOnUiThread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                addText(HDM_txt, "write success, current: " + current
                                                                        + " total: " + total
                                                                        //+ " justWrite: " + HexUtil.formatHexString(justWrite, true));
                                                                        + " justWrite: " + HexUtil.formatASCIIString(justWrite), false);
                                                            }
                                                        });

                                                        Throughput_States = ThroughputMsg.THROUGHPUT_STATE_SENT_STRRXDISABLE_STRING_SCCESS;
                                                    }

                                                    @Override
                                                    public void onWriteFailure(final BleException exception) {
                                                        runOnUiThread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                addText(HDM_txt, exception.toString(), false);
                                                            }
                                                        });
                                                        Throughput_States = ThroughputMsg.THROUGHPUT_STATE_SENT_STRRXDISABLE_STRING_FAILURE;
                                                    }
                                                });

                                        while (Throughput_States != ThroughputMsg.THROUGHPUT_STATE_SENT_STRRXDISABLE_STRING_SCCESS) {
                                            if (Throughput_States == ThroughputMsg.THROUGHPUT_STATE_SENT_STRRXDISABLE_STRING_FAILURE) {
                                                //更新失敗...主動斷線重連
                                                if (BleManager.getInstance().isConnected(HDM_bleDevice)) {
                                                    BleManager.getInstance().disconnect(HDM_bleDevice);
                                                }
                                                try {
                                                    Thread.sleep(ThroughputMsg.THROUGHPUT_NEXT_PROCCESS_TIME_MS);
                                                    //Thread.sleep(10000);
                                                } catch (InterruptedException e) {
                                                    // TODO Auto-generated catch block
                                                    e.printStackTrace();
                                                }
                                                break;
                                            }

                                            try {
                                                Thread.sleep(1);
                                            } catch (InterruptedException e) {
                                                // TODO Auto-generated catch block
                                                e.printStackTrace();
                                            }

                                            if ((!BleManager.getInstance().isConnected(HDM_bleDevice)) || (app.getReConnectSuccessFlag())) {
                                                //斷線，必須離開 while
                                                try {
                                                    Thread.sleep(ThroughputMsg.THROUGHPUT_DISCONNECTED_WAIT_TIME_IN_WHILE_LOOP);
                                                } catch (InterruptedException e) {
                                                    // TODO Auto-generated catch block
                                                    e.printStackTrace();
                                                }
                                                break;
                                            }
                                        } //while (Throughput_States != ThroughputMsg.THROUGHPUT_STATE_SENT_STRESSTEST_STRING_SCCESS)

                                        if (Throughput_States == ThroughputMsg.THROUGHPUT_STATE_SENT_STRRXDISABLE_STRING_FAILURE) {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    addText(HDM_txt, "Write rxDisable Failure", false);
                                                }
                                            });
                                            // save
                                            SaveText2(HDM_txt); //Download folder
                                            SaveText(HDM_txt); //APP內存
                                            break;
                                        }

                                        Log.d(ThroughputMsg.THROUGH_PROCCESS_TAG, "Throughput_States = THROUGHPUT_STATE_SENT_rxDisable_STRING_SCCESS");
                                        //write command 之前, delay 300 minsec, 若delay 時間太短會跳錯。
                                        try {
                                            Thread.sleep(1000);
                                        } catch (InterruptedException e) {
                                            // TODO Auto-generated catch block
                                            e.printStackTrace();
                                        }

                                        write_str_temp.delete(0, write_str_temp.length()); //清除內容
                                        for (char wlength = 3; wlength < Throughput_sel_data_length; wlength++) {  //3~Throughput_sel_data_length
                                            write_str_temp.append(encodeHexStr_OneByte((char) wlength));
                                        }
                                        //**修改切割資料長度
                                        BleManager.getInstance().setSplitWriteNum(Throughput_sel_data_length);
                                        //**修改切割資料後，傳輸間的時間間隔。
                                        BleManager.getInstance().setInterval_Between_TwoPackage((long) Throughput_sel_sent_packet_interval_ms);

                                        Throughput_States = ThroughputMsg.THROUGHPUT_STATE_SENT_DATA;
                                        Throughput_total_transferred_data_len = 0;
                                        Throughput_total_receiver_data_len = 0;

                                        //1048712/244 = 4298
                                        //1048712/20 = 52435.6
                                        int loop_end = (Throughput_sel_packet_length / Throughput_sel_data_length);
                                        if (Throughput_sel_packet_length > (loop_end * Throughput_sel_data_length)) {
                                            loop_end += 1;
                                        }
                                        int wlength = 0;
                                        write_str_final.delete(0, write_str_final.length()); //清除內容
                                        write_str_final.append(encodeHexStr_OneByte((char) 0xFF));
                                        write_str_final.append(encodeHexStr_OneByte((char) ((wlength >> 8) & 0xff))); //MSB
                                        write_str_final.append(encodeHexStr_OneByte((char) (wlength & 0xff)));//LSB
                                        write_str_final.append(write_str_temp);
                                        for (wlength = 1; wlength < loop_end; wlength++) {

                                            write_str_final.append(encodeHexStr_OneByte((char) 0xFF));
                                            write_str_final.append(encodeHexStr_OneByte((char) ((wlength >> 8) & 0xff))); //MSB
                                            write_str_final.append(encodeHexStr_OneByte((char) (wlength & 0xff)));//LSB
                                            if (((wlength + 1) * Throughput_sel_data_length) > Throughput_sel_packet_length) {
                                                //最後一筆資料未整除
                                                int dataremainder = Throughput_sel_packet_length - (wlength * Throughput_sel_data_length);
                                                write_str_remainder.delete(0, write_str_remainder.length()); //清除內容
                                                for (char i = 3; i < dataremainder; i++) {  //3~dataremainder
                                                    write_str_remainder.append(encodeHexStr_OneByte((char) i));
                                                }
                                                write_str_final.append(write_str_remainder);

                                            } else {
                                                write_str_final.append(write_str_temp);
                                            }
                                            //}
                                        }

                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                addText(HDM_txt, "======APP Tx & Device Rx Result======", true);
                                                addText(HDM_txt, "TX phy: " + Integer.toString(app.getReConnectTxPhy()) + "M, RX phy: " + Integer.toString(app.getReConnectRxPhy()) + "M", true);
                                                addText(HDM_txt, "Packet Length: " + Integer.toString(Throughput_sel_data_length) + " Byte", true);
                                                switch (app.getConnectInterval()) {
                                                    case 0:
                                                        addText(HDM_txt, "connection interval: CONNECTION_PRIORITY_BALANCED (30ms–50ms)", true);
                                                        break;
                                                    case 1:
                                                        addText(HDM_txt, "connection interval: CONNECTION_PRIORITY_HIGH (11.25ms–15ms)", true);
                                                        break;
                                                    case 2:
                                                        addText(HDM_txt, "connection interval: CONNECTION_PRIORITY_LOW_POWER (100ms–125ms)", true);
                                                        break;
                                                }
                                            }
                                        });

                                        //紀錄開始時間
                                        Throughput_StartDate = new Date(System.currentTimeMillis());
                                        throughput_time_diff_index = 0;
                                        BleManager.getInstance().write(
                                                HDM_bleDevice,
                                                ThroughputMsg.THROUGHPUT_SERVICEUUID,
                                                ThroughputMsg.THROUGHPUT_CHARACTERISTICUUID_WRITE,
                                                HexUtil.hexStringToBytes(write_str_final.toString()),
                                                new BleWriteCallback() {
                                                    @Override
                                                    public void onWriteSuccess(final int current, final int total, final byte[] justWrite) {
    /*
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        addText(HDM_txt, "write success, current: " + current
                                                                + " total: " + total
                                                                + " justWrite: " + HexUtil.formatHexString(justWrite, true), false);
                                                    }
                                                });
    */
                                                        throughput_time_diff[throughput_time_diff_index++] = new Date(System.currentTimeMillis());

                                                        //Calculate the progress percentage
                                                        Throughput_percentage_val = (current * 100) / (total);
                                                        if ((Throughput_percentage_preval / 20) != (Throughput_percentage_val / 20)) { //1step = 10% show to panel,否則會拖到TX速度
                                                            // Change progress bar
                                                            runOnUiThread(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    changeProgressBar(PBar_thp, Throughput_percentage_val);
                                                                }
                                                            });
                                                            Throughput_percentage_preval = Throughput_percentage_val;
                                                        }

                                                        Throughput_total_transferred_data_len += justWrite.length;
                                                        if (current == total) {
                                                            write_one_package_end_falg = 1;
                                                            write_success_falg = 1;
                                                        }
                                                    }

                                                    @Override
                                                    public void onWriteFailure(final BleException exception) {
                                                        //final String failure_item = "BleException { code=102, description='gatt writeCharacteristic fail'}";
                                                        //BleException { code=102, description='gatt writeCharacteristic fail'}
                                                        // 發送數據到設備失敗
                                                        runOnUiThread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                addText(HDM_txt, exception.toString(), false);
                                                            }
                                                        });

                                                        write_one_package_end_falg = 1;
                                                        write_success_falg = 0;
                                                    }
                                                });


                                        while ((write_one_package_end_falg == (int) 0)) {
                                            try {
                                                Thread.sleep(1);
                                            } catch (InterruptedException e) {
                                                // TODO Auto-generated catch block
                                                e.printStackTrace();
                                            }

                                            if (!BleManager.getInstance().isConnected(HDM_bleDevice)) {
                                                //disconnect，exit while loop((write_one_package_end_falg == (int)0))
                                                break;
                                            }
                                        }
                                        Throughput_StopDate = new Date(System.currentTimeMillis());

                                        long difft = 0;
                                        long avg_difft = 0;
                                        for (int tloop = 0; tloop < (throughput_time_diff_index - 1); tloop++) {
                                            difft = throughput_time_diff[tloop + 1].getTime() - throughput_time_diff[tloop].getTime();
                                            avg_difft += difft;
    /*
                                            final int finalTloop = tloop;
                                            final long finalDifft = difft;
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    addText(HDM_txt, "index: " + Integer.toString(finalTloop)
                                                            + " time: " + Integer.toString((int) finalDifft) +"ms", false);
                                                }
                                            });
    */
                                        }
                                        avg_difft = avg_difft * 1000;   //ms->us
                                        avg_difft = (avg_difft / (throughput_time_diff_index - 1));
                                        final int showtime = (int) avg_difft;

                                        write_one_package_end_falg = 0;
                                        write_success_falg = 0;

                                        //stop flag
                                        //if (Throughput_stop_test_flag == 1) {
                                        //    break;
                                        //}

                                        final long diff = Throughput_StopDate.getTime() - Throughput_StartDate.getTime();

//                                        final int fota_use_time_mm = (int) (diff / 1000) / 60;
//                                        final int fota_use_time_ss = (int) (diff / 1000) - (int) fota_use_time_mm * 60;
//
//                                        runOnUiThread(new Runnable() {
//                                            @Override
//                                            public void run() {
//                                                addText(HDM_txt, "use time(min:sec) = " + fota_use_time_mm + " : " + fota_use_time_ss, true);
//                                            }
//                                        });

                                        try {
                                            Thread.sleep(1000);
                                        } catch (InterruptedException e) {
                                            // TODO Auto-generated catch block
                                            e.printStackTrace();
                                        }

                                        //long double datarate = ((totalDataCount * 8)/(totalTime_ms/1000.0000))/1024.00; // BPS

                                        final int throughput_use_time_only_ms = (int) (diff);
                                        final double throughput_use_time_only_ss = (double) ((double) diff / 1000);
                                        final double datarate = ((((double) Throughput_total_transferred_data_len * 1000 * 8) / (double) throughput_use_time_only_ms) / (double) 1024);  //kbps  (bit per second)

                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                addText(HDM_txt, "Average write one packet time: = " + Integer.toString(showtime) + "us", false);
                                                addText(HDM_txt, "Total Data Length  = " + Integer.toString(Throughput_total_transferred_data_len) + " bytes", true);
                                                addText(HDM_txt, "Total Time  = " + Double.toString(throughput_use_time_only_ss) + " sec", true);
                                                addText(HDM_txt, "Data Rate  = " + Double.toString(datarate) + " kbps", true);
                                                addText(HDM_txt, "====================================", true);
                                                addText(HDM_txt, "", true);
                                            }
                                        });

                                        try {
                                            Thread.sleep(5000);
                                        } catch (InterruptedException e) {
                                            // TODO Auto-generated catch block
                                            e.printStackTrace();
                                        }

                                        if ((!THROUGTPUT_DO_AUTO_TEST_COMBINATION) || (app.getReleaseToCTMR())) {
                                            // save
                                            SaveText2(HDM_txt); //Download folder
                                            SaveText(HDM_txt); //APP

                                            //Keep the result on the phone screen.
//                                            runOnUiThread(new Runnable() {
//                                                @Override
//                                                public void run() {
//                                                    addText(HDM_txt, "2======APP Tx & Device Rx Result======", true);
//                                                    addText(HDM_txt, "TX phy: " + Integer.toString(app.getReConnectTxPhy()) + "M, RX phy: " + Integer.toString(app.getReConnectRxPhy()) + "M", true);
//                                                    addText(HDM_txt, "Packet Length: " + Integer.toString(Throughput_sel_data_length) + " Byte", true);
//                                                    switch (app.getConnectInterval()) {
//                                                        case 0:
//                                                            addText(HDM_txt, "connection interval: CONNECTION_PRIORITY_BALANCED (30ms–50ms)", true);
//                                                            break;
//                                                        case 1:
//                                                            addText(HDM_txt, "connection interval: CONNECTION_PRIORITY_HIGH (11.25ms–15ms)", true);
//                                                            break;
//                                                        case 2:
//                                                            addText(HDM_txt, "connection interval: CONNECTION_PRIORITY_LOW_POWER (100ms–125ms)", true);
//                                                            break;
//                                                    }
//                                                    addText(HDM_txt, "Average write one packet time: = " + Integer.toString(showtime) + "us", false);
//                                                    addText(HDM_txt, "Total Data Length  = " + Integer.toString(Throughput_total_transferred_data_len) + " bytes", true);
//                                                    addText(HDM_txt, "Total Time  = " + Double.toString(throughput_use_time_only_ss) + " sec", true);
//                                                    addText(HDM_txt, "Data Rate  = " + Double.toString(datarate) + " kbps", true);
//                                                    addText(HDM_txt, "====================================", true);
//                                                    addText(HDM_txt, "", true);
//                                                }
//                                            });
                                        }
                                    }
                                }
                            }
                        }while(((Throughput_stop_test_flag==0) && (THROUGTPUT_AUTO_TEST_OPTION_SEL==5)));
                        if ((THROUGTPUT_DO_AUTO_TEST_COMBINATION) && (!app.getReleaseToCTMR())) {
                            // save
                            SaveText2(HDM_txt); //Download folder
                            SaveText(HDM_txt); //APP
                        }
                        Throughput_States = ThroughputMsg.THROUGHPUT_STATE_IDAL;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                btn_runthroughput.setText(getActivity().getString(R.string.ate_run));
                                btn_san_ble.setEnabled(true);
                            }
                        });

                    }
                    break;

                    case ThroughputMsg.THROUGHPUT_TEST_APPRX_DEVICETX: {

                        final BleDevice HDM_bleDevice = _connectBleDevice; //這裡銜接上藍牙裝置
                        final TextView HDM_txt = (TextView) txtlog;

                        Throughput_States = ThroughputMsg.THROUGHPUT_STATE_IDAL;
                        Throughput_total_receiver_data_len = 0;

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            //BleManager.getInstance().setMtu(HDM_bleDevice, 255, new BleMtuChangedCallback() {
                            BleManager.getInstance().setMtu(HDM_bleDevice, (Throughput_sel_data_length + 3), new BleMtuChangedCallback() {
                                @Override
                                public void onSetMTUFailure(final BleException exception) {
                                    // Change MTU Failure
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            addText(HDM_txt, "setMTUFailure" + exception.toString(), false);
                                        }
                                    });
                                    Throughput_States = ThroughputMsg.THROUGHPUT_STATE_MTU_CHANGE_FAILURE;
                                }

                                @Override
                                public void onMtuChanged(final int mtu) {
                                    // Change MTU success，and get the MTU value supported by the current BLE device transmission.
                                    app.setReConnectMtu(mtu);
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            addText(HDM_txt, "MtuChanged" + mtu, false);
                                        }
                                    });
                                    Throughput_States = ThroughputMsg.THROUGHPUT_STATE_MTU_CHANGE_SCCESS;
                                }
                            });


                            while (Throughput_States != ThroughputMsg.THROUGHPUT_STATE_MTU_CHANGE_SCCESS) {
                                if (Throughput_States == ThroughputMsg.THROUGHPUT_STATE_MTU_CHANGE_FAILURE) {
                                    //更新失敗...主動斷線重連
                                    if (BleManager.getInstance().isConnected(HDM_bleDevice)) {
                                        BleManager.getInstance().disconnect(HDM_bleDevice);
                                    }
                                    try {
                                        Thread.sleep(ThroughputMsg.THROUGHPUT_NEXT_PROCCESS_TIME_MS);
                                    } catch (InterruptedException e) {
                                        // TODO Auto-generated catch block
                                        e.printStackTrace();
                                    }
                                    break;
                                }

                                try {
                                    Thread.sleep(1);
                                } catch (InterruptedException e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                }

                                if ((!BleManager.getInstance().isConnected(HDM_bleDevice)) || (app.getReConnectSuccessFlag())) {
                                    //disconnect，exit while loop
                                    try {
                                        Thread.sleep(ThroughputMsg.THROUGHPUT_DISCONNECTED_WAIT_TIME_IN_WHILE_LOOP);
                                    } catch (InterruptedException e) {
                                        // TODO Auto-generated catch block
                                        e.printStackTrace();
                                    }
                                    break;
                                }
                            } //while (Throughput_States != ThroughputMsg.THROUGHPUT_STATE_MTU_CHANGE_SCCESS)

                            if (Throughput_States == ThroughputMsg.THROUGHPUT_STATE_MTU_CHANGE_FAILURE) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        addText(HDM_txt, "MTU Failure", false);
                                    }
                                });
                                break;
                            }

                            Log.d(ThroughputMsg.THROUGH_PROCCESS_TAG, "THROUGHPUT_States = THROUGHPUT_STATE_MTU_CHANGE_SCCESS");

                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                        }

                        //notify
                        BleManager.getInstance().notify(
                                HDM_bleDevice,
                                ThroughputMsg.THROUGHPUT_SERVICEUUID,
                                ThroughputMsg.THROUGHPUT_NOTIFY,
                                new BleNotifyCallback() {
                                    @Override
                                    public void onNotifySuccess() {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                addText(HDM_txt, "notify success", false);
                                            }
                                        });
                                        Throughput_States = ThroughputMsg.THROUGHPUT_STATE_NOTIFY_OPEN_SCCESS;
                                    }

                                    @Override
                                    public void onNotifyFailure(final BleException exception) {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                addText(HDM_txt, exception.toString(), false);
                                            }
                                        });
                                        Throughput_States = ThroughputMsg.THROUGHPUT_STATE_NOTIFY_OPEN_FAILURE;
                                    }

                                    @Override
                                    public void onCharacteristicChanged(final byte[] data) {
                                        final int index_temp = (int) (((data[1] & 0xFF) << 8) + (data[2] & 0xFF));

                                        if (Throughput_total_receiver_data_len == 0) {
                                            Throughput_StartDate = new Date(System.currentTimeMillis());
                                            if (ThroughputMsg.THROUGH_SHOW_LOG_TO_PANEL) {
                                                throughput_savelog_time_start = new Date(System.currentTimeMillis());
                                            }
                                            Throughput_sel_data_length = data.length;
                                        }

                                        if (ThroughputMsg.THROUGH_SHOW_LOG_TO_PANEL) {
                                            throughput_savelog_time_arrival = new Date(System.currentTimeMillis());
                                            ;
                                            throughput_savelog_time_diff = throughput_savelog_time_arrival.getTime() - throughput_savelog_time_start.getTime();
                                            if ((throughput_savelog_time_diff / 1000) >= ThroughputMsg.THROUGHPUT_LOG_SAVE_TIME)// THROUGHPUT_LOG_SAVE_TIME sec to save file
                                            {
                                                throughput_savelog_time_start = throughput_savelog_time_arrival;
                                                throughput_savelog_time_diff = 0;
                                                // save
                                                SaveText2(HDM_txt); //Download folder
                                                SaveText(HDM_txt); //APP
                                            }
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    addText(HDM_txt, "Data : " + HexUtil.formatHexString(data, true), false);
                                                }
                                            });
                                        }

                                        Throughput_total_receiver_data_len += data.length;
                                        // Calculate the progress percentage
                                        Throughput_percentage_val = (Throughput_total_receiver_data_len * 100) / (Throughput_sel_packet_length);

                                        if (Throughput_total_receiver_data_len == Throughput_sel_packet_length) {
                                            Throughput_StopDate = new Date(System.currentTimeMillis());
                                            Throughput_States = ThroughputMsg.THROUGHPUT_STATE_RECEIVER_DATA_DONE;
                                        } else if (Throughput_total_receiver_data_len > Throughput_sel_packet_length) {
                                            Throughput_States = ThroughputMsg.THROUGHPUT_STATE_RECEIVER_DATA_DONE;
                                        }


                                        if ((Throughput_percentage_preval) != (Throughput_percentage_val)) { //1step = 1% show to panel
                                            //Change progress bar
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    changeProgressBar(PBar_thp, Throughput_percentage_val);
                                                }
                                            });
                                            Throughput_percentage_preval = Throughput_percentage_val;
                                        }
                                    }
                                });


                        while (Throughput_States != ThroughputMsg.THROUGHPUT_STATE_NOTIFY_OPEN_SCCESS) {
                            if (Throughput_States == ThroughputMsg.THROUGHPUT_STATE_NOTIFY_OPEN_FAILURE) {
                                //Notify open failure, APP actively disconnects DUT connection.
                                if (BleManager.getInstance().isConnected(HDM_bleDevice)) {
                                    BleManager.getInstance().disconnect(HDM_bleDevice);
                                }
                                try {
                                    Thread.sleep(ThroughputMsg.THROUGHPUT_NEXT_PROCCESS_TIME_MS);
                                } catch (InterruptedException e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                }
                                break;
                            }

                            try {
                                Thread.sleep(1);
                            } catch (InterruptedException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }

                            if (!BleManager.getInstance().isConnected(HDM_bleDevice)) {
                                //disconnect，exit while loop
                                try {
                                    Thread.sleep(ThroughputMsg.THROUGHPUT_DISCONNECTED_WAIT_TIME_IN_WHILE_LOOP);
                                } catch (InterruptedException e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                }
                                break;
                            }
                        } //while (Throughput_States != ThroughputMsg.THROUGHPUT_STATE_NOTIFY_OPEN_SCCESS)

                        if (Throughput_States == ThroughputMsg.THROUGHPUT_STATE_NOTIFY_OPEN_FAILURE) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    addText(HDM_txt, "Notify open Failure", false);
                                }
                            });
                            // save
                            SaveText2(HDM_txt); //Download folder
                            SaveText(HDM_txt); //APP
                            break;
                        }

                        Log.d(ThroughputMsg.THROUGH_PROCCESS_TAG, "THROUGH_States = THROUGHPUT_STATE_NOTIFY_OPEN_SCCESS");

                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }


                        //CONNECTION_PRIORITY_BALANCED = 0; CONNECTION_PRIORITY_HIGH = 1; CONNECTION_PRIORITY_LOW_POWER = 2;
                        int physt, physp;
                        int connectionIntervalst, connectionIntervalsp;
                        int mtuIndexst, mtuIndexsp;

                        int[] Throughput_sel_data_length_ate_mtu_opt = new int[0];

                        if ((THROUGTPUT_DO_AUTO_TEST_COMBINATION) && (!app.getReleaseToCTMR())) {//測試所有組合
                            String[] mtuopt;
                            String str_mtu = et_ate_mtu_test_option.getText().toString();

                            if (THROUGTPUT_AUTO_TEST_OPTION_SEL == 1) { //ALL option
                                if (TextUtils.isEmpty(str_mtu)) {
                                    mtuopt = null;
                                } else {
                                    mtuopt = str_mtu.split(",");
                                }
                                Throughput_sel_data_length_ate_mtu_opt = new int[mtuopt.length];
                                for (int mtuoptidx = 0; mtuoptidx < mtuopt.length; mtuoptidx++) {
                                    Throughput_sel_data_length_ate_mtu_opt[mtuoptidx] = Integer.parseInt(mtuopt[mtuoptidx]);
                                }
                                mtuIndexst = 0;
                                //mtuIndexsp = 2;
                                mtuIndexsp = mtuopt.length - 1;
                                physt = 1;
                                physp = 2;
                                connectionIntervalst = 0;
                                connectionIntervalsp = 2;
                            } else if (THROUGTPUT_AUTO_TEST_OPTION_SEL == 2) { //PHY
                                mtuIndexst = 1;
                                mtuIndexsp = 1;
                                physt = 1;
                                physp = 2;
                                connectionIntervalst = app.getConnectInterval();
                                connectionIntervalsp = app.getConnectInterval();
                            } else if (THROUGTPUT_AUTO_TEST_OPTION_SEL == 3) { //connection interval
                                mtuIndexst = 1;
                                mtuIndexsp = 1;
                                physt = app.getReConnectTxPhy();
                                physp = app.getReConnectTxPhy();
                                connectionIntervalst = 0;
                                connectionIntervalsp = 2;
                            } else {// if(THROUGTPUT_AUTO_TEST_OPTION_SEL==3){ //MTU
                                if (TextUtils.isEmpty(str_mtu)) {
                                    mtuopt = null;
                                } else {
                                    mtuopt = str_mtu.split(",");
                                }
                                Throughput_sel_data_length_ate_mtu_opt = new int[mtuopt.length];
                                for (int mtuoptidx = 0; mtuoptidx < mtuopt.length; mtuoptidx++) {
                                    Throughput_sel_data_length_ate_mtu_opt[mtuoptidx] = Integer.parseInt(mtuopt[mtuoptidx]);
                                }
                                mtuIndexst = 0;
                                //mtuIndexsp = 2;
                                mtuIndexsp = mtuopt.length - 1;
                                physt = app.getReConnectTxPhy();
                                physp = app.getReConnectTxPhy();
                                connectionIntervalst = app.getConnectInterval();
                                connectionIntervalsp = app.getConnectInterval();
                            }

                        } else {
                            mtuIndexst = 1;
                            mtuIndexsp = 1;
                            physt = app.getReConnectTxPhy();
                            physp = app.getReConnectTxPhy();
                            connectionIntervalst = app.getConnectInterval();
                            connectionIntervalsp = app.getConnectInterval();
                        }

                        do{
                            for (int mtuIndex = mtuIndexst; mtuIndex <= mtuIndexsp; mtuIndex++) {
                                for (int testphy = physt; testphy <= physp; testphy++) { //Phy= 1M/2M
                                    for (int testonn = connectionIntervalst; testonn <= connectionIntervalsp; testonn++) { //CONNECTION_PRIORITY_BALANCED = 0; CONNECTION_PRIORITY_HIGH = 1; CONNECTION_PRIORITY_LOW_POWER = 2;
                                        Throughput_total_receiver_data_len = 0; //clear receiver len parameter
                                        if ((THROUGTPUT_DO_AUTO_TEST_COMBINATION) && (!app.getReleaseToCTMR())) {

                                            try {
                                                Thread.sleep(500);
                                            } catch (InterruptedException e) {
                                                // TODO Auto-generated catch block
                                                e.printStackTrace();
                                            }

                                            if ((THROUGTPUT_AUTO_TEST_OPTION_SEL == 1) || (THROUGTPUT_AUTO_TEST_OPTION_SEL == 4)) {
                                                Throughput_sel_data_length = Throughput_sel_data_length_ate_mtu_opt[mtuIndex];
                                            } else {
                                                Throughput_sel_data_length = Integer.parseInt(et_datamtulen.getText().toString().trim());
                                            }

                                            //BleManager.getInstance().setMtu(HDM_bleDevice, 255, new BleMtuChangedCallback() {
                                            BleManager.getInstance().setMtu(HDM_bleDevice, (Throughput_sel_data_length + 3), new BleMtuChangedCallback() {
                                                @Override
                                                public void onSetMTUFailure(final BleException exception) {
                                                    // Change MTU Failure
                                                    runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            addText(HDM_txt, "setMTUFailure" + exception.toString(), false);
                                                        }
                                                    });
                                                    Throughput_States = ThroughputMsg.THROUGHPUT_STATE_MTU_CHANGE_FAILURE;
                                                }

                                                @Override
                                                public void onMtuChanged(final int mtu) {
                                                    // Change MTU success，and get the MTU value supported by the current BLE device transmission.
                                                    app.setReConnectMtu(mtu);
                                                    runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            addText(HDM_txt, "MtuChanged" + mtu, false);
                                                        }
                                                    });
                                                    Throughput_States = ThroughputMsg.THROUGHPUT_STATE_MTU_CHANGE_SCCESS;
                                                }
                                            });


                                            while (Throughput_States != ThroughputMsg.THROUGHPUT_STATE_MTU_CHANGE_SCCESS) {
                                                if (Throughput_States == ThroughputMsg.THROUGHPUT_STATE_MTU_CHANGE_FAILURE) {
                                                    //MTU Change failure, APP actively disconnects DUT connection.
                                                    if (BleManager.getInstance().isConnected(HDM_bleDevice)) {
                                                        BleManager.getInstance().disconnect(HDM_bleDevice);
                                                    }
                                                    try {
                                                        Thread.sleep(ThroughputMsg.THROUGHPUT_NEXT_PROCCESS_TIME_MS);
                                                    } catch (InterruptedException e) {
                                                        // TODO Auto-generated catch block
                                                        e.printStackTrace();
                                                    }
                                                    break;
                                                }

                                                try {
                                                    Thread.sleep(1);
                                                } catch (InterruptedException e) {
                                                    // TODO Auto-generated catch block
                                                    e.printStackTrace();
                                                }

                                                if ((!BleManager.getInstance().isConnected(HDM_bleDevice)) || (app.getReConnectSuccessFlag())) {
                                                    //disconnect，exit while loop
                                                    try {
                                                        Thread.sleep(ThroughputMsg.THROUGHPUT_DISCONNECTED_WAIT_TIME_IN_WHILE_LOOP);
                                                    } catch (InterruptedException e) {
                                                        // TODO Auto-generated catch block
                                                        e.printStackTrace();
                                                    }
                                                    break;
                                                }
                                            } //while (Throughput_States != ThroughputMsg.THROUGHPUT_STATE_MTU_CHANGE_SCCESS)

                                            if (Throughput_States == ThroughputMsg.THROUGHPUT_STATE_MTU_CHANGE_FAILURE) {
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        addText(HDM_txt, "MTU Failure", false);
                                                    }
                                                });
                                                break;
                                            }

                                            Log.d(ThroughputMsg.THROUGH_PROCCESS_TAG, "THROUGHPUT_States = THROUGHPUT_STATE_MTU_CHANGE_SCCESS");

                                            try {
                                                Thread.sleep(500);
                                            } catch (InterruptedException e) {
                                                // TODO Auto-generated catch block
                                                e.printStackTrace();
                                            }


                                            //Change PHY
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                BleManager.getInstance().setPreferredPhy(HDM_bleDevice, testphy, testphy, 0);
                                                app.setReConnectTxPhy(testphy);
                                                app.setReConnectRxPhy(testphy);
                                            } else {
                                                //no PHY 2M
                                                Toast.makeText(getActivity(), getString(R.string.only_support_1M), Toast.LENGTH_LONG).show();
                                            }

                                            try {
                                                Thread.sleep(1000);
                                            } catch (InterruptedException e) {
                                                // TODO Auto-generated catch block
                                                e.printStackTrace();
                                            }

                                            //change connect interval
                                            //CONNECTION_PRIORITY_BALANCED = 0; CONNECTION_PRIORITY_HIGH = 1; CONNECTION_PRIORITY_LOW_POWER = 2;
                                            //時間由短至長測試
                                            if (testonn == 0) {
                                                BleManager.getInstance().requestConnectionPriority(HDM_bleDevice, 1);
                                                app.setConnectInterval(1);
                                            } else if (testonn == 1) {
                                                BleManager.getInstance().requestConnectionPriority(HDM_bleDevice, 0);
                                                app.setConnectInterval(0);
                                            } else {
                                                BleManager.getInstance().requestConnectionPriority(HDM_bleDevice, testonn);
                                                app.setConnectInterval(testonn);
                                            }


                                            try {
                                                Thread.sleep(2000);
                                            } catch (InterruptedException e) {
                                                // TODO Auto-generated catch block
                                                e.printStackTrace();
                                            }

                                        }//if ((THROUGTPUT_DO_AUTO_TEST_COMBINATION) && (!app.getReleaseToCTMR()))



                                        try {
                                            Thread.sleep(1000);
                                        } catch (InterruptedException e) {
                                            // TODO Auto-generated catch block
                                            e.printStackTrace();
                                        }


                                        // "Throughput App-Rx and Device-Tx 模式" 需先寫入"pTxtest"字串  + Throughput_sel_packet_length 字串
                                        BleManager.getInstance().write(
                                                HDM_bleDevice,
                                                ThroughputMsg.THROUGHPUT_SERVICEUUID,
                                                ThroughputMsg.THROUGHPUT_CHARACTERISTICUUID_WRITE,
                                                HexUtil.AcsiiStringToBytes(ThroughputMsg.THROUGHPUT_TEST_APPRX_DEVICETX_START_STRING + Integer.toString(Throughput_sel_packet_length)),
                                                new BleWriteCallback() {
                                                    @Override
                                                    public void onWriteSuccess(final int current, final int total, final byte[] justWrite) {
                                                        runOnUiThread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                addText(HDM_txt, "write success, current: " + current
                                                                        + " total: " + total
                                                                        //+ " justWrite: " + HexUtil.formatHexString(justWrite, true));
                                                                        + " justWrite: " + HexUtil.formatASCIIString(justWrite), false);
                                                            }
                                                        });

                                                        Throughput_States = ThroughputMsg.THROUGHPUT_STATE_SENT_STRPTXTEST_STRING_SCCESS;
                                                    }

                                                    @Override
                                                    public void onWriteFailure(final BleException exception) {
                                                        runOnUiThread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                addText(HDM_txt, exception.toString(), false);
                                                            }
                                                        });
                                                        Throughput_States = ThroughputMsg.THROUGHPUT_STATE_SENT_STRPTXTEST_STRING_FAILURE;
                                                    }
                                                });

                                        while (Throughput_States != ThroughputMsg.THROUGHPUT_STATE_SENT_STRPTXTEST_STRING_SCCESS) {
                                            if (Throughput_States == ThroughputMsg.THROUGHPUT_STATE_SENT_STRPTXTEST_STRING_FAILURE) {
                                                //更新失敗...主動斷線重連
                                                if (BleManager.getInstance().isConnected(HDM_bleDevice)) {
                                                    BleManager.getInstance().disconnect(HDM_bleDevice);
                                                }
                                                try {
                                                    Thread.sleep(ThroughputMsg.THROUGHPUT_NEXT_PROCCESS_TIME_MS);
                                                } catch (InterruptedException e) {
                                                    // TODO Auto-generated catch block
                                                    e.printStackTrace();
                                                }
                                                break;
                                            }

                                            try {
                                                Thread.sleep(1);
                                            } catch (InterruptedException e) {
                                                // TODO Auto-generated catch block
                                                e.printStackTrace();
                                            }

                                            if ((!BleManager.getInstance().isConnected(HDM_bleDevice)) || (app.getReConnectSuccessFlag())) {
                                                //斷線，必須離開 while
                                                try {
                                                    Thread.sleep(ThroughputMsg.THROUGHPUT_DISCONNECTED_WAIT_TIME_IN_WHILE_LOOP);
                                                } catch (InterruptedException e) {
                                                    // TODO Auto-generated catch block
                                                    e.printStackTrace();
                                                }
                                                break;
                                            }
                                        } //while (Throughput_States != ThroughputMsg.THROUGHPUT_STATE_SENT_STRPTXTEST_STRING_SCCESS)

                                        if (Throughput_States == ThroughputMsg.THROUGHPUT_STATE_SENT_STRPTXTEST_STRING_FAILURE) {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    addText(HDM_txt, "Write pTxtest Failure", false);
                                                }
                                            });
                                            // save
                                            SaveText2(HDM_txt); //Download folder
                                            SaveText(HDM_txt); //APP
                                            break;
                                        }

                                        Log.d(ThroughputMsg.THROUGH_PROCCESS_TAG, "Throughput_States = THROUGHPUT_STATE_SENT_pTxtest_STRING_SCCESS");
                                        //write command 之前, delay 300 minsec, 若delay 時間太短會跳錯。
                                        try {
                                            Thread.sleep(1000);
                                        } catch (InterruptedException e) {
                                            // TODO Auto-generated catch block
                                            e.printStackTrace();
                                        }

                                        while (Throughput_States != ThroughputMsg.THROUGHPUT_STATE_RECEIVER_DATA_DONE) {
                                            try {
                                                Thread.sleep(1);
                                            } catch (InterruptedException e) {
                                                // TODO Auto-generated catch block
                                                e.printStackTrace();
                                            }
                                        }

        /*
                                        //close Notify
                                        BleManager.getInstance().stopNotify(
                                                HDM_bleDevice,
                                                ThroughputMsg.THROUGHPUT_SERVICEUUID,
                                                ThroughputMsg.THROUGHPUT_CHARACTERISTICUUID_NOTIFY);

                                        try {
                                            Thread.sleep(500);
                                        } catch (InterruptedException e) {
                                            // TODO Auto-generated catch block
                                            e.printStackTrace();
                                        }

        */
                                        if (ThroughputMsg.THROUGHPUT_TEST_APPRX_DEVICETX_DISCONNECTED) {
                                            //主動斷線
                                            if (BleManager.getInstance().isConnected(HDM_bleDevice)) {
                                                BleManager.getInstance().disconnect(HDM_bleDevice);
                                            }
                                        }


                                        final long diff = Throughput_StopDate.getTime() - Throughput_StartDate.getTime();
//                                        final int fota_use_time_mm = (int) (diff / 1000) / 60;
//                                        final int fota_use_time_ss = (int) (diff / 1000) - (int) fota_use_time_mm * 60;
//
//                                        runOnUiThread(new Runnable() {
//                                            @Override
//                                            public void run() {
//                                                addText(HDM_txt, "use time(min:sec) = " + fota_use_time_mm + " : " + fota_use_time_ss, true);
//                                            }
//                                        });

                                        try {
                                            Thread.sleep(1000);
                                        } catch (InterruptedException e) {
                                            // TODO Auto-generated catch block
                                            e.printStackTrace();
                                        }

                                        //final int fota_use_time_only_ss = (int) (diff / 1000);
                                        //final double datarate = (((Throughput_total_receiver_data_len * 8) / (double)fota_use_time_only_ss)/1024);  //kbps  (bit per second)

                                        final int throughput_use_time_only_ms = (int) (diff);
                                        final double throughput_use_time_only_ss = (double) ((double) diff / 1000);
                                        //final double datarate = (((Throughput_total_transferred_data_len * 8) / (double)fota_use_time_only_ss) / 1024);  //kbps  (bit per second)
                                        //if (Throughput_total_receiver_data_len >= Throughput_sel_packet_length) {
                                        //    Throughput_total_receiver_data_len = Throughput_sel_packet_length;
                                        //}
                                        final double datarate = ((((double) Throughput_total_receiver_data_len * 1000 * 8) / (double) throughput_use_time_only_ms) / (double) 1024);  //kbps  (bit per second)
                                        //final double datarate = ((((double) Throughput_sel_packet_length * 1000 * 8) / (double) throughput_use_time_only_ms) / (double) 1024);  //kbps  (bit per second)

                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                addText(HDM_txt, "======APP Rx & Device Tx Result======", true);
                                                addText(HDM_txt, "TX phy: " + Integer.toString(app.getReConnectTxPhy()) + "M, RX phy: " + Integer.toString(app.getReConnectRxPhy()) + "M", true);
                                                addText(HDM_txt, "Packet Length: " + Integer.toString(Throughput_sel_data_length), true);
                                                switch (app.getConnectInterval()) {
                                                    case 0:
                                                        addText(HDM_txt, "connection interval: CONNECTION_PRIORITY_BALANCED (30ms–50ms)", true);
                                                        break;
                                                    case 1:
                                                        addText(HDM_txt, "connection interval: CONNECTION_PRIORITY_HIGH (11.25ms–15ms)", true);
                                                        break;
                                                    case 2:
                                                        addText(HDM_txt, "connection interval: CONNECTION_PRIORITY_LOW_POWER (100ms–125ms)", true);
                                                        break;
                                                }
                                                addText(HDM_txt, "Total Data Length  = " + Integer.toString(Throughput_total_receiver_data_len) + " bytes", true);
                                                addText(HDM_txt, "Total Time  = " + Double.toString(throughput_use_time_only_ss) + " sec", true);
                                                addText(HDM_txt, "Data Rate  = " + Double.toString(datarate) + " kbps", true);
                                                addText(HDM_txt, "====================================", true);
                                                addText(HDM_txt, "", true);
                                            }
                                        });

                                        try {
                                            Thread.sleep(3000);
                                        } catch (InterruptedException e) {
                                            // TODO Auto-generated catch block
                                            e.printStackTrace();
                                        }

                                        if ((!THROUGTPUT_DO_AUTO_TEST_COMBINATION) && (!app.getReleaseToCTMR())) {
                                            // save
                                            SaveText2(HDM_txt); //Download folder
                                            SaveText(HDM_txt); //APP

                                            //停留在畫面上
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    addText(HDM_txt, "======APP Rx & Device Tx Result======", true);
                                                    addText(HDM_txt, "TX phy: " + Integer.toString(app.getReConnectTxPhy()) + "M, RX phy: " + Integer.toString(app.getReConnectRxPhy()) + "M", true);
                                                    addText(HDM_txt, "MTU: " + Integer.toString(Throughput_sel_data_length), true);
                                                    switch (app.getConnectInterval()) {
                                                        case 0:
                                                            addText(HDM_txt, "connection interval: CONNECTION_PRIORITY_BALANCED (30ms–50ms)", true);
                                                            break;
                                                        case 1:
                                                            addText(HDM_txt, "connection interval: CONNECTION_PRIORITY_HIGH (11.25ms–15ms)", true);
                                                            break;
                                                        case 2:
                                                            addText(HDM_txt, "connection interval: CONNECTION_PRIORITY_LOW_POWER (100ms–125ms)", true);
                                                            break;
                                                    }
                                                    addText(HDM_txt, "Total Data Length  = " + Integer.toString(Throughput_total_receiver_data_len) + " bytes", true);
                                                    addText(HDM_txt, "Total Time  = " + Double.toString(throughput_use_time_only_ss) + " sec", true);
                                                    addText(HDM_txt, "Data Rate  = " + Double.toString(datarate) + " kbps", true);
                                                    addText(HDM_txt, "====================================", true);
                                                    addText(HDM_txt, "", true);
                                                }
                                            });
                                        }
                                    }
                                } //for (int testphy = physt; testphy <= physp; testphy++)
                            }
                        }while(((Throughput_stop_test_flag==0) && (THROUGTPUT_AUTO_TEST_OPTION_SEL==5)));

                        if ((THROUGTPUT_DO_AUTO_TEST_COMBINATION) || (app.getReleaseToCTMR())) {
                            // save
                            SaveText2(HDM_txt); //Download folder
                            SaveText(HDM_txt); //APP
                        }

                        if(ThroughputMsg.THROUGHPUT_TEST_APPRX_DEVICETX_DISCONNECTED) {
                            if (app.getNeedReConnect()) {
                                while (!app.getReConnectSuccessFlag()) //等待重連
                                {
                                    try {
                                        Thread.sleep(100);
                                    } catch (InterruptedException e) {
                                        // TODO Auto-generated catch block
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }

                        Throughput_States = ThroughputMsg.THROUGHPUT_STATE_IDAL;

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                btn_runthroughput.setText(getActivity().getString(R.string.ate_run));
                                btn_san_ble.setEnabled(true);
                            }
                        });

                    }
                    break;

                    default: {

                    }
                }

            }//if (characteristicoperationfragment != null) {
        }//public void handleMessage(Message msg) {
    }//private final class ATEWRHandler extends Handler {


    public void prepare() {
        mHandlerThread = new HandlerThread(AppThroughputFrsgment.class.getSimpleName());
        mHandlerThread.start();
        mHandler = new AppThroughputFrsgment.ATEWRHandler(mHandlerThread.getLooper(), this);
        mHandling = true;

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_application_throughput, null);

        _isSpinnerOnCreate = true;

        app = new BleApplication();
        btn_runthroughput = (Button) view.findViewById(R.id.btn_runthroughput);
        btn_runthroughput.setText(getActivity().getString(R.string.ate_run));
        initView(view);
        showData();

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getActivity(), R.array.throughput_test_item_array_ctmr, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp_testtype.setAdapter(adapter);

        ArrayAdapter<CharSequence> adapter2 = ArrayAdapter.createFromResource(getActivity(), R.array.throughput_ate_type_array, android.R.layout.simple_spinner_item);
        adapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp_atetype.setAdapter(adapter2);



        //選單按下後處裡
        //test type : aT-dR or aR-dT
        AdapterView.OnItemSelectedListener spnOnItemSelected
                = new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int pos, long id) {

                if(_isSpinnerOnCreate == true){
                    Throughput_sel_test_type = pos;
                    return;
                }

                switch (pos) {
                    case 0:
                        Toast.makeText(getActivity(), "Please select a throughput test item.", Toast.LENGTH_LONG).show();
                        break;
                    //aT-dR
                    //aR-dT
                    default:
                        Throughput_sel_test_type = pos;
                        break;
                }
            }

            public void onNothingSelected(AdapterView<?> parent) {
                //
            }
        };
        sp_testtype.setOnItemSelectedListener(spnOnItemSelected);


        AdapterView.OnItemSelectedListener spnOnItemSelected2
                = new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int pos, long id) {
                THROUGTPUT_AUTO_TEST_OPTION_SEL = pos;
                switch (pos) {
                    case 0: //No ATE
                        THROUGTPUT_DO_AUTO_TEST_COMBINATION = false;
                        tv_datamtulen.setVisibility(View.VISIBLE);
                        et_datamtulen.setVisibility(View.VISIBLE);
                        tv_ate_mtu_test_option.setVisibility(View.GONE);
                        et_ate_mtu_test_option.setVisibility(View.GONE);
                        break;
                    case 1: //Test All Option
                        THROUGTPUT_DO_AUTO_TEST_COMBINATION = true;
                        tv_datamtulen.setVisibility(View.INVISIBLE);
                        et_datamtulen.setVisibility(View.INVISIBLE);
                        tv_ate_mtu_test_option.setVisibility(View.VISIBLE);
                        et_ate_mtu_test_option.setVisibility(View.VISIBLE);
                        break;
                    case 2: //Test PHY 1M/2M Option
                        THROUGTPUT_DO_AUTO_TEST_COMBINATION = true;
                        tv_datamtulen.setVisibility(View.VISIBLE);
                        et_datamtulen.setVisibility(View.VISIBLE);
                        tv_ate_mtu_test_option.setVisibility(View.GONE);
                        et_ate_mtu_test_option.setVisibility(View.GONE);
                        break;
                    case 3: //Test ConnectInterval Option
                        THROUGTPUT_DO_AUTO_TEST_COMBINATION = true;
                        tv_datamtulen.setVisibility(View.VISIBLE);
                        et_datamtulen.setVisibility(View.VISIBLE);
                        tv_ate_mtu_test_option.setVisibility(View.GONE);
                        et_ate_mtu_test_option.setVisibility(View.GONE);
                        break;
                    case 4: //Test MTU Len Option
                        THROUGTPUT_DO_AUTO_TEST_COMBINATION = true;
                        tv_datamtulen.setVisibility(View.INVISIBLE);
                        et_datamtulen.setVisibility(View.INVISIBLE);
                        tv_ate_mtu_test_option.setVisibility(View.VISIBLE);
                        et_ate_mtu_test_option.setVisibility(View.VISIBLE);
                        break;
                    case 5: //repeat test same option
                        THROUGTPUT_DO_AUTO_TEST_COMBINATION = false;
                        tv_datamtulen.setVisibility(View.VISIBLE);
                        et_datamtulen.setVisibility(View.VISIBLE);
                        tv_ate_mtu_test_option.setVisibility(View.GONE);
                        et_ate_mtu_test_option.setVisibility(View.GONE);
                        break;
                    default:
                        THROUGTPUT_DO_AUTO_TEST_COMBINATION = true;
                        tv_datamtulen.setVisibility(View.VISIBLE);
                        et_datamtulen.setVisibility(View.VISIBLE);
                        tv_ate_mtu_test_option.setVisibility(View.GONE);
                        et_ate_mtu_test_option.setVisibility(View.GONE);
                        break;
                }
            }

            public void onNothingSelected(AdapterView<?> parent) {
                //
            }
        };
        sp_atetype.setOnItemSelectedListener(spnOnItemSelected2);


        btn_runthroughput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(_connectBleDevice == null || BleManager.getInstance().isConnected(_connectBleDevice) == false){
                    Toast.makeText(getActivity(), getString(R.string.ble_not_connect), Toast.LENGTH_LONG).show();
                    return;
                }

                if (Throughput_sel_test_type == 0) {
                    Toast.makeText(getActivity(), "Please select a throughput test item.", Toast.LENGTH_LONG).show();
                    return;
                } else {
                    if (btn_runthroughput.getText().toString().equals(getActivity().getString(R.string.ate_run))) {

                        if(et_datamtulen.getText().toString().trim().length() <= 0){
                            Toast.makeText(getActivity(), "Please check the input is correct.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        if(et_packetlen.getText().toString().trim().length() <= 0){
                            Toast.makeText(getActivity(), "Please check the input is correct.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        if(et_sent_packet_interval.getText().toString().trim().length() <= 0){
                            Toast.makeText(getActivity(), "Please check the input is correct.", Toast.LENGTH_LONG).show();
                            return;
                        }

                        Throughput_stop_test_flag = 0;
                        Throughput_sel_data_length = Integer.parseInt(et_datamtulen.getText().toString().trim());
                        Throughput_sel_packet_length = Integer.parseInt(et_packetlen.getText().toString().trim());
                        Throughput_sel_sent_packet_interval_ms = Integer.parseInt(et_sent_packet_interval.getText().toString().trim());

                        if (Throughput_sel_packet_length > 1048712) {
                            Toast.makeText(getActivity(), "The maximum packet length is 1048712, please enter again.", Toast.LENGTH_LONG).show();
                            btn_runthroughput.setText(getActivity().getString(R.string.ate_run));
                            btn_san_ble.setEnabled(true);
                            return;
                        }

                        btn_runthroughput.setText(getActivity().getString(R.string.ate_stop));
                        btn_san_ble.setEnabled(false);
                        //=================啟動新線程=======================
                        //  啟動線程
                        prepare();
                        if (!mHandling)
                            return;

                        Message message = mHandler.obtainMessage();
                        switch (Throughput_sel_test_type) {
                            //1:App is Tx, Device is Rx
                            case 1: {
                                message.what = ThroughputMsg.THROUGHPUT_TEST_APPTX_DEVICERX;
                            }
                            break;
                            //2: App is Rx, Device is Tx
                            case 2: {
                                message.what = ThroughputMsg.THROUGHPUT_TEST_APPRX_DEVICETX;
                            }
                            break;
                            default:

                                break;
                        }
                        //message.obj = (TextView) txtlog;
                        mHandler.sendMessage(message);
                        //================================================
                    } else {
                        btn_runthroughput.setText(getActivity().getString(R.string.ate_run));
                        Throughput_stop_test_flag = 1;
                    }
                }
            }// @Override public void onClick(View view) {
        }); //btn_test.setOnClickListener(new View.OnClickListener() {

        return view;
    }

    private void initView(View v) {
        tv_packetlen = (TextView) v.findViewById(R.id.tv_packetlen);
        et_packetlen = (EditText) v.findViewById(R.id.et_packetlen);
        tv_datamtulen = (TextView) v.findViewById(R.id.tv_datamtulen);
        et_datamtulen = (EditText) v.findViewById(R.id.et_datamtulen);
        tv_sent_packet_interval = (TextView) v.findViewById(R.id.tv_sent_packet_interval);
        et_sent_packet_interval = (EditText) v.findViewById(R.id.et_sent_packet_interval);
        tv_ate_mtu_test_option = (TextView) v.findViewById(R.id.tv_ate_mtu_test_option);
        et_ate_mtu_test_option = (EditText) v.findViewById(R.id.et_ate_mtu_test_option);
        tv_ate_type = (TextView) v.findViewById(R.id.tv_ate_type);
        sp_testtype = (Spinner) v.findViewById(R.id.sp_testtype);
        sp_atetype = (Spinner) v.findViewById(R.id.sp_atetype);
        txtlog = (TextView) v.findViewById(R.id.txtlog);
        txtlog.setMovementMethod(ScrollingMovementMethod.getInstance());
        txtlogtitle = (TextView) v.findViewById(R.id.txtlogtitle);
        PBar_thp = (ProgressBar) v.findViewById(R.id.progressbar_thput);

        //////////////add wphu
        btn_san_ble = (Button) v.findViewById(R.id.btn_san_ble);
        btn_san_ble.setOnClickListener(onClickScanBleButton);
        bleDeviceText = (TextView) v.findViewById(R.id.BLE_DEVICE_TEXT);
        bleStatusText = (TextView) v.findViewById(R.id.BLE_STATUS_TEXT);
        //set phy
        spinner_phy = (Spinner) v.findViewById(R.id.spinner3);
        ArrayAdapter<CharSequence> adapter_phy = ArrayAdapter.createFromResource(getActivity(), R.array.phy_array, android.R.layout.simple_spinner_item);
        adapter_phy.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
        spinner_phy.setAdapter(adapter_phy);
        spinner_phy.setOnItemSelectedListener(phy_slectListener);
        //set request Connection Priority
        spinner_priority = (Spinner) v.findViewById(R.id.spinner2);
        ArrayAdapter<CharSequence> adapter_priority = ArrayAdapter.createFromResource(getActivity(), R.array.priority_array, android.R.layout.simple_spinner_item);
        adapter_priority.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
        spinner_priority.setAdapter(adapter_priority);
        spinner_priority.setOnItemSelectedListener(priority_slectListener);

        ProgressNum = (TextView) v.findViewById(R.id.ProgressNum);
    }

    public void showData() {

        final BleDevice bleDevice_info = _connectBleDevice; //這裡銜接上藍牙裝置
//        String device_adrs = bleDevice_info.getMac();
        String device_adrs ="";
        et_datamtulen.setText(String.valueOf(Throughput_sel_data_length));
        et_packetlen.setText(String.valueOf(Throughput_sel_packet_length));
        et_sent_packet_interval.setText(String.valueOf(Throughput_sel_sent_packet_interval_ms));
        et_ate_mtu_test_option.setText("20,244");
        txtlogtitle.setText("Throughput run log:  ( MAC: " + device_adrs +" )");
        if (app.getReleaseToCTMR()) {
            tv_ate_type.setVisibility(View.GONE);
            sp_atetype.setVisibility(View.GONE);
            tv_ate_mtu_test_option.setVisibility(View.GONE); //隱藏
            et_ate_mtu_test_option.setVisibility(View.GONE); //隱藏
        }
    }

    private void runOnUiThread(Runnable runnable) {
        if (isAdded() && getActivity() != null)
            getActivity().runOnUiThread(runnable);
    }

    //stubborn = true ，不受 PRINT_FOTA_PROCCESS_PRINT_TO_TXTVIEW 影響
    private void addText(TextView textView, String content, Boolean stubborn) {
        String colorText = content;

        if(content.indexOf("FotaSuccessNum")>-1){
            colorText = TextTool.getColoredSpanned(content,"#00DB00");
        }
        if(content.indexOf("FotaFailureNum")>-1){
            colorText = TextTool.getColoredSpanned(content,"#CE0000");
        }
        if(content.indexOf("FotaRescuedNum")>-1){
            colorText = TextTool.getColoredSpanned(content,"#2828FF");
        }
        if(content.indexOf("====")>-1){
            colorText = TextTool.getColoredSpanned(content,"#EAC100");
        }
        if(content.indexOf("can be updated")>-1){
            colorText = TextTool.getColoredSpanned(content,"#00DB00");
        }
        if(content.indexOf("no need to update")>-1){
            colorText = TextTool.getColoredSpanned(content,"#CE0000");
        }
        if(content.indexOf("Connect Success")>-1){
            colorText = TextTool.getColoredSpanned(content,"#00DB00");
        }
        if(content.indexOf("Dis Connected")>-1){
            colorText = TextTool.getColoredSpanned(content,"#CE0000");
        }
        if(content.indexOf("Fail")>-1){
            colorText = TextTool.getColoredSpanned(content,"#CE0000");
        }

        if ((FotaMsg.PRINT_FOTA_PROCCESS_PRINT_TO_TXTVIEW == 1) || (stubborn)) {
            textView.append(Html.fromHtml(colorText));
            textView.append("\n"); //換行
            int offset = textView.getLineCount() * textView.getLineHeight();
            if (offset > textView.getHeight()) {
                //移動到最後的位置
                textView.scrollTo(0, offset- textView.getHeight()+20);
            }
        }


    }

    //延續增加字串在同一行後面 只用一隔空格 隔開每次的結果
    private void addText_do_no_change_line(TextView textView, String content, Boolean stubborn) {
        if ((FotaMsg.PRINT_FOTA_PROCCESS_PRINT_TO_TXTVIEW == 1) || (stubborn)) {
            textView.append(content);
            textView.append(" ");
            int offset = textView.getLineCount() * textView.getLineHeight();
            if (offset > textView.getHeight()) {
                //移動到最後的位置
                textView.scrollTo(0, offset - textView.getHeight());
            }
        }
    }

    private void ClearText(final TextView textView) {
        textView.setText("");
    }

    //Change progress bar
    private void changeProgressBar(ProgressBar pbar, int val) {
//        addText(txtlog,"---"+val+"%---",true);
        ProgressNum.setText(val+"%");
        pbar.setProgress(val);
    }

    //save log
    private void SaveText(final TextView textView) {
        Calendar mCal = Calendar.getInstance();
        CharSequence gettoday = DateFormat.format("yyyy_MM_dd_kk", mCal.getTime());
        CharSequence gettoday2 = DateFormat.format("mm", mCal.getTime());
        int time_jarge = Integer.valueOf((String) gettoday2);

        if(isAdded() != true){
            return;
        }

        File dir = getActivity().getFilesDir();


        String strsavelog;
        if (time_jarge >= 30) {
            strsavelog = "_savelog2.txt";
        } else {
            strsavelog = "_savelog1.txt";
        }

        if (FotaMsg.LOG_FILE_TYPE == FotaMsg.LOG_FILE_APPLY) {
            File outFile_delete = new File(dir, ((String) gettoday) + strsavelog);
            //判斷檔案是否存在，存在則刪除
            if (outFile_delete.exists()) {
                outFile_delete.delete();
            }

            //在該目錄底下開啟或建立檔名為 "test.txt" 的檔案
            File outFile = new File(dir, (String) gettoday + strsavelog);

            //將資料寫入檔案中，若 package name 為 com.myapp
            //就會產生 /data/data/com.myapp/files/test.txt 檔案
            writeToFileReplace(outFile, textView.getText().toString());
        } else {
            //在該目錄底下開啟或建立檔名為 "test.txt" 的檔案
            File outFile = new File(dir, (String) gettoday + strsavelog);

            //將資料寫入檔案中，若 package name 為 com.myapp
            //就會產生 /data/data/com.myapp/files/test.txt 檔案
            writeToFileAppend(outFile, textView.getText().toString());

            //寫完會清除 textView 內容 (release 版本Log很少，清掉會不完整)
            if (!app.getReleaseToCTMR()) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        textView.setText("");
                    }
                });
            }
        }
    }

    //save to Download folder
    //@RequiresApi(api = Build.VERSION_CODES.N)
    private void SaveText2(final TextView textView) {
        Calendar mCal = Calendar.getInstance();
        //CharSequence s = DateFormat.format("yyyy-MM-dd kk:mm:ss", mCal.getTime());    // kk:24小時制, hh:12小時制
        CharSequence gettoday = DateFormat.format("yyyy_MM_dd_kk", mCal.getTime());    // kk:24小時制, hh:12小時制
        CharSequence gettoday2 = DateFormat.format("mm", mCal.getTime());
        int time_jarge = Integer.valueOf((String) gettoday2);

        //String savelogpath = Environment.getExternalStorageDirectory().getPath();
        String savelogpath = Environment.getExternalStorageDirectory().getAbsolutePath();
        savelogpath += "/Download/fota_savelog/";


        //目錄
        File dir = new File(savelogpath);
        //先檢查該目錄是否存在
        if (!dir.exists()) {
            //若不存在則建立它
            dir.mkdir();
        }

        String strsavelog;
        if (time_jarge >= 30) {
            strsavelog = "_savelog2.txt";
        } else {
            strsavelog = "_savelog1.txt";
        }


        if (FotaMsg.LOG_FILE_TYPE == FotaMsg.LOG_FILE_APPLY) {
            File outFile_delete = new File(savelogpath, ((String) gettoday) + strsavelog);
            //判斷檔案是否存在，存在則刪除
            if (outFile_delete.exists()) {
                outFile_delete.delete();
            }
            File outFile = new File(savelogpath, ((String) gettoday) + strsavelog);
            //將資料寫入檔案中，若 package name 為 com.myapp
            //就會產生 /data/data/com.myapp/files/test.txt 檔案
            writeToFileReplace(outFile, textView.getText().toString());
        } else {
            File outFile = new File(savelogpath, ((String) gettoday) + strsavelog);
            //將資料寫入檔案中，若 package name 為 com.myapp
            //就會產生 /data/data/com.myapp/files/test.txt 檔案
            writeToFileAppend(outFile, textView.getText().toString());
        }
    }

    //writeToFile 方法如下  覆蓋原本檔案  (append:附加)
    private void writeToFileReplace(File fout, String data) {
        FileOutputStream osw = null;
        try {
            osw = new FileOutputStream(fout, false);
            osw.write(data.getBytes());
            osw.flush();
        } catch (Exception e) {
            ;
        } finally {
            try {
                osw.close();
            } catch (Exception e) {
                ;
            }
        }
    }

    //writeToFile 方法如下 接著檔案最後寫  (append:附加)
    private void writeToFileAppend(File fout, String data) {
        FileOutputStream osw = null;
        try {
            osw = new FileOutputStream(fout, true);
            osw.write(data.getBytes());
            osw.flush();
        } catch (Exception e) {
            ;
        } finally {
            try {
                osw.close();
            } catch (Exception e) {
                ;
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        _isAutoReConnect = false;
        BleManager.getInstance().disconnect(_connectBleDevice);
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private Button btn_san_ble;
    private Button btn_rate_run;
    //SCAN
    private ArrayList<BleDevice> _scanDeviceArray = new ArrayList<BleDevice>();
    private ArrayList<String> _scanDeviceStringArray = new ArrayList<String>();
    private MaterialDialog _alertDialog = null;
    //連線的藍芽裝置
    private BleDevice _connectBleDevice = null;
    private TextView bleDeviceText = null;
    private TextView bleStatusText = null;
    private Spinner spinner_phy = null;
    private Spinner spinner_priority = null;

    private TextView ProgressNum  = null;

    private boolean _isSpinnerOnCreate = true;  //如果是第一次載入
    private boolean _isAutoReConnect = true;  //自動重新連線 （預設開）

    //掃描藍芽裝置
    private Button.OnClickListener onClickScanBleButton = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            Log.i("DataRate","onClickScanBleButton");

            //若已經連線就先斷線
            if(_connectBleDevice != null && BleManager.getInstance().isConnected(_connectBleDevice) == true){
                _isAutoReConnect = false;
                BleManager.getInstance().disconnect(_connectBleDevice);
                return;
            }

            _scanDeviceStringArray.clear();
            _scanDeviceArray.clear();

            _alertDialog = new MaterialDialog(ResourceManager.context,MaterialDialog.getDEFAULT_BEHAVIOR());
            _alertDialog.cancelOnTouchOutside(false);
            _alertDialog.cancelable(false);
            _alertDialog.title(null,"BLE Device Scaning...");
            DialogListExtKt.listItems(_alertDialog, null, _scanDeviceStringArray, null, true, (materialDialog, index, text) -> {

                Log.i("DialogListExtKt","listItems:"+index);

                if(index >= _scanDeviceArray.size()){
                    return null;
                }

                _isAutoReConnect = true;
                connectBle(_scanDeviceArray.get(index));//藍芽連線
                bleDeviceText.setText("BLE Device : "+_scanDeviceArray.get(index).getName());
                BleManager.getInstance().cancelScan();
                _alertDialog.dismiss();
                return null;
            });
            _alertDialog.negativeButton(null,"cancel", (materialDialog) -> {
                BleManager.getInstance().cancelScan();
                _alertDialog.dismiss();
                return null;
            });

            _alertDialog.show();

            BleManager.getInstance().scan(new BleScanCallback() {
                @Override
                public void onScanStarted(boolean success) {
                }
                @Override
                public void onScanning(BleDevice bleDevice) {
                    Log.d("DataRate", "result:"+bleDevice.getName()  +"    MAC:"+bleDevice.getMac()+"    Rssi:"+bleDevice.getRssi());
                    if(bleDevice.getName()== null){
                        return;
                    }

                    String displayName = bleDevice.getName()+"\n"+bleDevice.getMac();

                    if(!_scanDeviceStringArray.contains(displayName) && !_scanDeviceArray.contains(bleDevice)){
                        _scanDeviceStringArray.add(displayName);
                        _scanDeviceArray.add(bleDevice);
                        DialogListExtKt.updateListItems(_alertDialog,null,_scanDeviceStringArray,null,null);
                    }
                }
                @Override
                public void onScanFinished(List<BleDevice> scanResultList) {
                    _scanDeviceStringArray.add("** End of Search, If the device is not found, please try again. **");
                    DialogListExtKt.updateListItems(_alertDialog,null,_scanDeviceStringArray,null,null);
                }
            });
        }
    };

    //ble連線
    private void connectBle(BleDevice bleDevice) {
        BleManager.getInstance().connect(bleDevice, bgk);
    }

    //ble Callback
    private BleGattCallback bgk = new BleGattCallback() {
        @Override
        public void onStartConnect() {
            Log.d("DataRate", "connectBle: onStartConnect");
            bleStatusText.setText("BLE Status : Start Connect");
            bleStatusText.setTextColor(Color.BLUE);
        }

        @Override
        public void onConnectFail(BleDevice bleDevice, BleException exception) {
            Log.d("DataRate", "connectBle: onConnectFail");
            addText(txtlog, "Connect Fail", true);
            bleStatusText.setText("BLE Status : Connect Fail");
            bleStatusText.setTextColor(Color.RED);

            if(_isAutoReConnect == true){
                BleManager.getInstance().connect(bleDevice, bgk);
            }

            btn_san_ble.setText("SCAN BLE");
        }

        @Override
        public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
            Log.d("DataRate", "connectBle: onConnectSuccess");
            _connectBleDevice = bleDevice;
            addText(txtlog, "Connect Success", true);
            bleStatusText.setText("BLE Status : Connect Success");
            bleStatusText.setTextColor(Color.GREEN);
            btn_san_ble.setText("DisConnected");

            //change MTU size
            app.setReConnectMtu(255);
            BleManager.getInstance().setMtu(_connectBleDevice, 255, new BleMtuChangedCallback() {
                @Override
                public void onSetMTUFailure(final BleException exception) {
                    // Change MTU Failure
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            addText(txtlog, "setMTUFailure" + exception.toString(), false);
                        }
                    });
                }

                @Override
                public void onMtuChanged(final int mtu) {
                    // Change MTU success，and get the MTU value supported by the current BLE device transmission.
                    app.setReConnectMtu(mtu);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            addText(txtlog, "MtuChanged = " + mtu, false);
                        }
                    });
                }
            });

            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //每次連線成功重新設定
            app.setReConnectTxPhy(1);
            app.setReConnectRxPhy(1);
            spinner_phy.setSelection(0); //PHY default is 1M
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                BleManager.getInstance().setPreferredPhy(_connectBleDevice, 1, 1, 0);
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //每次連線成功重新設定
            spinner_priority.setSelection(0);
            BleManager.getInstance().requestConnectionPriority(_connectBleDevice, 0);
            app.setConnectInterval(0);

            List<BluetoothGattService> gattServices = BleManager.getInstance().getBluetoothGattServices(bleDevice);
            for (BluetoothGattService bgs:gattServices) {
                List<BluetoothGattCharacteristic> bgcs = BleManager.getInstance().getBluetoothGattCharacteristics(bgs);
                for (BluetoothGattCharacteristic bgc:bgcs) {
                    if(bgc.getUuid().toString().indexOf(ThroughputMsg.THROUGHPUT_CHARACTERISTICUUID_NOTIFY)>-1){
                        ThroughputMsg.THROUGHPUT_NOTIFY = THROUGHPUT_CHARACTERISTICUUID_NOTIFY;
                        Log.i("THROUGHPUT_NOTIFY",ThroughputMsg.THROUGHPUT_CHARACTERISTICUUID_NOTIFY);
                    }
                    if(bgc.getUuid().toString().indexOf(ThroughputMsg.THROUGHPUT_CHARACTERISTICUUID_128BIT_NOTIFY)>-1){
                        ThroughputMsg.THROUGHPUT_NOTIFY = THROUGHPUT_CHARACTERISTICUUID_128BIT_NOTIFY;
                        Log.i("THROUGHPUT_NOTIFY",ThroughputMsg.THROUGHPUT_CHARACTERISTICUUID_128BIT_NOTIFY);
                    }
                }
            }
        }

        @Override
        public void onDisConnected(boolean isActiveDisConnected, BleDevice device, BluetoothGatt gatt, int status) {
            Log.d("DataRate", "connectBle: onDisConnected");
            addText(txtlog, "Dis Connected", true);
            bleStatusText.setText("BLE Status : Dis Connected");
            bleStatusText.setTextColor(Color.RED);

            if(_isAutoReConnect == true){
                BleManager.getInstance().connect(device, bgk);
            }

            btn_san_ble.setText("SCAN BLE");
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            Log.d("DataRate", "onMtuChanged " +mtu);
            super.onMtuChanged(gatt, mtu, status);
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.d("DataRate", "onConnectionStateChange " +status +" >> "+newState);
        }

        @Override
        public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
            super.onPhyUpdate(gatt, txPhy, rxPhy, status);
            Log.d("DataRate", "onPhyUpdate   TX:" +txPhy +" RX:"+rxPhy);
        }



    };

    /**
     * set phy Selected
     */
    private AdapterView.OnItemSelectedListener phy_slectListener = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

            if(_isSpinnerOnCreate == true){
                app.setReConnectTxPhy(1);
                app.setReConnectRxPhy(1);
                _isSpinnerOnCreate = false;
                return;
            }

            if(_connectBleDevice == null || BleManager.getInstance().isConnected(_connectBleDevice) == false){
                Toast.makeText(getActivity(), getString(R.string.ble_not_connect), Toast.LENGTH_LONG).show();
                spinner_phy.setSelection(0); //PHY default is 1M
                return;
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {  //版本大於等於Ｏ v26
                Toast.makeText(getActivity(), getString(R.string.only_support_1M), Toast.LENGTH_LONG).show();
                app.setReConnectTxPhy(1);
                app.setReConnectRxPhy(1);
                spinner_phy.setSelection(0); //PHY default is 1M
                return;
            }

            switch(position)
            {
                case 0: //TX:1M RX:1M
                    app.setReConnectTxPhy(1);
                    app.setReConnectRxPhy(1);
                    BleManager.getInstance().setPreferredPhy(_connectBleDevice, 1, 1, 0);
                    break;
                case 1: //TX:2M RX:2M     //same default
                    app.setReConnectTxPhy(2);
                    app.setReConnectRxPhy(2);
                    BleManager.getInstance().setPreferredPhy(_connectBleDevice, 2, 2, 0);
                    break;
                case 2: //TX:2M RX:1M
                    app.setReConnectTxPhy(2);
                    app.setReConnectRxPhy(1);
                    BleManager.getInstance().setPreferredPhy(_connectBleDevice, 2, 1, 0);
                    break;
                case 3: //TX:1M RX:2M
                    app.setReConnectTxPhy(1);
                    app.setReConnectRxPhy(2);
                    BleManager.getInstance().setPreferredPhy(_connectBleDevice, 1, 2, 0);
                    break;
                default:
                    app.setReConnectTxPhy(1);
                    app.setReConnectRxPhy(1);
                    spinner_phy.setSelection(0); //PHY default is 1M
                    BleManager.getInstance().setPreferredPhy(_connectBleDevice, 1, 1, 0);
                    break;
            }
        }
        @Override
        public void onNothingSelected(AdapterView<?> parent) {

        }
    };

    /**
     * set priority Selected
     */
    private AdapterView.OnItemSelectedListener priority_slectListener = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

            if(_isSpinnerOnCreate == true){
                app.setConnectInterval(0);
                return;
            }

            if(_connectBleDevice == null || BleManager.getInstance().isConnected(_connectBleDevice) == false){
                Toast.makeText(getActivity(), getString(R.string.ble_not_connect), Toast.LENGTH_LONG).show();
                app.setConnectInterval(0);
                return;
            }

            //change connect interval
            //CONNECTION_PRIORITY_BALANCED = 0; CONNECTION_PRIORITY_HIGH = 1; CONNECTION_PRIORITY_LOW_POWER = 2;
            //時間由短至長測試
            if (position == 1) { //HIGH
                BleManager.getInstance().requestConnectionPriority(_connectBleDevice, 1);
                app.setConnectInterval(1);
            } else if (position == 2) {  //LOW
                BleManager.getInstance().requestConnectionPriority(_connectBleDevice, 2);
                app.setConnectInterval(2);
            } else {
                BleManager.getInstance().requestConnectionPriority(_connectBleDevice, 0);
                app.setConnectInterval(0);
            }

        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {

        }
    };
}
