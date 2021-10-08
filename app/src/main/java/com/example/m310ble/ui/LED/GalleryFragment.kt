package com.example.m310ble.ui.LED

import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.afollestad.materialdialogs.list.updateListItems
import com.example.m310ble.Bluetooth.BluetoothLeData
import com.example.m310ble.Bluetooth.BluetoothLeDataManager
import com.example.m310ble.MainActivity
import com.example.m310ble.R
import com.example.m310ble.UtilTool.Log
import com.example.m310ble.UtilTool.ResourceManager
import java.util.*
import kotlin.concurrent.schedule

class GalleryFragment : Fragment() {
    private val TAG = javaClass.name

    //BLE
    private var _Write_BC: BluetoothLeData.CharacteristicData? = null
    private var _BleData: BluetoothLeData? = null
    private val _bdm = BluetoothLeDataManager.getInstance()
    //SCAN
    private var _scanResultArray: ArrayList<ScanResult> = ArrayList<ScanResult>()
    private var _scanResultDeviceArray: ArrayList<String> = ArrayList<String>()
    private lateinit var _alertDialog: MaterialDialog

    //UI
    private var _bleDevice: TextView? = null
    private var _bleStatus: TextView? = null

    private var _onButton: Button? = null
    private var _offButton: Button? = null
    private var _bleButton: Button? = null

