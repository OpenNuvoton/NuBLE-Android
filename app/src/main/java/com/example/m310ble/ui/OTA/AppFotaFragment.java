package com.example.m310ble.ui.OTA;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.Html;
import android.text.format.DateFormat;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.list.DialogListExtKt;
import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleBaseCallback;
import com.clj.fastble.callback.BleGattCallback;
import com.clj.fastble.callback.BleIndicateCallback;
import com.clj.fastble.callback.BleMtuChangedCallback;
import com.clj.fastble.callback.BleNotifyCallback;
import com.clj.fastble.callback.BleScanCallback;
import com.clj.fastble.callback.BleWriteCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.utils.HexUtil;
import com.example.m310ble.MainActivity;
import com.example.m310ble.R;
import com.example.m310ble.UtilTool.ResourceManager;
import com.example.m310ble.UtilTool.TextTool;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static android.os.Environment.DIRECTORY_DOWNLOADS;
import static com.clj.fastble.utils.HexUtil.encodeHexStr_OneByte;

//import android.icu.util.Calendar;


@RequiresApi (Build.VERSION_CODES.JELLY_BEAN_MR2)
public class AppFotaFragment extends Fragment {

    private BleApplication app;

    private Handler mMainHandler = new Handler(Looper.getMainLooper());
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private boolean mHandling;

    //FOTA States
    public int FOTA_States = FotaMsg.FOTA_STATE_IDAL;
    //FOTA select bin file name
    private String FOTA_load_bin_name;
    //MTU Data length
    private int fota_user_sel_mtu_data_length;

    //???????????????????????????????????????(ms)
    private int fota_user_enter_data_delaytime_ms=0; //default is 0

    //This parameter determines the length of the Notify interval. The default value is 1920.
    //When the data is transmitted 1920 bytes, the process must wait until the notify from the DUT is received.
    private int fota_user_sel_notify_interval_length=1920;

    private int fota_Already_transmitted_length=0;
    private int fota_Notyet_transmitted_length=0;

    //overnigth test ????????????
    private int FotaSuccessNum = 0;
    private int FotaRescuedNum = 0;
    private int FotaFailureNum = 0;
    //???Flag???true????????????????????????false????????????????????????
    private boolean FotaRescuedflag = false;

    private boolean FotaFirstRun = true;
    private boolean FOTA_USER_QUERY_FW_VERSION_ONLY = false;
    //============================FOTA Exception=========================================
    //When an exception occurs, (Notify)Flag=1???
    private Boolean NotifyOccurredFlag = false;

    //bit[0]:
    //bit[1]: DATA TIME OUT
    //bit[2]: DATA_ADDRESS_UNEXPECTED
    //bit[3]:
    //bit[4]:
    //bit[5]: DATA_ADDRESS_ERROR
    //bit[6]:
    //bit[7]:
    public char FOTA_NOTIFY_STATES = 0x00;

    //bit[0]:
    //bit[1]: CMD_ERR
    //bit[2]: ALREADY_START
    //bit[3]: NOT_START
    //bit[4]:
    //bit[5]:
    //bit[6]:
    //bit[7]:
    public char FOTA_INDICATE_STATES = 0x00;

    //(Notify)Exception occurs, DUT expects address
    private int NotifyExpectedNextAddress = 0;

    //???CMD = Start ????????????0x02????????????????????????????????????????????????????????????
    private Boolean IndicateUnfinishedDataCRCCompareResult = false;   //Device ????????????CRC value, ??????Load??????CRC??????????????????????????????????????????????????????Erase??????????????????
    private int IndicateUnfinishedDataNextAddress = 0;  //Device ??????????????????????????????????????????
    //==================================================================================

    //??????????????????????????????????????????(FOTA, Stress, ...)
    public int write_one_package_end_falg = 0; //????????????????????????????????????????????????true
    public int write_success_falg = 1; //???????????????????????????true????????????false

    //Stop the FOTA process when this flag=1
    public int write_stop_test_flag = 0;

    //????????????
    final int FILE_SELECT_CODE =0;

    //Provide transmission percentage calculation
    public int Percentage_val = 0;
    public int Percentage_preval = 0;

    //??????????????????
    public int Number_of_packets = 0;
    public int Number_of_remaining_data = 0;

    //private final class FotaHandler extends Handler {
    class FotaHandler extends Handler {

        private final WeakReference<AppFotaFragment> mAppFotaFragment;

        FotaHandler(Looper looper, AppFotaFragment appFotaFragment) {
            super(looper);
            mAppFotaFragment = new WeakReference<>(appFotaFragment);
        }

