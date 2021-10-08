package com.clj.fastble.data;


import android.bluetooth.BluetoothDevice;
import android.os.Parcel;
import android.os.Parcelable;


public class BleDevice implements Parcelable {

    private BluetoothDevice mDevice;
    private byte[] mScanRecord;
    private int mRssi;
    private long mTimestampNanos;

    private int getServiceUUIDTime; //UUID SERVICE DISCOVER 取得時間

    public BleDevice(BluetoothDevice device) {
        mDevice = device;
    }

    public BleDevice(BluetoothDevice device, int rssi, byte[] scanRecord, long timestampNanos) {
        mDevice = device;
        mScanRecord = scanRecord;
        mRssi = rssi;
        mTimestampNanos = timestampNanos;
    }

    protected BleDevice(Parcel in) {
        mDevice = in.readParcelable(BluetoothDevice.class.getClassLoader());
        mScanRecord = in.createByteArray();
        mRssi = in.readInt();
        mTimestampNanos = in.readLong();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mDevice, flags);
        dest.writeByteArray(mScanRecord);
        dest.writeInt(mRssi);
        dest.writeLong(mTimestampNanos);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<BleDevice> CREATOR = new Creator<BleDevice>() {
        @Override
        public BleDevice createFromParcel(Parcel in) {
            return new BleDevice(in);
        }

        @Override
        public BleDevice[] newArray(int size) {
            return new BleDevice[size];
        }
    };

    //藍牙廣播名
    public String getName() {
        if (mDevice != null)
            return mDevice.getName();
        return null;
    }

    //藍牙Mac地址
    public String getMac() {
        if (mDevice != null)
            return mDevice.getAddress();
        return null;
    }


    public String getKey() {
        if (mDevice != null)
            return mDevice.getName() + mDevice.getAddress();
        return "";
    }

    public BluetoothDevice getDevice() {
        return mDevice;
    }

    public void setDevice(BluetoothDevice device) {
        this.mDevice = device;
    }

    //被掃描到時候攜帶的廣播數據
    public byte[] getScanRecord() {
        return mScanRecord;
    }

    public void setScanRecord(byte[] scanRecord) {
        this.mScanRecord = scanRecord;
    }

    //被掃描到時候的信號強度
    public int getRssi() {
        return mRssi;
    }

    public void setRssi(int rssi) {
        this.mRssi = rssi;
    }

    //儲存UUID DISCOVER 耗費時間
    public void setuuidtime(int getuuidtime) {
        this.getServiceUUIDTime = getuuidtime;
    }
    public int getuuidtime() {
        return getServiceUUIDTime;
    }

    public long getTimestampNanos() {
        return mTimestampNanos;
    }

    public void setTimestampNanos(long timestampNanos) {
        this.mTimestampNanos = timestampNanos;
    }

}
