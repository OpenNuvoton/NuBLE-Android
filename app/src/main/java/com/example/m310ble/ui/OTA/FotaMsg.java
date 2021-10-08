package com.example.m310ble.ui.OTA;

public class FotaMsg {

    public static final String FOTA_PROCCESS_TAG = "FotaDemoLog";

    //log file processing (Log 檔案儲存方式)
    public static final int PRINT_FOTA_PROCCESS_PRINT_TO_TXTVIEW=1; //0:只show時間等少部分資訊，1:show 整個流程在 txt view 上，Release 給客戶時，此FLAG應該為0。
    public static final int LOG_FILE_APPLY=0; //用取代原本檔案的方式
    public static final int LOG_FILE_APPEND=1;//用附加的方式，將LOG加入到檔案內容最後，並清除app上的txt內容，否則會拖到處理速度
    public static final int LOG_FILE_TYPE=LOG_FILE_APPEND;
    public static final int FOTA_SAVE_TIME = 10;

    // FOTA
    //UUID
    public static final String FOTASERVICEUUID = "09102132-4354-6576-8798-a9bacbdcedfe";
    public static final String FOTACHARACTERISTICUUID_WRITENORESPONSE_NOTIFY = "01112131-4151-6171-8191-a1b1c1d1e1f1";
    public static final String FOTACHARACTERISTICUUID_WRITE_INDICATE = "02122232-4252-6272-8292-a2b2c2d2e2f2";

    public static final boolean FOTA_CONTINUOUS_MODE = true; //不間斷測試
    public static final boolean FOTA_VERIFY_FW_VERSION = false; //驗證版本，若global_RELEASE_TO_CTMR = true, 此參數無效，都會驗證版本。
    public static final boolean FOTA_RESCUE_UNFINISHED_DATA = true; //續傳，補救未完成資料
    public static final boolean FOTA_WAIT_FAILURE_DATA_TO_BE_TRANSMITTED = false; //傳輸Fail是否要等待其餘資料全部傳完後再重新連線?

    public static final int FOTA_WAIT_NEXT_FOTA_PROCCESS_TIME_MS = 10000; //不間斷測試等待時間
    public static final int FOTA_DISCONNECTED_WAIT_TIME_IN_WHILE_LOOP = 3000; //如果再While 回圈內等待，發生斷線時的處裡
    public static final int FOTA_NO_NOTIFY_RECEIVER_TIIME_OUT_UNIT_SEC = 5; //當等待Notify超過此時間，程式就不等，當作沒有收到，單位sce

    public static final boolean FOTA_NOTIFY_INTERVAL_DEFINED_BY_UESR=false; //是否給使用者定義Notify Interval長度 //kawa 智強說要關閉 20210526
    public static final int FOTA_NOTIFY_INTERVAL_MAX_VALUE=100000; //如果是自動比對，這裡為Max預設值，以不超過此值為條件下，設最大值。 預設值2000

    //.bin file start不是從0開始時，Data prepare需要shift address使得address從0開始傳，收到Notify資料時也需要shift start address才能對應到自己正確的資料。
    public static final boolean FOTA_AUTO_SHIFT_START_ADDRESS_FROM_ZREO=true;

    public static final int FOTA_START = 0X00;

    public static final byte FOTA_CMD_QUERY = 0x00;
    public static final byte FOTA_CMD_START = 0x01;
    public static final byte FOTA_CMD_ERASE = 0x02;
    public static final byte FOTA_CMD_APPLY_UPDATE = 0x03;

    public static final byte FOTA_CMD_QUERY_LENGTH = 1;         //CMD(1byte)
    public static final byte FOTA_CMD_START_LENGTH = 13;        //CMD(1byte) + data total length(4Byte) + CRC32(4Byte) + notify interval(4Byte)
    public static final byte FOTA_CMD_ERASE_LENGTH = 1;         //CMD(1byte)
    public static final byte FOTA_CMD_APPLY_UPDATE_LENGTH = 5;  //CMD(1byte) + Start address (4Byte)

    public static final int FOTA_STATE_IDAL = 0;
    public static final int FOTA_STATE_MTU_CHANGE_SCCESS  = 1;
    public static final int FOTA_STATE_MTU_CHANGE_FAILURE  = 2;
    public static final int FOTA_STATE_INDICATION_OPEN_SCCESS  = 3;
    public static final int FOTA_STATE_INDICATION_OPEN_FAILURE  = 4;
    public static final int FOTA_STATE_NOTIFY_OPEN_SCCESS  = 5;
    public static final int FOTA_STATE_NOTIFY_OPEN_FAILURE  = 6;
    public static final int FOTA_STATE_CMD_QUERY_INFORMATION= 7;
    public static final int FOTA_STATE_INDICATION_RECEIVER_DEVICE_INFO_QUERY_PASS  = 8;
    public static final int FOTA_STATE_INDICATION_RECEIVER_DEVICE_INFO_QUERY_NOPASS  = 9;
    public static final int FOTA_STATE_INDICATION_NO_RECEIVER_DEVICE_INFO_QUERY  = 10;
    public static final int FOTA_STATE_CMD_START= 11;
    public static final int FOTA_STATE_INDICATION_RECEIVER_DEVICE_INFO_START  = 12;
    public static final int FOTA_STATE_INDICATION_NO_RECEIVER_DEVICE_INFO_START  = 13;
    public static final int FOTA_STATE_SENT_DATA = 14;
    public static final int FOTA_STATE_CMD_APPLY_UPDATE  = 15;
    public static final int FOTA_STATE_INDICATION_RECEIVER_DEVICE_INFO_APPLY = 16;
    public static final int FOTA_STATE_INDICATION_NO_RECEIVER_DEVICE_INFO_APPLY  = 17;

}