        @Override
        public void handleMessage(Message msg) {
            AppFotaFragment appFotaFragment = mAppFotaFragment.get();

            if (appFotaFragment != null) {
                switch(msg.what) {
                    case FotaMsg.FOTA_START:{
                        final int fota_one_package_data_length = fota_user_sel_mtu_data_length;
                        final int DelayTimeEachData = fota_user_enter_data_delaytime_ms;
                        final BleDevice HDM_bleDevice = _connectBleDevice; //???????????????????????????
                        Object[] objs =  (Object[]) msg.obj;
                        final TextView HDM_txt = (TextView) objs[0];
                        final ProgressBar HDM_PBar1 = (ProgressBar) objs[1];

                        int FotaTransmittedLength = 0; //(???????????????) ????????????????????????????????? = fota_user_sel_notify_interval_length ??????????????????Notify???????????????????????????????????????
                        //load bin data
                        final byte[] load_bin_data = new byte[524320]; //512K Byte  (524,288+32=524,320)
                        final byte[] load_bin_data_0to15_device_info = new byte[16];
                        final byte[] load_bin_data_include_defined_adrs = new byte[655392]; //512K Byte  (524288+32+131072=655,392), (131072=4*32768), (32768=524288/16), (524288=0x80000)

                        Date StartDate = new Date(System.currentTimeMillis());
                        //clear parameter
                        FotaSuccessNum = 0;
                        FotaRescuedNum = 0;
                        FotaFailureNum = 0;
                        FotaRescuedflag = false;
                        write_stop_test_flag = 0;
                        FotaFirstRun = true;

                        while(((app.getNeedReConnect())&&(write_stop_test_flag==(int)0)) || (FOTA_States==FotaMsg.FOTA_STATE_CMD_START) ||(FotaFirstRun)) {

                            //?????????????????????????????????(/sdcard)
//                            String binpath = Environment.getExternalStorageDirectory().getPath();
                            String binpath = Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS).getPath();
                            File binFile = new File(binpath + "/fota_bin/" + FOTA_load_bin_name);

                            if(FOTA_States!=FotaMsg.FOTA_STATE_CMD_START) {
                                while ((!app.getReConnectSuccessFlag()) && (!FotaFirstRun)) //????????????
                                {
                                    try {
                                        Thread.sleep(100);
                                    } catch (InterruptedException e) {
                                        // TODO Auto-generated catch block
                                        e.printStackTrace();
                                    }
                                }

                                //?????????????????????????????????MTU
                                if ((app.getReConnectSuccessFlag())) {
                                    try {
                                        Thread.sleep(200);
                                    } catch (InterruptedException e) {
                                        // TODO Auto-generated catch block
                                        e.printStackTrace();
                                    }
                                }
                                app.setReConnectStartFlag(false);
                                app.setReConnectSuccessFlag(false);
                                int ComfirmeConnectNum = 0;
                                while (!BleManager.getInstance().isConnected(HDM_bleDevice)) {
                                    ComfirmeConnectNum += 1;
                                    try {
                                        Thread.sleep(100);
                                    } catch (InterruptedException e) {
                                        // TODO Auto-generated catch block
                                        e.printStackTrace();
                                    }
                                }

                                Calendar mCalStart = Calendar.getInstance();
                                final CharSequence starttime_kk = DateFormat.format("kk", mCalStart.getTime());    // kk:24?????????, hh:12?????????
                                final CharSequence starttime_mm = DateFormat.format("mm", mCalStart.getTime());    // kk:24?????????, hh:12?????????
                                final CharSequence starttime_ss = DateFormat.format("ss", mCalStart.getTime());    // kk:24?????????, hh:12?????????

                                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                                StartDate = new Date(System.currentTimeMillis());

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!FOTA_USER_QUERY_FW_VERSION_ONLY) { //FOTA_USER_QUERY_FW_VERSION_ONLY : ????????????FW????????????????????????
                                            addText(HDM_txt, "start time = " + (String) starttime_kk + ":" + (String) starttime_mm + ":" + (String) starttime_ss, true);
                                        }
                                    }
                                });

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        addText(HDM_txt, "MTU size = " + fota_one_package_data_length, false);
                                    }
                                });

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        addText(HDM_txt, "DelayTimeEachData = " + DelayTimeEachData + " ms", false);
                                    }
                                });

                                if ((!FotaMsg.FOTA_NOTIFY_INTERVAL_DEFINED_BY_UESR) || (app.getReleaseToCTMR())) {
                                    //Calculate the notify interval ???find the value closest to "FotaMsg.FOTA_NOTIFY_INTERVAL_MAX_VALUE"
                                    fota_user_sel_notify_interval_length = fota_one_package_data_length; //????????????MTU???????????????MTU?????????
                                    while ((fota_user_sel_notify_interval_length + fota_one_package_data_length) <= FotaMsg.FOTA_NOTIFY_INTERVAL_MAX_VALUE) {
                                        fota_user_sel_notify_interval_length += fota_one_package_data_length;
                                    }
                                }

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        addText(HDM_txt, "Notify Interval = " + fota_user_sel_notify_interval_length, false);
                                    }
                                });

                                try {
                                    FileInputStream fin = new FileInputStream(binFile);
                                    while (fin.read(load_bin_data) != -1) {
                                    }
                                    fin.close();
                                } catch (FileNotFoundException e) {
                                    e.printStackTrace();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                                System.arraycopy(load_bin_data, 0, load_bin_data_0to15_device_info, 0, 16);
                                final String strBinData_0to15_Byte = HexUtil.formatHexString(load_bin_data_0to15_device_info, false);// parameter2 = false ; ???????????????????????????

                                //Initial
                                FOTA_States = FotaMsg.FOTA_STATE_IDAL;
                                //???????????????????????????MTU?????????????????????MainActivity??????????????????MTU Change.
                                if (FotaFirstRun && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)) {
                                    //==========================================  Prepare =================================
                                    //1. change MTU
                                    app.setReConnectMtu(255);
                                    BleManager.getInstance().setMtu(HDM_bleDevice, 255, new BleMtuChangedCallback() {
                                        @Override
                                        public void onSetMTUFailure(final BleException exception) {
                                            // Change MTU Failure
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    addText(HDM_txt, "setMTUFailure" + exception.toString(), false);
                                                }
                                            });
                                            FotaFirstRun = false;
                                            FOTA_States = FotaMsg.FOTA_STATE_MTU_CHANGE_FAILURE;
                                        }

                                        @Override
                                        public void onMtuChanged(final int mtu) {
                                            // Change MTU success???and get the MTU value supported by the current BLE device transmission.
                                            app.setReConnectMtu(mtu);
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    addText(HDM_txt, "MtuChanged = " + mtu, false);
                                                    //if DUT no support???????????????mtu??????
                                                }
                                            });
                                            FotaFirstRun = false;
                                            FOTA_States = FotaMsg.FOTA_STATE_MTU_CHANGE_SCCESS;
                                        }
                                    });

                                    while (FOTA_States != FotaMsg.FOTA_STATE_MTU_CHANGE_SCCESS) {
                                        if (FOTA_States == FotaMsg.FOTA_STATE_MTU_CHANGE_FAILURE) {
                                            //MTU Change failure, APP actively disconnects DUT connection.
                                            if (BleManager.getInstance().isConnected(HDM_bleDevice)) {
                                                BleManager.getInstance().disconnect(HDM_bleDevice);
                                            }
                                            try {
                                                Thread.sleep(FotaMsg.FOTA_WAIT_NEXT_FOTA_PROCCESS_TIME_MS);
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
                                            //disconnect???exit while (FOTA_States != FotaMsg.FOTA_STATE_MTU_CHANGE_SCCESS)
                                            try {
                                                Thread.sleep(FotaMsg.FOTA_DISCONNECTED_WAIT_TIME_IN_WHILE_LOOP);
                                            } catch (InterruptedException e) {
                                                // TODO Auto-generated catch block
                                                e.printStackTrace();
                                            }
                                            break;
                                        }
                                    } //while (FOTA_States != FotaMsg.FOTA_STATE_MTU_CHANGE_SCCESS)


                                    if (FOTA_States == FotaMsg.FOTA_STATE_MTU_CHANGE_FAILURE) {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                addText(HDM_txt, "MTU Failure", false);
                                            }
                                        });
                                        FotaFailureNum += 1;
                                        //save
                                        SaveText2(HDM_txt); //Download folder
                                        SaveText(HDM_txt); //APP
                                        continue;
                                    }
                                }


                                /**
                                 * @param2 txPhy preferred transmitter PHY. Bitwise OR of any of
                                 *             {@link BluetoothDevice#PHY_LE_1M_MASK}, {@link BluetoothDevice#PHY_LE_2M_MASK},
                                 *             and {@link BluetoothDevice#PHY_LE_CODED_MASK}.
                                 * @param3 rxPhy preferred receiver PHY. Bitwise OR of any of
                                 *             {@link BluetoothDevice#PHY_LE_1M_MASK}, {@link BluetoothDevice#PHY_LE_2M_MASK},
                                 *             and {@link BluetoothDevice#PHY_LE_CODED_MASK}.
                                 * @param4 phyOptions preferred coding to use when transmitting on the LE Coded PHY. Can be one
                                 *             of {@link BluetoothDevice#PHY_OPTION_NO_PREFERRED},
                                 *             {@link BluetoothDevice#PHY_OPTION_S2} or {@link BluetoothDevice#PHY_OPTION_S8}
                                 *public static final int PHY_LE_1M_MASK = 1;   *public static final int PHY_LE_2M_MASK = 2;    *public static final int PHY_OPTION_NO_PREFERRED = 0;
                                 */

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    //3. ??????PHY
                                    BleManager.getInstance().setPreferredPhy(HDM_bleDevice, app.getReConnectTxPhy(), app.getReConnectRxPhy(), 0);
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            addText(HDM_txt, "PHY: TX" + Integer.toString(app.getReConnectTxPhy()) + "M, RX" + Integer.toString(app.getReConnectRxPhy()) + "M", false);
                                        }
                                    });
                                }

                                try {
                                    Thread.sleep(150);
                                } catch (InterruptedException e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                }

                                //===========================================================================================
                                // indicate
                                BleManager.getInstance().indicate(HDM_bleDevice,FotaMsg.FOTASERVICEUUID,FotaMsg.FOTACHARACTERISTICUUID_WRITE_INDICATE,
                                        new BleIndicateCallback() {
                                            @Override
                                            public void onIndicateSuccess() {
                                                // ????????????????????????
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        addText(HDM_txt, "indicate success", false);
                                                    }
                                                });
                                                FOTA_States = FotaMsg.FOTA_STATE_INDICATION_OPEN_SCCESS;
                                            }

                                            @Override
                                            public void onIndicateFailure(final BleException exception) {
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        addText(HDM_txt, exception.toString(), false);
                                                    }
                                                });
                                                FOTA_States = FotaMsg.FOTA_STATE_INDICATION_OPEN_FAILURE;
                                            }

                                            @Override
                                            public void onCharacteristicChanged(final byte[] data) {  //device ???????????????????????????
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        //addText(txt, HexUtil.formatHexString(characteristic.getValue(), true), false);
                                                        addText(HDM_txt, "indicate:" + HexUtil.formatHexString(data, true) + ",  FOTA_States: " + FOTA_States, false);
                                                    }
                                                });

                                                switch (FOTA_States) {
                                                    case FotaMsg.FOTA_STATE_CMD_QUERY_INFORMATION:
                                                        //??????????????????16byte ???????????????
                                                        String strIndicateRxData = HexUtil.formatHexString(data, false);// parameter2 = false ; ???????????????????????????
                                                        int strnotifydatalen = strIndicateRxData.length(); //??????????????????
                                                        //??????????????????
                                                        final String strRxData = (String) strIndicateRxData.subSequence(2, strnotifydatalen);//?????? Device ????????????
                                                        boolean boolcompareresult = strRxData.equals(strBinData_0to15_Byte);

                                                        //System.arraycopy(??????, ????????????, ??????, ????????????, ????????????)
                                                        byte[] device_return_version_data_16Byte = new byte[16];
                                                        System.arraycopy(data, 1, device_return_version_data_16Byte, 0, 16);
                                                        final String strFwName = HexUtil.formatASCIIString(device_return_version_data_16Byte);

                                                        if (FOTA_USER_QUERY_FW_VERSION_ONLY) {
                                                            runOnUiThread(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    addText(HDM_txt, "=========Device information========", true);
                                                                    addText(HDM_txt, "Device FW Info: " + strRxData, true);
                                                                    addText(HDM_txt, "Device FW Ver Name: " + strFwName, true);
                                                                    addText(HDM_txt, "===================================", true);
                                                                    addText(HDM_txt, "", true);
                                                                }
                                                            });
                                                        }

                                                        if (boolcompareresult) {
                                                            //?????????????????????????????????????????????????????? => ?????? + break
                                                            Toast.makeText(getActivity(), getString(R.string.fota_same_version_no_need_update), Toast.LENGTH_LONG).show();
                                                            btn_ota_run.setEnabled(false);
                                                            btn_san_ble.setEnabled(true);
                                                            btn_sel_bin.setEnabled(true);

                                                            FOTA_States = FotaMsg.FOTA_STATE_INDICATION_RECEIVER_DEVICE_INFO_QUERY_NOPASS;
                                                        } else {
                                                            //?????????????????????????????????????????????????????????????????????
                                                            FOTA_States = FotaMsg.FOTA_STATE_INDICATION_RECEIVER_DEVICE_INFO_QUERY_PASS;
                                                        }
                                                        break;
                                                    case FotaMsg.FOTA_STATE_CMD_START:
                                                        char[] char_adrs = new char[3];
                                                        //char[] char_crc = new char[4];
                                                        final String strIndicateRxData2 = HexUtil.formatHexString(data, false);// parameter2 = false ; ???????????????????????????
                                                        //overnigth test ????????????
                                                        if (data[0] == 0x02) {// 0x02: ALREADY_START
                                                            //?????????????????????????????????CRC???????????????????????????????????????
                                                            // ?????????CRC??????????????????????????????(?????????)
                                                            // ?????????CRC???????????????ERASE command????????????????????????(????????????)
                                                            FOTA_INDICATE_STATES |= 0x04;
                                                            //char_crc[0] = (char) (data[1] & 0xFF); //LSB
                                                            //char_crc[1] = (char) (data[2] & 0xFF);
                                                            //char_crc[2] = (char) (data[3] & 0xFF);
                                                            //char_crc[3] = (char) (data[4] & 0xFF); //MSB
                                                            char_adrs[0] = (char) (data[9] & 0xFF); //LSB
                                                            char_adrs[1] = (char) (data[10] & 0xFF);
                                                            char_adrs[2] = (char) (data[11] & 0xFF); //MSB

                                                            //CRC32: CRC ?????? (load_bin_data[16 ~ 19])
                                                            if ((load_bin_data[19] == data[5]) && (load_bin_data[18] == data[6]) && (load_bin_data[17] == data[7]) && (load_bin_data[16] == data[8])) {
                                                                //Device?????????Address
                                                                runOnUiThread(new Runnable() {
                                                                    @Override
                                                                    public void run() {
                                                                        addText(HDM_txt, "CRC OK", true);
                                                                    }
                                                                });
                                                                IndicateUnfinishedDataCRCCompareResult = true;
                                                                IndicateUnfinishedDataNextAddress = (int) ((char_adrs[2] << 16) + (char_adrs[1] << 8) + (char_adrs[0]));
                                                            }
                                                            runOnUiThread(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    addText(HDM_txt, "Data" + strIndicateRxData2, true);
                                                                    addText(HDM_txt, "IndicateUnfinishedDataNextAddress=" + Integer.toString(IndicateUnfinishedDataNextAddress), true);
                                                                }
                                                            });
                                                            FotaRescuedflag = true;
                                                        } else {
                                                            IndicateUnfinishedDataNextAddress=0;
                                                            FotaRescuedflag = false;
                                                        }
                                                        FOTA_States = FotaMsg.FOTA_STATE_INDICATION_RECEIVER_DEVICE_INFO_START;
                                                        break;
                                                    case FotaMsg.FOTA_STATE_CMD_APPLY_UPDATE:    // ????????????????????????????????????
                                                        // ???Data = 0x00 ??????????????????????????????????????????
                                                        // ???????????????????????????????????????????????????log ?????????
                                                        if (data[0] == 0x00) {
                                                            if (!FotaRescuedflag) {
                                                                FotaSuccessNum += 1;
                                                            } else {
                                                                FotaRescuedNum += 1;
                                                            }
                                                        } else {
                                                            FotaFailureNum += 1;
                                                        }

                                                        runOnUiThread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                addText(HDM_txt, "Result:   FotaSuccessNum=" + Integer.toString(FotaSuccessNum), true);
                                                                addText(HDM_txt, "Result:   FotaFailureNum=" + Integer.toString(FotaFailureNum), true);
                                                                addText(HDM_txt, "Result:   FotaRescuedNum=" + Integer.toString(FotaRescuedNum), true);
                                                            }
                                                        });
                                                        FOTA_States = FotaMsg.FOTA_STATE_INDICATION_RECEIVER_DEVICE_INFO_APPLY; //?????????????????????
                                                        break;
                                                    default:

                                                        break;
                                                }
                                            }
                                        });


                                while (FOTA_States != FotaMsg.FOTA_STATE_INDICATION_OPEN_SCCESS) {
                                    if (FOTA_States == FotaMsg.FOTA_STATE_INDICATION_OPEN_FAILURE) {
                                        //Indication open failure, APP actively disconnects DUT connection.
                                        if (BleManager.getInstance().isConnected(HDM_bleDevice)) {
                                            BleManager.getInstance().disconnect(HDM_bleDevice);
                                        }
                                        try {
                                            Thread.sleep(FotaMsg.FOTA_WAIT_NEXT_FOTA_PROCCESS_TIME_MS);
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
                                        //disconnect???exit while (FOTA_States != FotaMsg.FOTA_STATE_INDICATION_OPEN_SCCESS)
                                        try {
                                            Thread.sleep(FotaMsg.FOTA_DISCONNECTED_WAIT_TIME_IN_WHILE_LOOP);
                                        } catch (InterruptedException e) {
                                            // TODO Auto-generated catch block
                                            e.printStackTrace();
                                        }
                                        break;
                                    }
                                }

                                if (FOTA_States == FotaMsg.FOTA_STATE_INDICATION_OPEN_FAILURE) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            addText(HDM_txt, "indicate open Failure", false);
                                        }
                                    });
                                    FotaFailureNum += 1;
                                    // save
                                    SaveText2(HDM_txt); //Download folder
                                    SaveText(HDM_txt); //APP
                                    continue;
                                }

                                //private Boolean NotifyOccurredFlag = false;
                                //????????????????????????
                                //private int NotifyExpectedNextAddress = 0;

                                //notify
                                BleManager.getInstance().notify(
                                        HDM_bleDevice,
                                        FotaMsg.FOTASERVICEUUID,
                                        FotaMsg.FOTACHARACTERISTICUUID_WRITENORESPONSE_NOTIFY,
                                        new BleNotifyCallback() {
                                            @Override
                                            public void onNotifySuccess() {
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        addText(HDM_txt, "notify success", false);
                                                    }
                                                });
                                                FOTA_States = FotaMsg.FOTA_STATE_NOTIFY_OPEN_SCCESS;
                                            }

                                            @Override
                                            public void onNotifyFailure(final BleException exception) {
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        addText(HDM_txt, exception.toString(), false);
                                                    }
                                                });
                                                FOTA_States = FotaMsg.FOTA_STATE_NOTIFY_OPEN_FAILURE;
                                            }

                                            @Override
                                            public void onCharacteristicChanged(final byte[] data) {
                                                if (FOTA_States == FotaMsg.FOTA_STATE_SENT_DATA) //????????????????????????
                                                {
                                                    char[] char_adrs = new char[3];
                                                    char state_index = (char) (data[0] & 0xFF);
                                                    switch (state_index) {
                                                        case 0: //DATA_SUCCESS
                                                            NotifyOccurredFlag = true;
                                                            FOTA_NOTIFY_STATES = 0x00;
                                                            char_adrs[0] = (char) (data[1] & 0xFF); //LSB
                                                            char_adrs[1] = (char) (data[2] & 0xFF);
                                                            char_adrs[2] = (char) (data[3] & 0xFF); //MSB
                                                            //Device?????????Address
                                                            NotifyExpectedNextAddress = (int) ((char_adrs[2] << 16) + (char_adrs[1] << 8) + (char_adrs[0]));
                                                            break;
                                                        case 1: //DATA TIME OUT
                                                        case 2: //DATA_ADDRESS_UNEXPECTED
                                                        case 5: //DATA_ADDRESS_ERROR
                                                            NotifyOccurredFlag = true;
                                                            FOTA_NOTIFY_STATES |= (1 << state_index);
                                                            char_adrs[0] = (char) (data[1] & 0xFF); //LSB
                                                            char_adrs[1] = (char) (data[2] & 0xFF);
                                                            char_adrs[2] = (char) (data[3] & 0xFF); //MSB
                                                            //Device?????????Address
                                                            NotifyExpectedNextAddress = (int) ((char_adrs[2] << 16) + (char_adrs[1] << 8) + (char_adrs[0]));
                                                            break;
                                                        case 3: //DATA_LENGTH_ERR
                                                            NotifyOccurredFlag = true;
                                                            break;
                                                        case 4://DATA_TOTAL_LENGTH_ERR
                                                            NotifyOccurredFlag = true;
                                                            break;
                                                        default: //other
                                                            NotifyOccurredFlag = true;
                                                            break;
                                                    }
                                                }
/*
                                                // ????????????????????????????????????????????????????????????
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        //addText(txt, HexUtil.formatHexString(characteristic.getValue(), true));// parameter2 = true ; ???????????????????????????
                                                        addText(HDM_txt, "notify:" + HexUtil.formatHexString(data, true), false);

                                                        if (FOTA_States == FotaMsg.FOTA_STATE_SENT_DATA) //????????????????????????
                                                        {
                                                            char[] char_adrs = new char[3];
                                                            char state_index = (char) (data[0] & 0xFF);
                                                            switch (state_index) {
                                                                case 0: //DATA_SUCCESS
                                                                    NotifyOccurredFlag = true;
                                                                    FOTA_NOTIFY_STATES = 0x00;
                                                                    char_adrs[0] = (char) (data[1] & 0xFF); //LSB
                                                                    char_adrs[1] = (char) (data[2] & 0xFF);
                                                                    char_adrs[2] = (char) (data[3] & 0xFF); //MSB
                                                                    //Device?????????Address
                                                                    NotifyExpectedNextAddress = (int) ((char_adrs[2] << 16) + (char_adrs[1] << 8) + (char_adrs[0]));
                                                                    break;
                                                                case 1: //DATA TIME OUT
                                                                case 2: //DATA_ADDRESS_UNEXPECTED
                                                                case 5: //DATA_ADDRESS_ERROR
                                                                    NotifyOccurredFlag = true;
                                                                    FOTA_NOTIFY_STATES |= (1 << state_index);
                                                                    char_adrs[0] = (char) (data[1] & 0xFF); //LSB
                                                                    char_adrs[1] = (char) (data[2] & 0xFF);
                                                                    char_adrs[2] = (char) (data[3] & 0xFF); //MSB
                                                                    //Device?????????Address
                                                                    NotifyExpectedNextAddress = (int) ((char_adrs[2] << 16) + (char_adrs[1] << 8) + (char_adrs[0]));
                                                                    break;
                                                                case 3: //DATA_LENGTH_ERR
                                                                    NotifyOccurredFlag = true;
                                                                    break;
                                                                case 4://DATA_TOTAL_LENGTH_ERR
                                                                    NotifyOccurredFlag = true;
                                                                    break;
                                                                default: //other
                                                                    NotifyOccurredFlag = true;
                                                                    break;
                                                            }
                                                        }
                                                    }
                                                });
*/
                                            }
                                        });


                                while (FOTA_States != FotaMsg.FOTA_STATE_NOTIFY_OPEN_SCCESS) {
                                    if (FOTA_States == FotaMsg.FOTA_STATE_NOTIFY_OPEN_FAILURE) {
                                        //Notify open failure, APP actively disconnects DUT connection.
                                        if (BleManager.getInstance().isConnected(HDM_bleDevice)) {
                                            BleManager.getInstance().disconnect(HDM_bleDevice);
                                        }
                                        try {
                                            Thread.sleep(FotaMsg.FOTA_WAIT_NEXT_FOTA_PROCCESS_TIME_MS);
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
                                        //disconnect???exit while loop(FOTA_States != FotaMsg.FOTA_STATE_INDICATION_OPEN_SCCESS)
                                        try {
                                            Thread.sleep(FotaMsg.FOTA_DISCONNECTED_WAIT_TIME_IN_WHILE_LOOP);
                                        } catch (InterruptedException e) {
                                            // TODO Auto-generated catch block
                                            e.printStackTrace();
                                        }
                                        break;
                                    }
                                }
                                if (FOTA_States == FotaMsg.FOTA_STATE_NOTIFY_OPEN_FAILURE) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            addText(HDM_txt, "notify open Failure", false);
                                        }
                                    });
                                    FotaFailureNum += 1;
                                    // save
                                    SaveText2(HDM_txt); //Download folder
                                    SaveText(HDM_txt); //APP
                                    continue;
                                }

                                //command 0x00: FOTA QUERY
                                FOTA_States = FotaMsg.FOTA_STATE_CMD_QUERY_INFORMATION;
                                byte[] wcmd = new byte[FotaMsg.FOTA_CMD_QUERY_LENGTH]; //write data
                                wcmd[0] = FotaMsg.FOTA_CMD_QUERY;
                                BleManager.getInstance().write(
                                        HDM_bleDevice,
                                        FotaMsg.FOTASERVICEUUID,
                                        FotaMsg.FOTACHARACTERISTICUUID_WRITE_INDICATE,
                                        //HexUtil.hexStringToBytes(hex), //??????Hex type (ex. ??????"4142" => device show "AB", ??????"31324142"=> device show "01AB")
                                        //HexUtil.charToByteAscii(hex), // ??????UTF-8 type (ex. ??????"abcde01234" => device show "abcde01234")
                                        wcmd,
                                        new BleWriteCallback() {
                                            @Override
                                            public void onWriteSuccess(final int current, final int total, final byte[] justWrite) {
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        addText(HDM_txt, "write success(cmd_query), current: " + current
                                                                + " total: " + total
                                                                + " justWrite: " + HexUtil.formatHexString(justWrite, true), false);
                                                    }
                                                });
                                            }

                                            @Override
                                            public void onWriteFailure(final BleException exception) {
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        addText(HDM_txt, exception.toString(), false);
                                                    }
                                                });
                                                FOTA_States = FotaMsg.FOTA_STATE_INDICATION_NO_RECEIVER_DEVICE_INFO_QUERY;
                                            }
                                        });

                                while ((FOTA_States != FotaMsg.FOTA_STATE_INDICATION_RECEIVER_DEVICE_INFO_QUERY_PASS) && (FOTA_States != FotaMsg.FOTA_STATE_INDICATION_RECEIVER_DEVICE_INFO_QUERY_NOPASS)) {
                                    if (FOTA_States == FotaMsg.FOTA_STATE_INDICATION_NO_RECEIVER_DEVICE_INFO_QUERY) {
                                        //Indication no receiver device info query, APP actively disconnects DUT connection.
                                        if (BleManager.getInstance().isConnected(HDM_bleDevice)) {
                                            BleManager.getInstance().disconnect(HDM_bleDevice);
                                        }
                                        try {
                                            Thread.sleep(FotaMsg.FOTA_WAIT_NEXT_FOTA_PROCCESS_TIME_MS);
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
                                        //disconnect???exit while (FOTA_States != FotaMsg.FOTA_STATE_INDICATION_RECEIVER_DEVICE_INFO_QUERY)
                                        try {
                                            Thread.sleep(FotaMsg.FOTA_DISCONNECTED_WAIT_TIME_IN_WHILE_LOOP);
                                        } catch (InterruptedException e) {
                                            // TODO Auto-generated catch block
                                            e.printStackTrace();
                                        }
                                        break;
                                    }
                                }

                                if (FOTA_States == FotaMsg.FOTA_STATE_INDICATION_NO_RECEIVER_DEVICE_INFO_QUERY) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            addText(HDM_txt, "no receiver info query", false);
                                        }
                                    });
                                    FotaFailureNum += 1;
                                    // save
                                    SaveText2(HDM_txt); //Download folder
                                    SaveText(HDM_txt); //APP
                                    continue;
                                } else if (FOTA_USER_QUERY_FW_VERSION_ONLY) {
                                    FOTA_USER_QUERY_FW_VERSION_ONLY = false;
                                    //?????????????????????FOTA??????

                                    final String showVersionResult;
                                    if (FOTA_States == FotaMsg.FOTA_STATE_INDICATION_RECEIVER_DEVICE_INFO_QUERY_NOPASS) {
                                        //????????????
                                        showVersionResult = "FW version is the same, no need to update.";
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                btn_ota_run.setEnabled(false);
                                            }
                                        });

                                    } else {
                                        //????????????
                                        showVersionResult = "The FW version is different and can be updated.";
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                btn_ota_run.setEnabled(true);
                                            }
                                        });
                                    }
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            addText(HDM_txt, "========FW compare result========", true);
                                            addText(HDM_txt, showVersionResult, true);
                                            addText(HDM_txt, "=================================", true);
                                            addText(HDM_txt, "", true);
                                        }
                                    });
                                    write_stop_test_flag = 1; //????????????

                                    continue; //???????????? while ??????
                                } else if ((FOTA_States == FotaMsg.FOTA_STATE_INDICATION_RECEIVER_DEVICE_INFO_QUERY_NOPASS) && ((FotaMsg.FOTA_VERIFY_FW_VERSION) || (app.getReleaseToCTMR()))) {
                                    //?????????????????????FOTA??????
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            addText(HDM_txt, "========End firmware update========", true);
                                            addText(HDM_txt, "FW version is the same, no need to update.", true);
                                            addText(HDM_txt, "===================================", true);
                                            addText(HDM_txt, "", true);
                                        }
                                    });
                                    write_stop_test_flag = 1; //????????????
                                    continue; //????????????while??????
                                }
                            } //if(FOTA_States!=FotaMsg.FOTA_STATE_CMD_START) {

                            char[] char_temp = new char[4];
                            //CRC ?????? (load_bin_data[16 ~ 19])
                            //?????????????????? (load_bin_data[20 ~ 23])
                            //?????????????????? (load_bin_data[24 ~ 27])
                            char_temp[0] = (char) (load_bin_data[20] & 0xFF);
                            char_temp[1] = (char) (load_bin_data[21] & 0xFF);
                            char_temp[2] = (char) (load_bin_data[22] & 0xFF);
                            char_temp[3] = (char) (load_bin_data[23] & 0xFF);
                            int data_start_adrs = (int) ((char_temp[0] << 24) + (char_temp[1] << 16) + (char_temp[2] << 8) + (char_temp[3]));
                            int load_bin_file_start_adrs = (int) ((char_temp[0] << 24) + (char_temp[1] << 16) + (char_temp[2] << 8) + (char_temp[3]));
                            char_temp[0] = (char) (load_bin_data[24] & 0xFF);
                            char_temp[1] = (char) (load_bin_data[25] & 0xFF);
                            char_temp[2] = (char) (load_bin_data[26] & 0xFF);
                            char_temp[3] = (char) (load_bin_data[27] & 0xFF);
                            int data_stop_adrs = (int) ((char_temp[0] << 24) + (char_temp[1] << 16) + (char_temp[2] << 8) + (char_temp[3]));

                            //int data_start_adrs = ((load_bin_data[20]<<24)+(load_bin_data[21]<<16)+(load_bin_data[22]<<8)+(load_bin_data[23]));
                            //int data_stop_adrs = ((load_bin_data[24]<<24)+(load_bin_data[25]<<16)+(load_bin_data[26]<<8)+(load_bin_data[27]));
                            //int total_length = (int)(data_stop_adrs - data_start_adrs);// 0x0000 ~ 0xF5FF (F600)
                            //int total_length = data_stop_adrs;// 0x0000 ~ 0xF5FF (F600)
                            int data_total_length = data_stop_adrs - data_start_adrs; // 0x0000 ~ 0xF5FF (F600)

                            //command 0x01: FOTA START
                            FOTA_States = FotaMsg.FOTA_STATE_CMD_START;

                            byte[] wcmd_start = new byte[FotaMsg.FOTA_CMD_START_LENGTH]; //write data
                            wcmd_start[0] = FotaMsg.FOTA_CMD_START;
                            //Data Length
                            wcmd_start[1] = (byte) (data_total_length & 0xff); //LSB
                            wcmd_start[2] = (byte) ((data_total_length >> 8) & 0xff);
                            wcmd_start[3] = (byte) ((data_total_length >> 16) & 0xff);
                            wcmd_start[4] = (byte) ((data_total_length >> 24) & 0xff); //MSB
                            //CRC32: CRC ?????? (load_bin_data[16 ~ 19])
                            wcmd_start[5] = (byte) (load_bin_data[19]); //LSB
                            wcmd_start[6] = (byte) (load_bin_data[18]);
                            wcmd_start[7] = (byte) (load_bin_data[17]);
                            wcmd_start[8] = (byte) (load_bin_data[16]); //MSB
                            //notify interval(?????????Byte???????????????Notify)
                            wcmd_start[9] = (byte) (fota_user_sel_notify_interval_length & 0xff); //LSB
                            wcmd_start[10] = (byte) ((fota_user_sel_notify_interval_length >> 8) & 0xff);
                            wcmd_start[11] = (byte) ((fota_user_sel_notify_interval_length >> 16) & 0xff);
                            wcmd_start[12] = (byte) ((fota_user_sel_notify_interval_length >> 24) & 0xff); //MSB

                            BleManager.getInstance().write(
                                    HDM_bleDevice,
                                    FotaMsg.FOTASERVICEUUID,
                                    FotaMsg.FOTACHARACTERISTICUUID_WRITE_INDICATE,
                                    //HexUtil.hexStringToBytes(hex), //??????Hex type (ex. ??????"4142" => device show "AB", ??????"31324142"=> device show "01AB")
                                    //HexUtil.charToByteAscii(hex), // ??????UTF-8 type (ex. ??????"abcde01234" => device show "abcde01234")
                                    wcmd_start,
                                    new BleWriteCallback() {
                                        @Override
                                        public void onWriteSuccess(final int current, final int total, final byte[] justWrite) {
                                            // ???????????????????????????
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    addText(HDM_txt, "write success(cmd_start), current: " + current
                                                            + " total: " + total
                                                            + " justWrite: " + HexUtil.formatHexString(justWrite, true), false);
                                                }
                                            });
                                        }

                                        @Override
                                        public void onWriteFailure(final BleException exception) {
                                            // ???????????????????????????
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    addText(HDM_txt, exception.toString(), false);
                                                }
                                            });
                                            FOTA_States = FotaMsg.FOTA_STATE_INDICATION_NO_RECEIVER_DEVICE_INFO_START;
                                        }
                                    });

                            while (FOTA_States != FotaMsg.FOTA_STATE_INDICATION_RECEIVER_DEVICE_INFO_START) {
                                if(FOTA_States == FotaMsg.FOTA_STATE_INDICATION_NO_RECEIVER_DEVICE_INFO_START) {
                                    //Indication no receiver device start information, APP actively disconnects DUT connection.
                                    if (BleManager.getInstance().isConnected(HDM_bleDevice)) {
                                        BleManager.getInstance().disconnect(HDM_bleDevice);
                                    }
                                    try {
                                        Thread.sleep(FotaMsg.FOTA_WAIT_NEXT_FOTA_PROCCESS_TIME_MS);
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

                                if((!BleManager.getInstance().isConnected(HDM_bleDevice)) || (app.getReConnectSuccessFlag())) {
                                    //disconnect???exit while (FOTA_States != FotaMsg.FOTA_STATE_INDICATION_RECEIVER_DEVICE_INFO_START)
                                    try {
                                        Thread.sleep(FotaMsg.FOTA_DISCONNECTED_WAIT_TIME_IN_WHILE_LOOP);
                                    } catch (InterruptedException e) {
                                        // TODO Auto-generated catch block
                                        e.printStackTrace();
                                    }
                                    break;
                                }
                            }
                            if(FOTA_States == FotaMsg.FOTA_STATE_INDICATION_NO_RECEIVER_DEVICE_INFO_START)
                            {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        addText(HDM_txt, "no receiver info start", false);
                                    }
                                });
                                FotaFailureNum += 1;
                                // save
                                SaveText2(HDM_txt); //Download folder
                                SaveText(HDM_txt); //APP
                                continue;
                            }

                            //bit[0]:
                            //bit[1]: CMD_ERR
                            //bit[2]: ALREADY_START
                            //bit[3]: NOT_START
                            //bit[4]:
                            //bit[5]:
                            //bit[6]:
                            //bit[7]:
                            if((FOTA_INDICATE_STATES&0x04) == 0x04)
                            {
                                //Device???????????????????????????????????????
                                FOTA_INDICATE_STATES &= 0xFB;
                                if((FotaMsg.FOTA_RESCUE_UNFINISHED_DATA) && (IndicateUnfinishedDataCRCCompareResult)){//??????????????????CRC???????????????????????????Device ??????Address???????????????
                                    if(!FotaMsg.FOTA_AUTO_SHIFT_START_ADDRESS_FROM_ZREO) {
                                        data_start_adrs = IndicateUnfinishedDataNextAddress;
                                    }
                                    else{
                                        data_start_adrs += IndicateUnfinishedDataNextAddress;
                                    }
                                    FotaTransmittedLength = 0;
                                }else{
                                    //????????????command???ERASE???ERASE???????????????
                                    byte[] wcmd_erase = new byte[FotaMsg.FOTA_CMD_QUERY_LENGTH]; //write data
                                    wcmd_erase[0] = FotaMsg.FOTA_CMD_ERASE;
                                    BleManager.getInstance().write(
                                            HDM_bleDevice,
                                            FotaMsg.FOTASERVICEUUID,
                                            FotaMsg.FOTACHARACTERISTICUUID_WRITE_INDICATE,
                                            //HexUtil.hexStringToBytes(hex), //??????Hex type (ex. ??????"4142" => device show "AB", ??????"31324142"=> device show "01AB")
                                            //HexUtil.charToByteAscii(hex), // ??????UTF-8 type (ex. ??????"abcde01234" => device show "abcde01234")
                                            wcmd_erase,
                                            new BleWriteCallback() {
                                                @Override
                                                public void onWriteSuccess(final int current, final int total, final byte[] justWrite) {
                                                    runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            addText(HDM_txt, "ERASE write success, current: " + current
                                                                    + " total: " + total
                                                                    + " justWrite: " + HexUtil.formatHexString(justWrite, true), false);
                                                        }
                                                    });
                                                }

                                                @Override
                                                public void onWriteFailure(final BleException exception) {
                                                    runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            addText(HDM_txt, exception.toString(), false);
                                                        }
                                                    });
                                                }
                                            });
                                    //?????????ERASE???????????????????????????????????????
                                    // save
                                    SaveText2(HDM_txt); //Download folder
                                    SaveText(HDM_txt); //APP
                                    continue;
                                }
                            }

                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }

                            FOTA_States = FotaMsg.FOTA_STATE_SENT_DATA;
                            Number_of_remaining_data = ((data_stop_adrs - data_start_adrs) % fota_one_package_data_length);
                            if(Number_of_remaining_data != 0) {
                                Number_of_packets = ((data_stop_adrs - data_start_adrs) / fota_one_package_data_length) + 1;
                            }
                            else{
                                Number_of_packets = ((data_stop_adrs - data_start_adrs) / fota_one_package_data_length);
                            }

                            //prepare data array
                            for (int wlength = 0; wlength < Number_of_packets; wlength++) {
                                final byte[] wsubdata = new byte[fota_one_package_data_length + 4]; //write data
                                if(!FotaMsg.FOTA_AUTO_SHIFT_START_ADDRESS_FROM_ZREO) {
                                    wsubdata[0] = (byte) (((wlength * fota_one_package_data_length) + data_start_adrs) & 0xff); //LSB
                                    wsubdata[1] = (byte) ((((wlength * fota_one_package_data_length) + data_start_adrs) >> 8) & 0xff);
                                    wsubdata[2] = (byte) ((((wlength * fota_one_package_data_length) + data_start_adrs) >> 16) & 0xff); //MSB
                                }
                                else {
                                    wsubdata[0] = (byte) (((wlength * fota_one_package_data_length)+IndicateUnfinishedDataNextAddress) & 0xff); //LSB
                                    wsubdata[1] = (byte) ((((wlength * fota_one_package_data_length)+IndicateUnfinishedDataNextAddress) >> 8) & 0xff);
                                    wsubdata[2] = (byte) ((((wlength * fota_one_package_data_length)+IndicateUnfinishedDataNextAddress) >> 16) & 0xff); //MSB
                                }
                                //wsubdata[3] = (byte) fota_one_package_data_length; //length

                                //System.arraycopy(??????, ????????????, ??????, ????????????, ????????????)
                                //???????????????????????? data_start_adrs
                                //System.arraycopy(load_bin_data, ((wlength * fota_one_package_data_length) + data_start_adrs + 32), wsubdata, 4, fota_one_package_data_length);
                                if((Number_of_remaining_data!=0) && (wlength==(Number_of_packets-1))){ //??????????????????????????????(??????1????????????)
                                    wsubdata[3] = (byte) Number_of_remaining_data; //length
                                    System.arraycopy(load_bin_data, ((wlength * fota_one_package_data_length) + data_start_adrs + 32), wsubdata, 4, Number_of_remaining_data);
                                    System.arraycopy(wsubdata, 0, load_bin_data_include_defined_adrs, (wlength * (fota_one_package_data_length+4)), (Number_of_remaining_data+4));
                                }
                                else {
                                    wsubdata[3] = (byte) fota_one_package_data_length; //length
                                    System.arraycopy(load_bin_data, ((wlength * fota_one_package_data_length) + data_start_adrs + 32), wsubdata, 4, fota_one_package_data_length);
                                    System.arraycopy(wsubdata, 0, load_bin_data_include_defined_adrs, (wlength * (fota_one_package_data_length + 4)), (fota_one_package_data_length + 4));
                                }
                            }

                            //**????????????????????????
                            BleManager.getInstance().setSplitWriteNum(fota_one_package_data_length+4);
                            //**???????????????????????????????????????????????????
                            BleManager.getInstance().setInterval_Between_TwoPackage((long) 0);

                            FotaTransmittedLength=0;//***?????????FOTA(debug mode)??????????????????
                            Date savetimest;
                            Date savetimesp;
                            long timediff;
                            savetimest = new Date(System.currentTimeMillis());
                            StartDate = new Date(System.currentTimeMillis()); //??????"??????"????????????(Start)
                            for (int wlength = 0; wlength < Number_of_packets; wlength++) { // fota_one_package_data_length
                                final byte[] wdata; //write data

                                if((wlength+(fota_user_sel_notify_interval_length/fota_one_package_data_length))<=((data_stop_adrs - data_start_adrs) / fota_one_package_data_length)){
                                    wdata = new byte[fota_user_sel_notify_interval_length+4*(fota_user_sel_notify_interval_length/fota_one_package_data_length)]; //write data
                                    System.arraycopy(load_bin_data_include_defined_adrs, (wlength * (fota_one_package_data_length+4)), wdata, 0, (fota_user_sel_notify_interval_length+4*(fota_user_sel_notify_interval_length/fota_one_package_data_length)));
                                    fota_Notyet_transmitted_length = fota_user_sel_notify_interval_length;
                                }
                                else{
                                    if(Number_of_remaining_data!=0) { //???????????????????????????
                                        wdata = new byte[((Number_of_packets - 1 - wlength)*(fota_one_package_data_length+4)) + (Number_of_remaining_data + 4)]; //write data
                                        System.arraycopy(load_bin_data_include_defined_adrs, (wlength * (fota_one_package_data_length+4)), wdata, 0, (((Number_of_packets - 1 - wlength)*(fota_one_package_data_length+4)) + (Number_of_remaining_data + 4)));
                                        fota_Notyet_transmitted_length = (((Number_of_packets - 1 - wlength)*(fota_one_package_data_length)) + Number_of_remaining_data);
                                    }
                                    else{
                                        wdata = new byte[((((data_stop_adrs - data_start_adrs) / fota_one_package_data_length) - wlength)*(fota_one_package_data_length+4))]; //write data
                                        System.arraycopy(load_bin_data_include_defined_adrs, (wlength * (fota_one_package_data_length+4)), wdata, 0, ((((data_stop_adrs - data_start_adrs) / fota_one_package_data_length) - wlength)*(fota_one_package_data_length+4)));
                                        fota_Notyet_transmitted_length = ((((data_stop_adrs - data_start_adrs) / fota_one_package_data_length) - wlength)*(fota_one_package_data_length));
                                    }
                                }

                                //Calculate the progress percentage
                                Percentage_val = ((wlength+((data_start_adrs-load_bin_file_start_adrs)/fota_one_package_data_length)) * 100) / (data_total_length / fota_one_package_data_length);
                                if ((Percentage_preval) != (Percentage_val)) { //1step = 10% show to panel,???????????????TX??????
                                    //Change progress bar
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            changeProgressBar(HDM_PBar1, Percentage_val);
                                        }
                                    });
                                    Percentage_preval = Percentage_val;
                                }



