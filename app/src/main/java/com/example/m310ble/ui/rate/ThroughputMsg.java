package com.example.m310ble.ui.rate;

public class ThroughputMsg {
    public static final String THROUGH_PROCCESS_TAG = "StressDemoLog";

    public static final String THROUGHPUT_SERVICEUUID = "00112233-4455-6677-8899-aabbccddeeff";
    public static final String THROUGHPUT_CHARACTERISTICUUID_WRITE = "50515253-5455-5657-5859-5a5b5c5d5e5f"; //Write and writer no response
    public static final String THROUGHPUT_CHARACTERISTICUUID_NOTIFY = "0000fa02-0000-1000-8000-00805f9b34fb"; //Notify and Indicate
     public static final String THROUGHPUT_CHARACTERISTICUUID_128BIT_NOTIFY = "30313233-3435-3637-3839-3a3b3c3d3e3f"; //Notify and Indicate
    public static String THROUGHPUT_NOTIFY = "30313233-3435-3637-3839-3a3b3c3d3e3f"; //Notify and Indicate

    //aR-dT aT-dR write string
    public static final String THROUGHPUT_TEST_APPTX_DEVICERX_START_STRING = "pRxtest";
    public static final String THROUGHPUT_TEST_APPRX_DEVICETX_START_STRING = "pTxtest";

    public static final Boolean THROUGH_SHOW_LOG_TO_PANEL = false;
    public static final Boolean THROUGHPUT_TEST_APPRX_DEVICETX_DISCONNECTED = false; //aR-dT測試完是否主動斷線?

    public static final int THROUGHPUT_TEST_APPTX_DEVICERX  = 1;
    public static final int THROUGHPUT_TEST_APPRX_DEVICETX  = 2;
    public static final int THROUGHPUT_TEST_TWO_WAY  = 3;

    public static final int THROUGHPUT_DISCONNECTED_WAIT_TIME_IN_WHILE_LOOP = 3000; //如果再While 回圈內等待，發生斷線時的處裡


    public static final int THROUGHPUT_NEXT_PROCCESS_TIME_MS = 20000; //不間斷測試

    public static final long THROUGHPUT_LOG_SAVE_TIME = 6; //多少秒存檔Log一次

    public static final int THROUGHPUT_STATE_IDAL = 0;
    public static final int THROUGHPUT_STATE_MTU_CHANGE_SCCESS  = 1;
    public static final int THROUGHPUT_STATE_MTU_CHANGE_FAILURE  = 2;
    public static final int THROUGHPUT_STATE_NOTIFY_OPEN_SCCESS  = 3;
    public static final int THROUGHPUT_STATE_NOTIFY_OPEN_FAILURE  = 4;
    public static final int THROUGHPUT_STATE_SENT_STRRXDISABLE_STRING  = 5;
    public static final int THROUGHPUT_STATE_SENT_STRRXDISABLE_STRING_SCCESS  = 6;
    public static final int THROUGHPUT_STATE_SENT_STRRXDISABLE_STRING_FAILURE  = 7;
    public static final int THROUGHPUT_STATE_SENT_STRPTXTEST_STRING_SCCESS  = 6;
    public static final int THROUGHPUT_STATE_SENT_STRPTXTEST_STRING_FAILURE  = 7;
    public static final int THROUGHPUT_STATE_SENT_STRSTRESSTEST_STRING_SCCESS  = 6;
    public static final int THROUGHPUT_STATE_SENT_STRSTRESSTEST_STRING_FAILURE  = 7;
    public static final int THROUGHPUT_STATE_SENT_DATA  = 8;
    public static final int THROUGHPUT_STATE_RECEIVER_DATA_DONE  = 9;
}