    private var _activity: MainActivity? = null

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {

        val root = inflater.inflate(R.layout.fragment_gallery, container, false)
//        val textView: TextView = root.findViewById(R.id.text_gallery)

        _onButton = root.findViewById(R.id.on_button)
        _onButton!!.setOnClickListener(onClickonButton)
        _offButton = root.findViewById(R.id.off_button)
        _offButton!!.setOnClickListener(onClickoffButton)
        _bleButton = root.findViewById(R.id.btn_san_ble)
        _bleButton!!.setOnClickListener(onClickBleButton)
        _bleDevice = root.findViewById(R.id.BLE_DEVICE_TEXT)
        _bleStatus = root.findViewById(R.id.BLE_STATUS_TEXT)

        _activity = activity as MainActivity?

        return root
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume()")

        if (_activity!!.TempBleData_LED != null) {

            _BleData = _activity!!.TempBleData_LED

            _BleData!!.setOnStateChangeListener(onStateChangeListener)

            _bleDevice!!.text = "BLE Device: " + _BleData!!.deviceName
            _Write_BC = _activity!!.TempWriteBC_LED

            if (_BleData!!.isConnect) {
                requireActivity().runOnUiThread {
                    _bleStatus!!.setText("BLE Status: CONNECTED")
                    _bleStatus!!.setTextColor(Color.GREEN)

                    _bleButton!!.isEnabled = true
                    _bleButton!!.setText("Disconnect")
                    _bleButton!!.setTextColor(Color.RED)
                }
            } else {
                requireActivity().runOnUiThread {
                    _bleStatus!!.setText("BLE Status: DISCONNECTED")
                    _bleStatus!!.setTextColor(Color.RED)

                    _bleButton!!.isEnabled = true
                    _bleButton!!.setText("SCAN BLE")
                    _bleButton!!.setTextColor(Color.WHITE)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "onStop()")
        _activity!!.TempBleData_LED = _BleData
        _activity!!.TempWriteBC_LED = _Write_BC

    }

    private val onClickBleButton = View.OnClickListener {
        if (_BleData?.isConnect == true) {
            _BleData!!.setDisConnect()
        } else {
            this.ScanBleDevice()
        }
    }

    private val onClickonButton = View.OnClickListener {
        if (_Write_BC == null) {
            return@OnClickListener
        }

        val Code = byteArrayOf(0x31)
        _Write_BC!!.write(Code,BleWriteCallBack)
    }
    private val onClickoffButton = View.OnClickListener {

        if (_Write_BC == null ) {
            return@OnClickListener
        }
        val Code = byteArrayOf(0x30)
        _Write_BC!!.write(Code,BleWriteCallBack)
    }

    private val BleWriteCallBack = BluetoothLeData.writeCallBack { status ->

    }


    /**
     *  打開ＢＬＥ搜尋
     */
    private fun ScanBleDevice() {

        if(_bdm.isBluetoothEnabled(ResourceManager.context) == false){
            Toast.makeText(ResourceManager.context, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            return
        }
        if(_bdm.isGPSEnabled(ResourceManager.context) == false){
            Toast.makeText(ResourceManager.context, R.string.GPS_not_supported, Toast.LENGTH_SHORT).show();
            return
        }

        _scanResultDeviceArray.clear()
        _scanResultArray.clear()

        _alertDialog = MaterialDialog(ResourceManager.context)
            .cancelOnTouchOutside(false)
            .cancelable(false)
            .title(text = "BLE Device Scan...")
            .listItems(items = _scanResultDeviceArray) { dialog, index, text ->
                    //TODO:點擊ＢＬＥ裝置事件

                    if(index >= _scanResultArray.size) return@listItems

                    _bleDevice!!.setText("BLE Device: " + _scanResultArray.get(index).scanRecord!!.deviceName)
                    Log.d(TAG, "onOptionsItemSelected:" + _scanResultArray.get(index).scanRecord!!.deviceName + " " + _scanResultArray.get(index).device.uuids)
                    _BleData = _bdm.getBluetoothLeData(ResourceManager.context, _scanResultArray.get(index).device.address)
                    _activity!!.TempBleData_LED = _BleData // 提前存 不然返回會是空值 無法斷線

                    _bdm.scanLeDevice(false, ResourceManager.context, scanCallback) //停止搜尋
                    this.connectBle(bleData = this._BleData!!)//藍芽連線

                    _alertDialog.dismiss()
                }
                .negativeButton(null, "cancel") { materialDialog: MaterialDialog? ->
                    Log.d(TAG, "ScanBleDevice Cancel")
                    _bdm.scanLeDevice(false, ResourceManager.context, scanCallback) //停止搜尋
                    _alertDialog.dismiss()
                    null
                }
        _alertDialog.show()

        _bdm.scanLeDevice(true, ResourceManager.context, scanCallback)

//        Timer().schedule(10000){
//            if (getActivity() == null || !isAdded()) return@schedule
//            requireActivity().runOnUiThread {
//                    _scanResultDeviceArray.add("** End of Search, If the device is not found, please try again. **")
//                    _alertDialog.updateListItems(items = _scanResultDeviceArray)
//                    _bdm.scanLeDevice(false, ResourceManager.context, scanCallback) //停止搜尋
//            }
//        }
    }

    /**
     * ＢＬＥ搜尋結果
     */
    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            Log.d(TAG, "onScanResult  callbackType:$callbackType   result:$result")

            var displayName = result.scanRecord!!.deviceName +"\n"+result.device.address

            if(result.scanRecord == null) return

            if (!_scanResultArray.contains(result) && !_scanResultDeviceArray.contains(displayName)) {
                if (result.scanRecord!!.deviceName != null) {
                    _scanResultArray.add(result)
                    Log.d(TAG, "onScanResult  deviceName:" + result.scanRecord!!.deviceName)
                    _scanResultDeviceArray.add(displayName)
                    _alertDialog.updateListItems(items = _scanResultDeviceArray)
                }
            }

        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
            Log.d(TAG, "results:$results")
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.d(TAG, "errorCode:$errorCode")
        }
    }


    /**
     * ＢＬＥ藍芽連線
     */
    private fun connectBle(bleData: BluetoothLeData) {

        if (bleData == null) {
            return
        }

        requireActivity().runOnUiThread {
            _bleStatus!!.setText("BLE Status: CONNECTING")
            _bleStatus!!.setTextColor(Color.BLUE)
        }

        bleData!!.setOnStateChangeListener(onStateChangeListener)

        bleData!!.connectLeDevice {
            Log.i("MainActivity", "connectLeDevice:" + it)
            if (it != true) {
                onStateChangeListener.onStateChange(bleData.getbleMacAddress(), 0, 0)
                return@connectLeDevice
            }
            for (bs in bleData!!.servicesDataArray) {
                for (bc in bs.characteristicDataArray) {
                    Log.i("MainActivity", "characteristic:" + bc.uuid)

                    //專門用來寫入之特徵
                    if (bc.uuid.indexOf("50515253-5455-5657-5859-5a5b5c5d5e5f") > -1) {
                        _Write_BC = bc
                    }

                    //專門用來監聽之特徵
                    if (bc.uuid.indexOf("0000fa02-0000-1000-8000-00805f9b34fb") > -1) {
                        bc.setNotify(true, myNotifyListener)
                        Log.i("MainActivity", "setNotify:" + bc.uuid)
                    }
                }
            }
        }
    }

    /**
     * 監聽ＢＬＥ連線變化
     */
    private var onStateChangeListener = BluetoothLeData.onStateChange { MAC, status, newState ->

        Log.i("onStateChangeListener", "MAC:" + MAC + "  status:" + status + "  newState:" + newState)

        if (getActivity() == null || !isAdded()) return@onStateChange

        requireActivity().runOnUiThread {
            if (newState == BluetoothProfile.STATE_CONNECTING) {
                requireActivity().runOnUiThread {
                    _bleStatus!!.setText("BLE Status: CONNECTING")
                    _bleStatus!!.setTextColor(Color.BLUE)
                }
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                requireActivity().runOnUiThread {
                    _bleStatus!!.setText("BLE Status: CONNECTED")
                    _bleStatus!!.setTextColor(Color.GREEN)

                    _bleButton!!.isEnabled = true
                    _bleButton!!.setText("Disconnect")
                    _bleButton!!.setTextColor(Color.RED)
                }
            }
        }

        if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            this._BleData = null
            requireActivity().runOnUiThread {
                _bleStatus!!.setText("BLE Status: DISCONNECTED")
                _bleStatus!!.setTextColor(Color.RED)

                _bleButton!!.isEnabled = true
                _bleButton!!.setText("SCAN BLE")
                _bleButton!!.setTextColor(Color.WHITE)
            }
        }
    }

    /***
     * 收通知的地方
     */
    private val myNotifyListener = BluetoothLeData.notifCallBack { bleMAC, UUID, Value ->

    }
}