//                                savetimesp = new Date(System.currentTimeMillis());   //????????????
//                                timediff = savetimesp.getTime() - savetimest.getTime();
//                                if((timediff/1000)>=FotaMsg.FOTA_SAVE_TIME){// WRITE_STRESS_SAVE_TIME sec to save file //???????????????
//                                    savetimest = savetimesp;
//                                    timediff = 0;
//                                    // save
//                                    SaveText2(HDM_txt); //Download folder
//                                    SaveText(HDM_txt); //APP
//                                    if(app.getReleaseToCTMR()) {
//                                        runOnUiThread(new Runnable() {
//                                            @Override
//                                            public void run() {
//                                                ClearText(HDM_txt);
//                                            }
//                                        });
//                                    }
//                                }

                                if (DelayTimeEachData != 0) {
                                    try {
                                        Thread.sleep(DelayTimeEachData);
                                    } catch (InterruptedException e) {
                                        // TODO Auto-generated catch block
                                        e.printStackTrace();
                                    }
                                }

                                Date waitNotifyTimeSt;
                                Date waitNotifyTimeSp;
                                long waitNotifyTimediff;
                                if(FotaTransmittedLength == fota_user_sel_notify_interval_length) {
                                    //Notify Interval, wait Notify, and clear this parameter
                                    int wait_notify_loopNum = 0;

                                    waitNotifyTimeSt = new Date(System.currentTimeMillis());
                                    while(!NotifyOccurredFlag){
                                        wait_notify_loopNum += 1;
                                        waitNotifyTimeSp = new Date(System.currentTimeMillis());
                                        waitNotifyTimediff = waitNotifyTimeSp.getTime() - waitNotifyTimeSt.getTime();
                                        if((waitNotifyTimediff/1000)>=FotaMsg.FOTA_NO_NOTIFY_RECEIVER_TIIME_OUT_UNIT_SEC)// /1000 ???????????????
                                        {
                                            waitNotifyTimeSt = waitNotifyTimeSp;
                                            waitNotifyTimediff = 0;
                                            // save
                                            SaveText2(HDM_txt); //Download folder
                                            SaveText(HDM_txt); //APP
                                            break; // while(NotifyOccurredFlag != true)
                                        }

                                        try {
                                            Thread.sleep(1);
                                        } catch (InterruptedException e) {
                                            // TODO Auto-generated catch block
                                            e.printStackTrace();
                                        }
                                    }
/*
                                    //??????loop??????
                                    final int finalWait_notify_loopNum = wait_notify_loopNum;
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            addText(HDM_txt, "===== "+ "Notify Wait Loop Num: "+Integer.toString(finalWait_notify_loopNum)+" =====", false);
                                        }
                                    });
*/
                                    final int finalWait_notify_loopNum = wait_notify_loopNum;
                                    if(FOTA_NOTIFY_STATES == 0x00) //DATA SUCCESS
                                    {
                                        //???????????????????????????????????????
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                addText(HDM_txt, "=== Notify success, wait Loop num: "+Integer.toString(finalWait_notify_loopNum)+" ===", false);
                                            }
                                        });
                                        FotaTransmittedLength = 0;
                                        NotifyOccurredFlag = false;
                                    }
                                    // save
                                    SaveText2(HDM_txt); //Download folder
                                    SaveText(HDM_txt); //APP
                                }

                                //Notify, ???????????????????
                                if (NotifyOccurredFlag) {
                                    //????????????
                                    //bit[0]:
                                    //bit[1]: DATA TIME OUT
                                    //bit[2]: DATA_ADDRESS_UNEXPECTED
                                    //bit[3]:
                                    //bit[4]:
                                    //bit[5]: DATA_ADDRESS_ERROR
                                    //bit[6]:
                                    //bit[7]:
                                    while(FOTA_NOTIFY_STATES!=0x00) {
                                        if(((FOTA_NOTIFY_STATES & 0x02)==0x02) || (FOTA_NOTIFY_STATES & 0x04)==0x04) {
                                            /*
                                            try {
                                                Thread.sleep(5000); //try ????????????5???
                                            } catch (InterruptedException e) {
                                                // TODO Auto-generated catch block
                                                e.printStackTrace();
                                            }
                                            //bit[1]: DATA TIME OUT
                                            //bit[2]: DATA_ADDRESS_UNEXPECTED
                                            FOTA_NOTIFY_STATES &= 0xF9;
                                            //Notify ???????????????????????????????????????
                                            FotaTransmittedLength = 0;
                                            //Device ????????????Address??????????????????????????????????????????Address????????????
                                            wlength = ((NotifyExpectedNextAddress) / fota_one_package_data_length);
                                            //print info
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    addText(HDM_txt, "FotaNotifyStates :"+ FOTA_NOTIFY_STATES + ", restart form address:  "
                                                            + NotifyExpectedNextAddress, false);
                                                }
                                            });
                                            try {
                                                Thread.sleep(10);
                                            } catch (InterruptedException e) {
                                                // TODO Auto-generated catch block
                                                e.printStackTrace();
                                            }
                                            */
                                            //bit[1]: DATA TIME OUT
                                            //bit[2]: DATA_ADDRESS_UNEXPECTED
                                            FOTA_NOTIFY_STATES &= 0xF9;
                                            FotaTransmittedLength = 0;
                                            FOTA_States = FotaMsg.FOTA_STATE_CMD_START;
                                        }
                                        else {
                                            //clear states
                                            FOTA_NOTIFY_STATES &= 0x06;
                                        }
                                    }//while(FOTA_NOTIFY_STATES!=0x00)
                                    NotifyOccurredFlag = false;

                                    //??????Notify ????????????????????????????????????START?????????Address???
                                    if(FOTA_States == FotaMsg.FOTA_STATE_CMD_START)
                                    {
                                        break; //??????For ??????
                                    }
                                } //if(NotifyOccurredFlag) {


                                /*
                                FotaTransmittedLength += fota_one_package_data_length;

                                wdata[0] = (byte) (((wlength * fota_one_package_data_length) + data_start_adrs) & 0xff); //LSB
                                wdata[1] = (byte) ((((wlength * fota_one_package_data_length) + data_start_adrs) >> 8) & 0xff);
                                wdata[2] = (byte) ((((wlength * fota_one_package_data_length) + data_start_adrs) >> 16) & 0xff); //MSB
                                wdata[3] = (byte) fota_one_package_data_length; //length
                                //1.???copy???????????????
                                //2.???????????????????????????(???????????????????????????????????????)
                                //3.?????????copy???????????????????????????
                                //4.?????????????????????()
                                //5.??????????????????????????????(copy??????)

                                //(+32): ???????????????32Byte(0~31 Byte) ???OTA CRC ???Start???Stop Address????????????Data?????????32Byte???????????????
                                //System.arraycopy(load_bin_data, ((wlength * fota_one_package_data_length) + 32), wdata, 4, fota_one_package_data_length);
                                //???????????????????????? data_start_adrs
                                System.arraycopy(load_bin_data, ((wlength * fota_one_package_data_length) + data_start_adrs + 32), wdata, 4, fota_one_package_data_length);
                                */


                                //Debug ????????????????????????
                                /*
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        addText(HDM_txt, "write ready:  " + Percentage_val + "%  :"
                                                + HexUtil.formatHexString(wdata, true), false);
                                    }
                                });
                                */

                                fota_Already_transmitted_length = 0; //clear
                                BleManager.getInstance().write(
                                        HDM_bleDevice,
                                        FotaMsg.FOTASERVICEUUID,
                                        FotaMsg.FOTACHARACTERISTICUUID_WRITENORESPONSE_NOTIFY,
                                        wdata,
                                        new BleWriteCallback() {
                                            @Override
                                            public void onWriteSuccess(final int current, final int total, final byte[] justWrite) {
                                                /*
                                                //write done and success
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        addText(HDM_txt, "write success, current: " + current
                                                                + " total: " + total
                                                                + " justWrite: " + HexUtil.formatHexString(justWrite), false);

                                                        //addText(HDM_txt, "write success:  " + Percentage_val + "%  :"
                                                        //      + HexUtil.formatHexString(justWrite, true), false);

                                                        //addText(HDM_txt, "write success:  " + Percentage_val + "%", false);
                                                        SaveText2(HDM_txt); //Download
                                                        SaveText(HDM_txt); //APP
                                                    }
                                                });
                                                 */
                                                if(current == total){
                                                    write_one_package_end_falg = 1;
                                                    write_success_falg = 1;
                                                }
                                                fota_Already_transmitted_length += fota_one_package_data_length;
                                                fota_Notyet_transmitted_length -= fota_one_package_data_length;
                                            }

                                            @Override
                                            public void onWriteFailure(final BleException exception) {
                                                //write done and fail ????????????
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        //addText(HDM_txt, exception.toString() + "resend-"  + Integer.toString(fota_Notyet_transmitted_length), false);
                                                        addText(HDM_txt, exception.toString(), false);
                                                    }
                                                });

                                                write_one_package_end_falg = 1;
                                                write_success_falg = 0;
                                                fota_Notyet_transmitted_length -= fota_one_package_data_length;

                                                //??????????????????????????????????????????????????????????????????????????????????????????????????????????????????
                                                //Samsung A31 ?????????????????????????????????
                                                if(FotaMsg.FOTA_WAIT_FAILURE_DATA_TO_BE_TRANSMITTED) {
                                                    if (fota_Notyet_transmitted_length > 0) {
                                                        app.setReConnectWait(true); //????????????
                                                    } else {
                                                        app.setReConnectWait(false); //????????????
                                                    }
                                                }
                                            }
                                        });

                                //=======================================================
                                boolean disConnectFlag = false;
                                //?????????????????? write_one_package_end_falg ??????true???????????????true????????????1?????????????????????
                                //??????????????? write_success_falg ????????????????????????
                                //???????????????????????????????????????????????????????????????????????????????????????????????????????????? FOTA_STATE_CMD_START ??????
                                //if(FotaMsg.FOTA_WAIT_FAILURE_DATA_TO_BE_TRANSMITTED) {
                                    long datatimecount=0;
                                    int fota_Notyet_transmitted_length_temp=fota_Notyet_transmitted_length;
                                    while ((write_one_package_end_falg == 0) || ((write_one_package_end_falg == 1) && (fota_Notyet_transmitted_length > 0)) && (datatimecount==10000)) {
                                        //while (write_one_package_end_falg==0) {
                                        try {
                                            Thread.sleep(1);
                                        } catch (InterruptedException e) {
                                            // TODO Auto-generated catch block
                                            e.printStackTrace();
                                        }

                                        if(fota_Notyet_transmitted_length_temp==fota_Notyet_transmitted_length) {
                                            datatimecount += 1;
                                        }
                                        else{
                                            datatimecount = 0;
                                            fota_Notyet_transmitted_length_temp=fota_Notyet_transmitted_length;
                                        }

                                        //????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
                                        //?????????:??????????????????????????????????????????????????????Fail?????????call???????????????????????????????????????Issue.
                                        if ((!BleManager.getInstance().isConnected(HDM_bleDevice)) || (app.getReConnectSuccessFlag())) {
                                            //??????????????????
                                            disConnectFlag = true;
                                        }
                                    }
