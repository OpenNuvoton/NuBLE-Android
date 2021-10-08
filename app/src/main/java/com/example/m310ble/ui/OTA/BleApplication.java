package com.example.m310ble.ui.OTA;

import android.app.Application;


/*
Release note.
提供給客戶時，請確保以下參數修改完畢
1.[BleApplication.java]    global_RELEASE_TO_CTMR = true;
2.[FotaMsg.java]           public static final int PRINT_FOTA_PROCCESS_PRINT_TO_TXTVIEW=0;
*/

public class BleApplication extends Application {

    private boolean global_FIXED_RELEASE_TO_CTMR_MODE = true; //不提供使用者在menu上修改release/debug mode, 給客戶時包含global_RELEASE_TO_CTMR 參數都設為true
    private boolean global_RELEASE_TO_CTMR = true; //初始為release mode or debug mode，此參數可以在menu上被修改

    private boolean global_need_reconnect; //提供是否需要回連flag，若設為0，則斷線後不回連
    private boolean global_reconnect_start_flag; //若斷線回連"開始"，此Flag會舉1，但並不代表回連成功，此功能需要 global_need_reconnect = 1
    private boolean global_reconnect_success_flag; //若斷線回連"成功"，此Flag會舉1，此功能需要 global_need_reconnect = 1
    private int global_reconnect_number;

    private boolean global_back_main_page_when_disconnect; //當斷線時，是否回到首頁 (note.回到首頁會將測試資料全部不見，需靠定時save log解決)

    //防止中斷連線後，Device 設定沒有設到，在重連的函數中直接取用以下參數
    private int global_device_tx_phy;
    private int global_device_rx_phy;
    private int global_device_mtu;
    //(30ms–50ms)    0: CONNECTION_PRIORITY_BALANCED
    //(11.25ms–15ms) 1: CONNECTION_PRIORITY_HIGH
    //(100ms–125ms)  2: CONNECTION_PRIORITY_LOW_POWER
    private int global_device_connection_interval;
    private int global_ate_test_item; //0:Stress test, 1:Write Len 240 only, 2:Write Data len 1 to 244
    private boolean global_ate_now_test_fota_no_reconnect_flag;//fota 遇到斷線，true為等待FOTA通知可以重連，false為可立刻重連，預設為false。

    @Override
    public void onCreate()
    {
        super.onCreate();
        initVal(); // 初始化全域性變數
    }

    public void initVal()
    {
        global_need_reconnect = true; //需要回連

        global_reconnect_start_flag = false;
        global_reconnect_success_flag = false;
        global_reconnect_number = 0;

        global_back_main_page_when_disconnect = false; //不回首頁

        global_device_tx_phy = 1;
        global_device_rx_phy = 1;
        global_device_mtu = 23;
        global_device_connection_interval = 0; //(30ms–50ms)    0: CONNECTION_PRIORITY_BALANCED

        global_ate_test_item = 0;
        global_ate_now_test_fota_no_reconnect_flag=false; //fota 遇到斷線，true為等待FOTA通知可以重連，false為可立刻重連，預設為false。
    }

    public void setNeedReConnect(boolean boolflag)
    {
        this.global_need_reconnect = boolflag;
    }
    public boolean getNeedReConnect()
    {
        return global_need_reconnect;
    }

    public void setReConnectSuccessFlag(boolean boolflag)
    {
        this.global_reconnect_success_flag = boolflag;
    }
    public boolean getReConnectSuccessFlag()
    {
        return global_reconnect_success_flag;
    }

    public void setReConnectStartFlag(boolean boolflag)
    {
        this.global_reconnect_start_flag = boolflag;
    }
    public boolean getReConnectStartFlag()
    {
        return global_reconnect_start_flag;
    }

    //FOTA測項用的到，不希望斷線後立刻重連
    public void setReConnectWait(boolean boolflag)
    {
        this.global_ate_now_test_fota_no_reconnect_flag = boolflag;
    }
    public boolean getReConnectWait()
    {
        return global_ate_now_test_fota_no_reconnect_flag;
    }


    public void setReConnectNum(int value)
    {
        this.global_reconnect_number = value;
    }
    public int getReConnectNum()
    {
        return global_reconnect_number;
    }

    public void setBackMainPageWhenDisconnect(boolean boolflag)
    {
        this.global_back_main_page_when_disconnect = boolflag;
    }
    public boolean getBackMainPageWhenDisconnect()
    {
        return global_back_main_page_when_disconnect;
    }


    public void setReConnectTxPhy(int phy)
    {
        this.global_device_tx_phy = phy;
    }
    public void setReConnectRxPhy(int phy)
    {
        this.global_device_rx_phy = phy;
    }
    public void setReConnectMtu(int mtu)
    {
        this.global_device_mtu = mtu;
    }
    public void setConnectInterval(int conn)
    {
        this.global_device_connection_interval = conn;
    }

    public int getReConnectTxPhy()
    {
        return global_device_tx_phy;
    }

    public int getReConnectRxPhy()
    {
        return global_device_rx_phy;
    }

    public int getReConnectMtu()
    {
        return global_device_mtu;
    }

    public int getConnectInterval()
    {
        return global_device_connection_interval;
    }

    public boolean getReleaseToCTMR()
    {
        return global_RELEASE_TO_CTMR;
    }

    public void setReleaseToCTMR(boolean boolflag)
    {
        this.global_RELEASE_TO_CTMR = boolflag;
    }

    public boolean getfixedReleaseToCTMR()
    {
        return global_FIXED_RELEASE_TO_CTMR_MODE;
    }
    public void setAteItem(int value)
    {
        this.global_ate_test_item = value;
    }
    public int getAteItem()
    {
        return global_ate_test_item;
    }

}