//                                }
//                                else{
//                                     while (write_one_package_end_falg==0) {
//                                        try {
//                                            Thread.sleep(1);
//                                        } catch (InterruptedException e) {
//                                            // TODO Auto-generated catch block
//                                            e.printStackTrace();
//                                        }
//                                    }
//                                }


                                //if ((write_one_package_end_falg == 1) && (write_success_falg == 0)) { //??????????????????????????? write_one_package_end_falg = 0 ?????????
                                if (write_success_falg == 0) {
                                    //???????????????????????????????????????

                                    /*
                                    wlength = wlength - 1;
                                    try {
                                        Thread.sleep(10);
                                    } catch (InterruptedException e) {
                                        // TODO Auto-generated catch block
                                        e.printStackTrace();
                                    }
                                    */

                                    //FotaTransmittedLength = 0;
                                    //FOTA_States = FotaMsg.FOTA_STATE_CMD_START;
                                    //??????for loop
                                    //break;
                                    //if((!BleManager.getInstance().isConnected(HDM_bleDevice)) || (app.getReConnectSuccessFlag())) {
                                    if(disConnectFlag){
                                        //???????????????????????? for loop
                                        write_one_package_end_falg = 0;
                                        write_success_falg = 0;
                                        break;
                                    }
                                    else{
                                        // ???????????????????????????????????????FOTA_STATE_CMD_START ??????
                                        FotaTransmittedLength = 0;
                                        FOTA_States = FotaMsg.FOTA_STATE_CMD_START;
                                        write_one_package_end_falg = 0;
                                        write_success_falg = 0;
                                        break;
                                    }
                                }
                                else{
                                    //FotaTransmittedLength = fota_user_sel_notify_interval_length;
                                    FotaTransmittedLength = fota_Already_transmitted_length;
                                    wlength += ((FotaTransmittedLength/fota_one_package_data_length)-1); // -1???????????????for loop???+1
                                    //for (int wlength = 0; wlength < ((data_stop_adrs - data_start_adrs) / fota_one_package_data_length); wlength++) { // fota_one_package_data_length
                                }
                                write_one_package_end_falg = 0;
                                write_success_falg = 0;

                                /*
                                if(FOTA_States == FotaMsg.FOTA_STATE_CMD_START)
                                {
                                    try {
                                        Thread.sleep(20000);
                                    } catch (InterruptedException e) {
                                        // TODO Auto-generated catch block
                                        e.printStackTrace();
                                    }
                                    break; //??????For ??????
                                }
                                 */

                                //disconnect???exit for loop
                                if((!BleManager.getInstance().isConnected(HDM_bleDevice)) || (app.getReConnectSuccessFlag())) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            addText(HDM_txt, "data sent Failure, exit data send loop", false);
                                        }
                                    });
                                    break;
                                }
                            }//for(int wlength=0;wlength< ((data_stop_adrs-data_start_adrs)/fota_one_package_data_length) ;wlength++) { // fota_one_package_data_length

                            if(FOTA_States == FotaMsg.FOTA_STATE_CMD_START){
                                //??????Notify ????????????CMD Start
                                // save
                                SaveText2(HDM_txt); //Download folder
                                SaveText(HDM_txt); //APP
                                continue;
                            }

                            if((!BleManager.getInstance().isConnected(HDM_bleDevice)) || (app.getReConnectSuccessFlag())) {
                                //disconnect, ???????????????while
                                try {
                                    Thread.sleep(FotaMsg.FOTA_DISCONNECTED_WAIT_TIME_IN_WHILE_LOOP);
                                } catch (InterruptedException e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                }
                                FotaFailureNum += 1;
                                // save
                                SaveText2(HDM_txt); //Download folder
                                SaveText(HDM_txt); //APP
                                continue;
                            }

                            //int data_start_adrs = ((load_bin_data[20]<<24)+(load_bin_data[21]<<16)+(load_bin_data[22]<<8)+(load_bin_data[23]));
                            //int data_stop_adrs = ((load_bin_data[24]<<24)+(load_bin_data[25]<<16)+(load_bin_data[26]<<8)+(load_bin_data[27]));
                            //int total_length = (int)(data_stop_adrs - data_start_adrs);// 0x0000 ~ 0xF5FF (F600)
                            //int total_length = data_stop_adrs;// 0x0000 ~ 0xF5FF (F600)
                            //command 0x01: FOTA Apply the update
                            FOTA_States = FotaMsg.FOTA_STATE_CMD_APPLY_UPDATE; //???????????? ???????????? //????????????????????? ????????????
                            final byte[] wcmd_apply = new byte[FotaMsg.FOTA_CMD_APPLY_UPDATE_LENGTH]; //write data
                            wcmd_apply[0] = (byte) FotaMsg.FOTA_CMD_APPLY_UPDATE;
                            //Start address
                            wcmd_apply[1] = (byte) (load_bin_data[23]); //LSB
                            wcmd_apply[2] = (byte) (load_bin_data[22]);
                            wcmd_apply[3] = (byte) (load_bin_data[21]);
                            wcmd_apply[4] = (byte) (load_bin_data[20]); //MSB

                            BleManager.getInstance().write(   //???  APPLY  CMD
                                    HDM_bleDevice,
                                    FotaMsg.FOTASERVICEUUID,
                                    FotaMsg.FOTACHARACTERISTICUUID_WRITE_INDICATE,
                                    //HexUtil.hexStringToBytes(hex), //??????Hex type (ex. ??????"4142" => device show "AB", ??????"31324142"=> device show "01AB")
                                    //HexUtil.charToByteAscii(hex), // ??????UTF-8 type (ex. ??????"abcde01234" => device show "abcde01234")
                                    wcmd_apply,
                                    new BleWriteCallback() {

                                        @Override
                                        public void onWriteSuccess(final int current, final int total, final byte[] justWrite) {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    addText(HDM_txt, "write success, current: " + current
                                                            + " total: " + total
                                                            + " justWrite: " + HexUtil.formatHexString(wcmd_apply, true), false);
                                                }
                                            });
                                        }

                                        @Override
                                        public void onWriteFailure(final BleException exception) {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    addText(HDM_txt, exception.toString(), false);
                                                }
                                            });
                                        }
                                    });

                            // Change the percentage to 100%
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    changeProgressBar(HDM_PBar1, 100);
                                }
                            });

                            while (FOTA_States != FotaMsg.FOTA_STATE_INDICATION_RECEIVER_DEVICE_INFO_APPLY) {
                                if(FOTA_States == FotaMsg.FOTA_STATE_INDICATION_NO_RECEIVER_DEVICE_INFO_APPLY) {
                                    //Indication no receiver device apply information, APP actively disconnects DUT connection.
                                    if (BleManager.getInstance().isConnected(HDM_bleDevice)) {
                                        BleManager.getInstance().disconnect(HDM_bleDevice);
                                    }
                                    try {
                                        Thread.sleep(FotaMsg.FOTA_WAIT_NEXT_FOTA_PROCCESS_TIME_MS);
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

                                if((!BleManager.getInstance().isConnected(HDM_bleDevice)) || (app.getReConnectSuccessFlag())) {
                                    //disconnect???exit while (FOTA_States != FotaMsg.FOTA_STATE_INDICATION_RECEIVER_DEVICE_INFO_APPLY)
                                    try {
                                        Thread.sleep(FotaMsg.FOTA_DISCONNECTED_WAIT_TIME_IN_WHILE_LOOP);
                                    } catch (InterruptedException e) {
                                        // TODO Auto-generated catch block
                                        e.printStackTrace();
                                    }
                                    break;
                                }
                            }

                            if(FOTA_States == FotaMsg.FOTA_STATE_INDICATION_NO_RECEIVER_DEVICE_INFO_APPLY)  //Device??????????????????
                            {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        addText(HDM_txt, "apply Failure", false);
                                    }
                                });
                                FotaFailureNum += 1;

                                // save
                                SaveText2(HDM_txt); //Download folder
                                SaveText(HDM_txt); //APP
                                continue;
                            }

                            //????????????
                            Calendar mCalStop = Calendar.getInstance();
                            Date StopDate = new Date(System.currentTimeMillis());

                            final CharSequence stoptime_kk = DateFormat.format("kk", mCalStop.getTime());    // kk:24?????????, hh:12?????????
                            final CharSequence stoptime_mm = DateFormat.format("mm", mCalStop.getTime());    // kk:24?????????, hh:12?????????
                            final CharSequence stoptime_ss = DateFormat.format("ss", mCalStop.getTime());    // kk:24?????????, hh:12?????????

                            final long diff = StopDate.getTime() - StartDate.getTime();

                            final int fota_use_time_mm = (int) (diff / 1000) / 60;
                            final int fota_use_time_ss = (int) (diff / 1000) - (int) fota_use_time_mm * 60;
                            final int fota_use_time_ms = (int) diff - ((int) (fota_use_time_mm * 60 * 1000) + (int) fota_use_time_ss*1000);

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    addText(HDM_txt, "stop time = " + (String) stoptime_kk + " : " + (String) stoptime_mm + " : " + (String) stoptime_ss
                                            + "       fota use time(min:sec) = " + fota_use_time_mm + " : " + fota_use_time_ss + " : " + fota_use_time_ms, true);
                                    addText(HDM_txt, "", true);
                                }
                            });


                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }

                            FOTA_States = FotaMsg.FOTA_STATE_IDAL;

                            if((FotaMsg.FOTA_CONTINUOUS_MODE) && (!app.getReleaseToCTMR())) {
                                //???????????????????????????
                                try {
                                    Thread.sleep(FotaMsg.FOTA_WAIT_NEXT_FOTA_PROCCESS_TIME_MS);
                                } catch (InterruptedException e) {
                                    // TODO Auto-generated catch block
                                    e.printStackTrace();
                                }
                            }
                            else{
                                write_stop_test_flag=1;
                            }
                            // save
                            SaveText2(HDM_txt); //Download
                            SaveText(HDM_txt); //APP
                            // Change the percentage to 0%
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    changeProgressBar(HDM_PBar1, 0);
                                }
                            });
                        }
                        write_stop_test_flag=0; //clear

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
//                                btn_ota_run.setEnabled(true);
                            }
                        });
                    }
                    break;

                    default:{

                    }
                    break;
                }
            }//if (characteristicoperationfragment != null) {
        }//public void handleMessage(Message msg) {
    }//private final class FotaHandler extends Handler {

    public void prepare() {
        mHandlerThread = new HandlerThread(AppFotaFragment.class.getSimpleName());
        mHandlerThread.start();
        mHandler = new FotaHandler(mHandlerThread.getLooper(), this);
        mHandling = true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_slideshow, null);
        //showData(view);
        app = new BleApplication();
//        final BleDevice bleDevice = ((BleApplicationActivity) getActivity()).getBleDevice(); //get BLE device  //?????????????????????

        btn_ota_run = (Button) view.findViewById(R.id.btn_runthroughput);
        btn_sel_bin = (Button) view.findViewById(R.id.btn_sel_bin);
        btn_ota_run.setEnabled(false); //????????????
        btn_sel_bin.setEnabled(false); //????????????

        btn_san_ble = (Button) view.findViewById(R.id.btn_san_ble);
        btn_san_ble.setOnClickListener(onClickScanBleButton);

        final Spinner spinner_mtu = (Spinner) view.findViewById(R.id.spinner2);
        ArrayAdapter<CharSequence> adapter2 = ArrayAdapter.createFromResource(getActivity(), R.array.mtu_data_array, android.R.layout.simple_spinner_item);
        adapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner_mtu.setAdapter(adapter2);

        _isSpinnerOnCreate = true;
        spinner_phy = (Spinner) view.findViewById(R.id.spinner3);
        ArrayAdapter<CharSequence> adapter_phy = ArrayAdapter.createFromResource(getActivity(), R.array.phy_array, android.R.layout.simple_spinner_item);
        adapter_phy.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner_phy.setAdapter(adapter_phy);
        spinner_phy.setOnItemSelectedListener(phy_slectListener);

        fotatxtlog = (TextView) view.findViewById(R.id.fotatxtlog);
        final ProgressBar PBar1 = (ProgressBar) view.findViewById(R.id.progressbar_thput);
        fotatxtlog.setMovementMethod(ScrollingMovementMethod.getInstance());

        bleDeviceText = (TextView) view.findViewById(R.id.BLE_DEVICE_TEXT);
        bleStatusText = (TextView) view.findViewById(R.id.BLE_STATUS_TEXT);
        if(BleManager.getInstance().isConnected(_connectBleDevice) == true){
            bleDeviceText.setText("BLE Device: "+_connectBleDevice.getDevice().getName());
            bleStatusText.setText("BLE Device: Connect Success");
//            btn_ota_run.setEnabled(false);
//            btn_san_ble.setEnabled(false);
//            btn_sel_bin.setEnabled(false);
        }
/*
        String device_adrs = bleDevice.getMac();
        fotatxttitle.setText("Fota log:  ( MAC: " + device_adrs +" )");*/  // ???????????????????????????

        PBar1.setProgress(0);

       /* if(app.getReleaseToCTMR())  //??????????????????????????????
        {
            //release mode
            et1.setText(String.valueOf(fota_user_sel_notify_interval_length));
            tv1.setVisibility(View.GONE);
            et1.setVisibility(View.GONE);
            spinner_phy.setVisibility(View.INVISIBLE);
        }*/

        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ArrayAdapter<CharSequence> adapter =
                    ArrayAdapter.createFromResource(getActivity(),
                            R.array.phy_array,
                            android.R.layout.simple_spinner_item);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner_phy.setAdapter(adapter);
        }
        else {
            spinner_phy.setVisibility(View.GONE);
        }*/


        //MTU
        AdapterView.OnItemSelectedListener spnOnItemSelected2 = new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view,int pos, long id) {

                if(FOTA_States==FotaMsg.FOTA_STATE_SENT_DATA)
                {
                    Toast.makeText(getActivity(), getString(R.string.error_fota_already_started_no_chang_mtu), Toast.LENGTH_LONG).show();
                }

                switch(pos)
                {
                    case 0:
                        fota_user_sel_mtu_data_length = 16;
                        break;
                    case 1:
                        fota_user_sel_mtu_data_length = 32;
                        break;
                    //case 2: //same default
                    //    fota_user_sel_mtu_data_length = 64;
                    //    break;
                    case 3:
                        fota_user_sel_mtu_data_length = 128;
                        break;
                    case 4:
                        fota_user_sel_mtu_data_length = 192;
                        break;
                    case 5:
                        fota_user_sel_mtu_data_length = 240;
                        break;
                    default:
                        fota_user_sel_mtu_data_length = 64;
                        break;
                }

            }
            public void onNothingSelected(AdapterView<?> parent) {
                //
            }
        };
/*
???????????????????????????
    //        spinner_phy.setOnItemSelectedListener(spnOnItemSelected);
    //        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    //            spinner_phy.setSelection(0); //PHY default is 2M
    //        }
    //        else{
    //            spinner_phy.setSelection(1); //PHY default is 1M
    //        }
*/
        spinner_mtu.setOnItemSelectedListener(spnOnItemSelected2);
        spinner_mtu.setSelection(5); //default is 240Byte.

        //select bin file
        btn_sel_bin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                btn_ota_run.setEnabled(false);
                //?????????????????????????????????(/sdcard)
//                String binpath = Environment.getExternalStorageDirectory().getPath();
                String binpath = Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS).getPath();
                binpath += "/fota_bin";

                File file = new File(binpath);

                if(!file.exists()) {
                    file.mkdir();
                }

                String[] fileList = file.list();

                final List<String> fileNameList = new ArrayList<>();

                if(fileList == null || fileList.length <=0){
                    Toast.makeText( getActivity(),binpath.toString() +" "+ getString(R.string.error_no_path_or_file), Toast.LENGTH_LONG).show();
                    return;
                }

                for (String f : fileList){
                    fileNameList.add(f);
                }

                final String finalBinPath = binpath; //temp
                new AlertDialog.Builder(getActivity())
                        .setTitle(getActivity().getString(R.string.select_bin_file_name))
                        .setItems(fileNameList.toArray(new String[fileNameList.size()]), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, final int which) {  //????????????
                                //load bin data
                                final byte[] load_bin_data_info = new byte[524320]; //512K Byte  (524,288+32=524,320)
                                FOTA_load_bin_name = fileNameList.get(which);
                                File file = new File(finalBinPath  + "/" + FOTA_load_bin_name);

                                try {
                                    FileInputStream fin = new FileInputStream(file);
                                    while (fin.read(load_bin_data_info) != -1) {
                                    }
                                    fin.close();
                                } catch (FileNotFoundException e) {
                                    e.printStackTrace();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                                final byte[] load_bin_data_0to15_device_info = new byte[16];
                                System.arraycopy(load_bin_data_info, 0, load_bin_data_0to15_device_info, 0, 16);
                                final String strBinData_0to15_Byte = HexUtil.formatHexString(load_bin_data_0to15_device_info, false);// parameter2 = false ; ???????????????????????????
                                final String strFwName = HexUtil.formatASCIIString(load_bin_data_0to15_device_info);
                                // print information
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        addText(fotatxtlog, "=======load file information======", true);
                                        addText(fotatxtlog, "load file: " + fileNameList.get(which), true);
                                        addText(fotatxtlog, "start adrs: " + encodeHexStr_OneByte((char)load_bin_data_info[20]) + encodeHexStr_OneByte((char)load_bin_data_info[21])
                                                + encodeHexStr_OneByte((char)load_bin_data_info[22]) + encodeHexStr_OneByte((char)load_bin_data_info[23]), false);
                                        addText(fotatxtlog, "stop adrs: " + encodeHexStr_OneByte((char)load_bin_data_info[24]) + encodeHexStr_OneByte((char)load_bin_data_info[25])
                                                + encodeHexStr_OneByte((char)load_bin_data_info[26]) + encodeHexStr_OneByte((char)load_bin_data_info[27]), false);
                                        addText(fotatxtlog, "CRC: " + encodeHexStr_OneByte((char)load_bin_data_info[16]) + encodeHexStr_OneByte((char)load_bin_data_info[17])
                                                + encodeHexStr_OneByte((char)load_bin_data_info[18]) + encodeHexStr_OneByte((char)load_bin_data_info[19]), false);
                                        addText(fotatxtlog, "Load FW Info: " + strBinData_0to15_Byte, true);
                                        addText(fotatxtlog, "Load FW Ver Name: " + strFwName, true);
                                        addText(fotatxtlog, "==================================", true);
                                        addText(fotatxtlog, "", true);
                                    }
                                });

                                //Only confirm the DUT version, do not execute FOTA.
                                FOTA_USER_QUERY_FW_VERSION_ONLY = true;
                                /*if(!app.getReleaseToCTMR()){
                                    fota_user_sel_notify_interval_length = Integer.parseInt(et1.getText().toString().trim());
                                }*/

                                //=================???????????????=======================
                                prepare();
                                if (!mHandling)
                                    return;
                                Message message = mHandler.obtainMessage();
                                message.what = FotaMsg.FOTA_START;
                                //message.obj = (TextView)txt;
                                message.obj = new Object[] { (TextView)fotatxtlog, (ProgressBar)PBar1};
                                mHandler.sendMessage(message);
                                //================================================

                            }
                        }).show();
            }//public void onClick(View view) {
        });//btn_sel_bin.setOnClickListener(new View.OnClickListener()


        //FOTA proccess
        btn_ota_run.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(FOTA_load_bin_name==null) {
                    //?????????????????????????????????????????????
                    Toast.makeText(getActivity(), getString(R.string.error_no_bin_file_name), Toast.LENGTH_LONG).show();
                    return;
                }

                if(app.getConnectInterval()==1) {
                    //(11.25ms???15ms) 1: CONNECTION_PRIORITY_HIGH
                    Toast.makeText(getActivity(), getString(R.string.error_no_support_connect_priority_high), Toast.LENGTH_LONG).show();
                    return;
                }

                /*if(!app.getReleaseToCTMR()){
                    fota_user_sel_notify_interval_length = Integer.parseInt(et1.getText().toString().trim());
                }*/

                btn_ota_run.setEnabled(false);
                btn_san_ble.setEnabled(false);
                btn_sel_bin.setEnabled(false);
                //=================???????????????=======================
                prepare();
                if (!mHandling)
                    return;
                Message message = mHandler.obtainMessage();
                message.what = FotaMsg.FOTA_START;
                //message.obj = (TextView)txt;
                message.obj = new Object[] {(TextView)fotatxtlog, (ProgressBar)PBar1};
                mHandler.sendMessage(message);
                //================================================

            }//public void onClick(View view) {
        });//btn2.setOnClickListener(new View.OnClickListener() {

        return view;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        /*
        try {
            if (requestCode == PICK_IMAGE_MULTIPLE && resultCode == RESULT_OK && null != data) {
                Toast.makeText(this, "Success to pick image", Toast.LENGTH_LONG).show();
                // ??????????????????????????? data ??????????????????????????????
            }
        } catch (Exception e) {
            Toast.makeText(this, "Something wrong", Toast.LENGTH_LONG).show();
        }
        */

        // ????????????
        switch(requestCode) {
            case FILE_SELECT_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    // Get the Uri of the selected file
                    Uri uri = data.getData();
                    Log.d(FotaMsg.FOTA_PROCCESS_TAG, "File Uri: " + uri.toString());
                    // Get the path
                    String path = getPathFromUri(getContext(), uri); // Paul Burke?????????????????????Uri??????????????????
                    Log.d(FotaMsg.FOTA_PROCCESS_TAG, "File Path: " + path);
                    // Get the file instance
                    // File file = new File(path);
                    // Initiate the upload
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }



//=============================????????????=================================================
    @SuppressLint("NewApi")
    public static String getPathFromUri(final Context context, final Uri uri) {
        if (uri == null) {
            return null;
        }
        // ???????????????Android 4.4???????????????
        final boolean after44 = Build.VERSION.SDK_INT >= 19;
        if (after44 && DocumentsContract.isDocumentUri(context, uri)) {
            // ?????????Android 4.4????????????????????????????????????URI
            final String authority = uri.getAuthority();
            // ??????Authority????????????????????????????????????
            if ("com.android.externalstorage.documents".equals(authority)) {
                // ??????????????????
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] divide = docId.split(":");
                final String type = divide[0];
                if ("primary".equals(type)) {
                    String path = Environment.getExternalStorageDirectory().getAbsolutePath().concat("/").concat(divide[1]);
                    return path;
                } else {
                    String path = "/storage/".concat(type).concat("/").concat(divide[1]);
                    return path;
                }
            } else if ("com.android.providers.downloads.documents".equals(authority)) {
                // ????????????
                final String docId = DocumentsContract.getDocumentId(uri);
                if (docId.startsWith("raw:")) {
                    final String path = docId.replaceFirst("raw:", "");
                    return path;
                }
                final Uri downloadUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.parseLong(docId));
                String path = queryAbsolutePath(context, downloadUri);
                return path;
            } else if ("com.android.providers.media.documents".equals(authority)) {
                // ?????????????????????
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] divide = docId.split(":");
                final String type = divide[0];
                Uri mediaUri = null;
                if ("image".equals(type)) {
                    mediaUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    mediaUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    mediaUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                } else {
                    return null;
                }
                mediaUri = ContentUris.withAppendedId(mediaUri, Long.parseLong(divide[1]));
                String path = queryAbsolutePath(context, mediaUri);
                return path;
            }
        } else {
            // ??????????????????URI
            final String scheme = uri.getScheme();
            String path = null;
            if ("content".equals(scheme)) {
                // ??????URI
                path = queryAbsolutePath(context, uri);
            } else if ("file".equals(scheme)) {
                // ??????URI
                path = uri.getPath();
            }
            return path;
        }
        return null;
    }

    public static String queryAbsolutePath(final Context context, final Uri uri) {
        final String[] projection = {MediaStore.MediaColumns.DATA};
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                return cursor.getString(index);
            }
        } catch (final Exception ex) {
            ex.printStackTrace();
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }
//=============================????????????=================================================

    private void runOnUiThread(Runnable runnable) {
        if (isAdded() && getActivity() != null)
            getActivity().runOnUiThread(runnable);
    }

    //stubborn = true ???????????? PRINT_FOTA_PROCCESS_PRINT_TO_TXTVIEW ??????
    private void addText(TextView textView, String content, Boolean stubborn) {

        String colorText = content;
//        if(content.indexOf("success")>-1){
//            colorText = TextTool.getColoredSpanned(content,"#00DB00");
//        }
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

        if((FotaMsg.PRINT_FOTA_PROCCESS_PRINT_TO_TXTVIEW==1) || (stubborn)) {
            textView.append(Html.fromHtml(colorText));
            textView.append("\n"); //??????
            int offset = textView.getLineCount() * textView.getLineHeight();
            if (offset > textView.getHeight()) {
                //????????????????????????
                textView.scrollTo(0, offset - textView.getHeight());
            }
        }
    }

    //???????????????????????????????????? ?????????????????? ?????????????????????
    private void addText_do_no_change_line(TextView textView, String content, Boolean stubborn) {
        if((FotaMsg.PRINT_FOTA_PROCCESS_PRINT_TO_TXTVIEW==1)||(stubborn)) {
            textView.append(content);
            textView.append(" ");
            int offset = textView.getLineCount() * textView.getLineHeight();
            if (offset > textView.getHeight()) {
                //????????????????????????
                textView.scrollTo(0, offset - textView.getHeight());
            }
        }
    }


    private void ClearText(final TextView textView) {
        textView.setText("");
    }

    //Change progress bar
    private void changeProgressBar(ProgressBar pbar, int val) {
        pbar.setProgress(val);
    }

    //save log
    private void SaveText(final TextView textView) {
        Calendar mCal = Calendar.getInstance();
        CharSequence gettoday = DateFormat.format("yyyy_MM_dd_kk", mCal.getTime());
        CharSequence gettoday2 = DateFormat.format("mm", mCal.getTime());
        int time_jarge = Integer.valueOf((String)gettoday2);

        File dir = getActivity().getFilesDir();
        String strsavelog;
        if(time_jarge>=30) {
            strsavelog = "_savelog2.txt";
        }
        else{
            strsavelog = "_savelog1.txt";
        }


        if(FotaMsg.LOG_FILE_TYPE==FotaMsg.LOG_FILE_APPLY){
            File outFile_delete = new File(dir, ((String) gettoday) + strsavelog);
            //??????????????????????????????????????????
            if (outFile_delete.exists()) {
                outFile_delete.delete();
            }

            //?????????????????????????????????????????? "test.txt" ?????????
            File outFile = new File(dir, (String) gettoday + strsavelog);

            //?????????????????????????????? package name ??? com.myapp
            //???????????? /data/data/com.myapp/files/test.txt ??????
            writeToFileReplace(outFile, textView.getText().toString());
        }
        else{
            //?????????????????????????????????????????? "test.txt" ?????????
            File outFile = new File(dir, (String) gettoday + strsavelog);

            //?????????????????????????????? package name ??? com.myapp
            //???????????? /data/data/com.myapp/files/test.txt ??????
            writeToFileAppend(outFile, textView.getText().toString());

            //??????????????? textView ?????? (release ??????Log???????????????????????????)
            if(!app.getReleaseToCTMR()){
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
    //@RequiresApi(api=Build.VERSION_CODES.N)
    private void SaveText2(final TextView textView)  {
        Calendar mCal = Calendar.getInstance();
        //CharSequence s = DateFormat.format("yyyy-MM-dd kk:mm:ss", mCal.getTime());    // kk:24?????????, hh:12?????????
        CharSequence gettoday = DateFormat.format("yyyy_MM_dd_kk", mCal.getTime());    // kk:24?????????, hh:12?????????
        CharSequence gettoday2 = DateFormat.format("mm", mCal.getTime());
        int time_jarge = Integer.valueOf((String)gettoday2);

        //String savelogpath = Environment.getExternalStorageDirectory().getPath();
        String savelogpath = Environment.getExternalStorageDirectory().getAbsolutePath();
        savelogpath += "/Download/fota_savelog/";

        File dir = new File(savelogpath);
        //check file path
        if (!dir.exists()){
            //?????????????????????
            dir.mkdir();
        }

        String strsavelog;
        if(time_jarge>=30) {
            strsavelog = "_savelog2.txt";
        }
        else{
            strsavelog = "_savelog1.txt";
        }


        if(FotaMsg.LOG_FILE_TYPE==FotaMsg.LOG_FILE_APPLY){
            File outFile_delete = new File(savelogpath , ((String) gettoday) + strsavelog);
            //??????????????????????????????????????????
            if (outFile_delete.exists()) {
                outFile_delete.delete();
            }
            File outFile = new File(savelogpath , ((String) gettoday) + strsavelog);
            //?????????????????????????????? package name ??? com.myapp
            //???????????? /data/data/com.myapp/files/test.txt ??????
            writeToFileReplace(outFile, textView.getText().toString());
        }
        else{
            File outFile = new File(savelogpath , ((String) gettoday) + strsavelog);
            //?????????????????????????????? package name ??? com.myapp
            //???????????? /data/data/com.myapp/files/test.txt ??????
            writeToFileAppend(outFile, textView.getText().toString());
        }
    }

    //writeToFile ????????????  ??????????????????
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

    //writeToFile ???????????? ?????????????????????  (append:??????)
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

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private Button btn_san_ble;
    private Button btn_ota_run;
    private Button btn_sel_bin;
    //SCAN
    private ArrayList<BleDevice> _scanDeviceArray = new ArrayList<BleDevice>();
    private ArrayList<String> _scanDeviceStringArray = new ArrayList<String>();
    private MaterialDialog _alertDialog = null;
    //?????????????????????
    private BleDevice _connectBleDevice = null;
    private TextView fotatxtlog = null;
    private TextView bleDeviceText = null;
    private TextView bleStatusText = null;
    private Spinner spinner_phy = null;
    private boolean _isSpinnerOnCreate = true;  //????????????????????????
    private boolean _isAutoReConnect = true;  //?????????????????? ???????????????

    //??????????????????
    private Button.OnClickListener onClickScanBleButton = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            Log.i("AppFotaFragment","onClickScanBleButton");

            //???????????????????????????
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
            _alertDialog.title(null,"BLE Device Scan...");
            DialogListExtKt.listItems(_alertDialog, null, _scanDeviceStringArray, null, true, (materialDialog, index, text) -> {

                Log.i("DialogListExtKt","listItems:"+index);

                if(index >= _scanDeviceArray.size()){
                    return null;
                }

                connectBle(_scanDeviceArray.get(index));//????????????
                bleDeviceText.setText("BLE Device : "+_scanDeviceArray.get(index).getName());
                _isAutoReConnect = true;
                BleManager.getInstance().cancelScan();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        _alertDialog.dismiss();
                    }
                });
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
                    Log.d("AppFotaFragment", "result:"+bleDevice.getName()  +"    MAC:"+bleDevice.getMac()+"    Rssi:"+bleDevice.getRssi());

                    if(bleDevice.getName()== null){
                        return;
                    }
                    String displayName = bleDevice.getName()+"\n" +bleDevice.getMac();

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


    //ble??????
    private void connectBle(BleDevice bleDevice) {
        BleManager.getInstance().connect(bleDevice, bgk);
    }


    private BleGattCallback bgk = new BleGattCallback() {
        @Override
        public void onStartConnect() {
            Log.d("AppFotaFragment", "connectBle: onStartConnect");
            bleStatusText.setText("BLE Status : Start Connect");
            bleStatusText.setTextColor(Color.BLUE);

            btn_ota_run.setEnabled(false);
            btn_sel_bin.setEnabled(false);
        }

        @Override
        public void onConnectFail(BleDevice bleDevice, BleException exception) {
            Log.d("AppFotaFragment", "connectBle: onConnectFail");
            addText(fotatxtlog, "Connect Fail", true);
            bleStatusText.setText("BLE Status : Connect Fail");
            bleStatusText.setTextColor(Color.RED);

            btn_ota_run.setEnabled(false);
            btn_sel_bin.setEnabled(false);
            btn_san_ble.setText("SCAN BLE");

            if(_isAutoReConnect == true){
                BleManager.getInstance().connect(bleDevice, bgk);
            }
        }

        @Override
        public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
            Log.d("AppFotaFragment", "connectBle: onConnectSuccess");
            _connectBleDevice = bleDevice;

            addText(fotatxtlog, "Connect Success", true);
            bleStatusText.setText("BLE Status : Connect Success");
            bleStatusText.setTextColor(Color.GREEN);
            btn_san_ble.setText("DisConnected");

            btn_sel_bin.setEnabled(true);
            btn_ota_run.setEnabled(false);

            //change MTU size
            app.setReConnectMtu(255);
            BleManager.getInstance().setMtu(_connectBleDevice, 255, new BleMtuChangedCallback() {
                @Override
                public void onSetMTUFailure(final BleException exception) {
                    // Change MTU Failure
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            addText(fotatxtlog, "setMTUFailure" + exception.toString(), false);
                        }
                    });
                    FOTA_States = FotaMsg.FOTA_STATE_MTU_CHANGE_FAILURE;
                }

                @Override
                public void onMtuChanged(final int mtu) {
                    // Change MTU success???and get the MTU value supported by the current BLE device transmission.
                    app.setReConnectMtu(mtu);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            addText(fotatxtlog, "MtuChanged = " + mtu, false);
                            if (mtu < fota_user_sel_mtu_data_length) {
                                Toast.makeText(getActivity(), getString(R.string.error_no_support_this_mtu), Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                    FOTA_States = FotaMsg.FOTA_STATE_MTU_CHANGE_SCCESS;
                }
            });
        }

        @Override
        public void onDisConnected(boolean isActiveDisConnected, BleDevice device, BluetoothGatt gatt, int status) {
            Log.d("AppFotaFragment", "connectBle: onDisConnected");
            addText(fotatxtlog, "Dis Connected", true);
            bleStatusText.setText("BLE Status : Dis Connected");
            bleStatusText.setTextColor(Color.RED);

            btn_ota_run.setEnabled(false);
            btn_sel_bin.setEnabled(false);
            btn_san_ble.setEnabled(true);
            btn_san_ble.setText("SCAN BLE");

            if(_isAutoReConnect == true){
                BleManager.getInstance().connect(device, bgk);
            }
        }
    };

    /**
     * set phy Selected
     */
    private AdapterView.OnItemSelectedListener phy_slectListener = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if(FOTA_States==FotaMsg.FOTA_STATE_SENT_DATA)
            {
                Toast.makeText(getActivity(), getString(R.string.error_fota_already_started_no_chang_mtu), Toast.LENGTH_LONG).show();
            }

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

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {  //????????????????????? v26
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
}
