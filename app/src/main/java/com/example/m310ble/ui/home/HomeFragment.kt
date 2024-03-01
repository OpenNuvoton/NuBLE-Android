package com.example.m310ble.ui.home

import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.View.SCROLLBARS_OUTSIDE_OVERLAY
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.afollestad.materialdialogs.list.updateListItems
import com.clj.fastble.BleManager
import com.example.m310ble.Bluetooth.BluetoothLeData
import com.example.m310ble.Bluetooth.BluetoothLeDataManager
import com.example.m310ble.MainActivity
import com.example.m310ble.R
import com.example.m310ble.UtilTool.HexUtil
import com.example.m310ble.UtilTool.Log
import com.example.m310ble.UtilTool.ResourceManager
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*


class HomeFragment : Fragment() {
    private val TAG = javaClass.name

    //BLE
    private var _Notify_BC: BluetoothLeData.CharacteristicData? = null
    private var _Write_BC: BluetoothLeData.CharacteristicData? = null
    private var _BleData: BluetoothLeData? = null
    private val _bdm = BluetoothLeDataManager.getInstance()

    //Count
//    private var _notifyCount = 0
//    private var _notifyCharSize = ""
//    private var _writeCount = 0
//    private var _writeCharSize = ""

    //SCAN
    private var _scanResultArray: ArrayList<ScanResult> = ArrayList<ScanResult>()
    private var _scanResultDeviceArray: ArrayList<String> = ArrayList<String>()
    private lateinit var _alertDialog: MaterialDialog

    private var _activity: MainActivity? = null

    //UI
    private var _bleDevice: TextView? = null
    private var _bleStatus: TextView? = null

    private var _sendText: EditText? = null
    private var _messageText: TextView? = null
    private var _countSendText: TextView? = null
    private var _countReceivedText: TextView? = null
    private var _sendButton: Button? = null
    private var _bleButton: Button? = null
    private var _clearButton: Button? = null
    private var _clearImageButton: ImageButton? = null
    private var _GoUpImageButton: ImageButton? = null
    private var _goDownImageButton: ImageButton? = null
    private lateinit var _checkBox: CheckBox
    private lateinit var _checkBox_ADD_CRLF: CheckBox


    private var _mseeage: String = ""

    private var _timer = Timer()
    private val _timerTask: TimerTask? = null

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        Log.i("HomeFragment", "onCreateView")
        val root = inflater.inflate(R.layout.fragment_home, container, false)

        _sendButton = root.findViewById(R.id.SEND_BUTTON)
        _sendButton!!.setOnClickListener(onClickSendButton)

        _bleButton = root.findViewById(R.id.btn_san_ble)
        _bleButton!!.setOnClickListener(onClickBleButton)

        _clearButton = root.findViewById(R.id.CLEAR_BUTTON)
        _clearButton!!.setOnClickListener(onClickClearButton)
        _clearImageButton = root.findViewById(R.id.CLEAR_ImageButton)
        _clearImageButton!!.setOnClickListener(onClickClearImageButton)

        _GoUpImageButton = root.findViewById(R.id.goUp_ImageButton)
        _GoUpImageButton!!.setOnClickListener(onClickGoUpImageButton)
        _goDownImageButton = root.findViewById(R.id.goDown_ImageButton)
        _goDownImageButton!!.setOnClickListener(onClickGoDownImageButton)

        _checkBox = root.findViewById(R.id.checkBox)
        _checkBox_ADD_CRLF = root.findViewById(R.id.checkBox_ADD_CRLF)
        _checkBox_ADD_CRLF.setOnCheckedChangeListener(onCheckedChangeCELFCheckBox)
        _checkBox.setOnCheckedChangeListener(onCheckedChangeLoopCheckBox)


        _messageText = root.findViewById(R.id.MESSAGE_TEXT)
        _messageText!!.setMovementMethod(ScrollingMovementMethod.getInstance());
        _messageText!!.scrollBarStyle = SCROLLBARS_OUTSIDE_OVERLAY

        _countSendText = root.findViewById(R.id.COUNT_SEND_TEXT)
        _countSendText!!.setMovementMethod(ScrollingMovementMethod.getInstance());
        _countReceivedText = root.findViewById(R.id.COUNT_RECEIVED_TEXT)
        _countReceivedText!!.setMovementMethod(ScrollingMovementMethod.getInstance());

        _sendText = root.findViewById(R.id.SEND_TEXT)

        _bleDevice = root.findViewById(R.id.BLE_DEVICE_TEXT)
        _bleStatus = root.findViewById(R.id.BLE_STATUS_TEXT)

        _activity = activity as MainActivity?

//        _activity!!.setbleInterfaceListener(object : MainActivity.bleInterface {
//            override fun notifyCallback(bleMAC: String, UUID: UUID, Value: ByteArray, count: Int) {
//                //TODO("收到ＢＬＥ的地方")
//                Log.i("HomeFragment BLE:", "UUID:" + UUID + "   value:" + Value.contentToString())
//
//                requireActivity().runOnUiThread {
//                    val date = Date()
//                    val formatter = SimpleDateFormat("mm:ss.SSS")
//                    val disPlayValue = String(Value, StandardCharsets.UTF_8)
////                    _messageText!!.text = " MCU -> APP : " + disPlayValue + " [" + formatter.format(date) + "][" + count + "]" + "\n" + _messageText!!.text
//                    _messageText!!.append(" MCU -> APP : " + disPlayValue + " [" + formatter.format(date) + "][" + count + "]")
//                    _mseeage = _mseeage + " MCU -> APP : " + disPlayValue + " [" + formatter.format(date) + "][" + count + "]" + "\n"
//                }
//
//            }
//        })

//        this.startTextAnimation()

        return root
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume()")

        var isAd = false
        while (isAd == false){
            Log.i(TAG, "onResume() isAdded:" + isAdded())
            if(isAdded() == true){
                isAd = true

                if (_activity!!.TempFragmentMseeage != "") {
                    _messageText!!.text = _activity!!.TempFragmentMseeage
                } //留言板紀錄  背景一直add text 返回會重繪 卻set就把背影add的洗掉了
//        if (_activity!!.TempFragmentText != "") _sendText!!.setText(_activity!!.TempFragmentText) //輸入匡紀錄
//        if (_activity!!.TempTxText != "") _notifyCharSize = _activity!!.TempTxText//輸入TX String
//        if (_activity!!.TempRxText != "") _writeCharSize = _activity!!.TempRxText //輸入RX String
//        if (_activity!!.TempTxCount != 0) _notifyCount = _activity!!.TempTxCount //輸入TX Count
//        if (_activity!!.TempRxCount != 0) _writeCount = _activity!!.TempRxCount //輸入RX Count


                if (_activity!!.TempBleData_UART != null) {

                    _BleData = _activity!!.TempBleData_UART
                    _BleData!!.setOnStateChangeListener(onStateChangeListener)
                    _bleDevice!!.text = "BLE Device: " + _BleData!!.deviceName
                    _Write_BC = _activity!!.TempWriteBC_UART
                    _Notify_BC = _activity!!.TempNotifyBC_UART

                    _Notify_BC?.setNotify(true, myNotifyListener)

                    if (_BleData!!.isConnect) {
                        requireActivity().runOnUiThread {
                            _bleStatus!!.setText("BLE Status: CONNECTED")
                            _bleStatus!!.setTextColor(Color.GREEN)

                            _bleButton!!.isEnabled = true
                            _bleButton!!.setText("Disconnect")
                            _bleButton!!.setTextColor(Color.WHITE)
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

                val handler = Handler(Looper.getMainLooper())
                handler.post(object : Runnable {
                    override fun run() {

                        if (isAdded()) { //確保已加載
                            requireActivity().runOnUiThread {
                                var ncs = _activity!!.TempTxText.toCharArray().size
                                var wcs = _activity!!.TempRxText.toCharArray().size
                                _countSendText!!.text =
                                        "  TX： " + _activity!!.TempRxCount + " data, $wcs char"
                                _countReceivedText!!.text =
                                        "RX： " + _activity!!.TempTxCount + " data, $ncs char"

                                if (_mseeage == "") return@runOnUiThread
//                _messageText!!.append(_mseeage)
                                _messageText!!.append(Html.fromHtml(_mseeage))


                                val offset: Int =
                                        _messageText!!.getLineCount() * _messageText!!.getLineHeight()
                                if (offset > _messageText!!.getHeight()) {
                                    _messageText!!.scrollTo(0, offset - _messageText!!.getHeight())
                                }

                                _mseeage = ""
                            }
                        }

                        handler.postDelayed(this, 500)
                    }
                })
            }

        }

    }

    private fun getColoredSpanned(text: String, color: String): String? {
        var message = text.replace("\n", "<br />");

        return "<font color=$color>$message</font>"
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy()")
//        _timer.purge()
//        _timer.cancel()
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "onStop()")
        _activity!!.TempFragmentMseeage = _messageText!!.text.toString()
        _activity!!.TempFragmentText = _sendText!!.text.toString()

        _activity!!.TempBleData_UART = _BleData
        _activity!!.TempWriteBC_UART = _Write_BC
//        _activity!!.TempTxText = _notifyCharSize
//        _activity!!.TempRxText = _writeCharSize
//        _activity!!.TempTxCount = _notifyCount
//        _activity!!.TempRxCount = _writeCount
        _activity!!.TempNotifyBC_UART = _Notify_BC

    }

    private val onClickClearButton = View.OnClickListener {
        _messageText!!.text = ""
//        _notifyCharSize = ""
//        _notifyCount = 0
//        _writeCharSize = ""
//        _writeCount = 0
        _activity!!.TempTxText = ""
        _activity!!.TempRxText = ""
        _activity!!.TempTxCount = 0
        _activity!!.TempRxCount = 0

        _activity!!.TempFragmentMseeage = ""
    }

    private val onClickClearImageButton = View.OnClickListener {
        _sendText!!.text.clear()
    }
    private val onClickGoUpImageButton = View.OnClickListener {
        _messageText!!.scrollTo(0, 0)

    }
    private val onClickGoDownImageButton = View.OnClickListener {
        val offset: Int = _messageText!!.getLineCount() * _messageText!!.getLineHeight()
        if (offset > _messageText!!.getHeight()) {
            _messageText!!.scrollTo(0, offset - _messageText!!.getHeight())
        }
    }
    private val onClickBleButton = View.OnClickListener {

        if(_checkBox!!.isChecked == true){
            requireActivity().runOnUiThread {
                Toast.makeText(ResourceManager.context, R.string.ble_busy, Toast.LENGTH_SHORT).show();
            }
            return@OnClickListener
        }

        if (_BleData?.isConnect == true) {
            _BleData!!.setDisConnect()
        } else {
            this.ScanBleDevice()
        }
    }

    private val onCheckedChangeCELFCheckBox = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
        if(isChecked ==  true){
            _checkBox.isEnabled = false
        }else{
            _checkBox.isEnabled = true
        }
    }
    private val onCheckedChangeLoopCheckBox = CompoundButton.OnCheckedChangeListener { buttonView, isChecked ->
        if(isChecked ==  true){
            _checkBox_ADD_CRLF.isEnabled = false
        }else{
            _checkBox_ADD_CRLF.isEnabled = true
        }
    }

    private val onClickSendButton = View.OnClickListener {
        if (_Write_BC == null || _sendText == null || _BleData == null || _BleData!!.isConnect == false) {
            return@OnClickListener
        }

        if (_sendText!!.text.toString() == "") {
            return@OnClickListener
        }

        _sendButton!!.isEnabled = false

        if (_checkBox!!.isChecked == false) {
            if(_checkBox_ADD_CRLF.isChecked == true){
                this.CRLF_Write()
            }else {
                this.NormalWrite()
            }
        } else {
            this.LoopWrite()
        }

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
                .title(text = "BLE Device Scaning...")
                .listItems(items = _scanResultDeviceArray) { dialog, index, text ->
                    //TODO:點擊ＢＬＥ裝置事件

                    if(index >= _scanResultArray.size) return@listItems //空指針返回

                    _bleDevice!!.setText("BLE Device: " + _scanResultArray.get(index).scanRecord!!.deviceName)
                    Log.d(
                            TAG,
                            "onOptionsItemSelected:" + _scanResultArray.get(index).scanRecord!!.deviceName + " " + _scanResultArray.get(
                                    index
                            ).device.uuids
                    )
                    _BleData = _bdm.getBluetoothLeData(
                            ResourceManager.context, _scanResultArray.get(
                            index
                    ).device.address
                    )
                    _activity!!.TempBleData_UART = _BleData // 提前存 不然返回會是空值 無法斷線

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
//                _scanResultDeviceArray.add("** End of Search, If the device is not found, please try again. **")
//                _alertDialog.updateListItems(items = _scanResultDeviceArray)
//                _bdm.scanLeDevice(false, ResourceManager.context, scanCallback) //停止搜尋
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

            if(result.scanRecord==null){
                return
            }

            result.scanRecord
            var displayName = result.scanRecord!!.deviceName +"\n"+result.device.address

            if (!_scanResultArray.contains(result) && !_scanResultDeviceArray.contains(displayName)) {
                if (result.scanRecord!!.deviceName != null) {

                    _scanResultArray.add(result)
                    Log.d(TAG, "onScanResult  deviceName:" + result.scanRecord!!.deviceName)
                    _scanResultDeviceArray.add(displayName)
                    _alertDialog.updateListItems(items = _scanResultDeviceArray )
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
                    //30313233-3435-3637-3839-3a3b3c3d3e3f  >> 2.x SDK
                    //0000fa02-0000-1000-8000-00805f9b34fb  >> 1.X SDK
                    if (bc.uuid.indexOf("0000fa02-0000-1000-8000-00805f9b34fb") > -1) {
                        _Notify_BC = bc
                        bc.setNotify(true, myNotifyListener)
                        Log.i("MainActivity", "setNotify:" + bc.uuid)
                    }
                    if (bc.uuid.indexOf("30313233-3435-3637-3839-3a3b3c3d3e3f") > -1) {
                        _Notify_BC = bc
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

        Log.i(
                "onStateChangeListener",
                "MAC:" + MAC + "  status:" + status + "  newState:" + newState
        )

        if (getActivity() == null || !isAdded()) return@onStateChange

            if (newState == BluetoothProfile.STATE_CONNECTING) {
                requireActivity().runOnUiThread {
                    _bleButton!!.isEnabled = false
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
                    _bleButton!!.setTextColor(Color.WHITE)
                }
            }


        if (newState == BluetoothProfile.STATE_DISCONNECTED) {

            requireActivity().runOnUiThread {

                Handler().postDelayed({
                    _BleData!!.setDisClose()
                }, 500)


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

            val date = Date()
            val formatter = SimpleDateFormat("mm:ss.SSS")
            val disPlayValue = String(Value, StandardCharsets.UTF_8)
//                    _messageText!!.append(" MCU -> APP : " + disPlayValue + " [" + formatter.format(date) + "][" + _notifyCount + "]")
        val adColorMessage = getColoredSpanned(
                "MCU -> APP :[" + formatter.format(date) + "] " + disPlayValue + "\n",
                "#00DB00"
        )//ＲＸ綠色 // 換色
        _mseeage = _mseeage + adColorMessage
//            _notifyCount = _notifyCount + 1
//            _notifyCharSize = _notifyCharSize + disPlayValue

        _activity!!.TempTxText = _activity!!.TempTxText + disPlayValue
        _activity!!.TempTxCount = _activity!!.TempTxCount + 1
    }

    private fun NormalWrite() {

//        val testString = "a1223344556677889900b1223344556677889900c1223344556677889900d1223344556677889900e1223344556677889900"
        val sendtext = _sendText!!.text.toString()

        val sendArray  = sendtext.chunked(20)
        var sendSize = sendArray.size
        var indexSend = 0
        val allByteArray = ArrayList<ByteArray>()

        val bwc = BluetoothLeData.writeCallBack { status ->
            Log.i("writeCallBack", "" + indexSend)

            if (indexSend < sendSize) {
                val adMessage = sendArray.get(indexSend)
                val date = Date()
                val formatter = SimpleDateFormat("mm:ss.SSS")
                val adColorMessage = getColoredSpanned(
                        "APP -> MCU :[" + formatter.format(date) + "] " + adMessage + "\n",
                        "#0000ff"
                )//ＴＸ藍色 // 換色
                _mseeage = _mseeage + adColorMessage
                indexSend = indexSend + 1 //這才是當筆未處理
            }

            if (indexSend >= sendSize) { //傳送完畢
                requireActivity().runOnUiThread {
                    _sendButton!!.isEnabled = true
                }
                return@writeCallBack
            } else if (indexSend < sendSize) { //還有就送
                _Write_BC!!.write(sendArray.get(indexSend))
//                _writeCount = _writeCount + 1
//                _writeCharSize = _writeCharSize + sendArray.get(indexSend)
                _activity!!.TempRxText = _activity!!.TempRxText + sendArray.get(indexSend)
                _activity!!.TempRxCount =  _activity!!.TempRxCount + 1
            }

        }

        _Write_BC!!.write(sendArray.get(0), bwc) //第一筆送出
//        _writeCount = _writeCount + 1
//        _writeCharSize = _writeCharSize + sendArray.get(indexSend)
        _activity!!.TempRxText = _activity!!.TempRxText + sendArray.get(0)
        _activity!!.TempRxCount =  _activity!!.TempRxCount + 1
    }

    private fun CRLF_Write() {

//        val testString = "a1223344556677889900b1223344556677889900c1223344556677889900d1223344556677889900e1223344556677889900"
        val sendtext = _sendText!!.text.toString()

        val sendArray  = sendtext.chunked(20)
        var sendSize = sendArray.size
        var indexSend = 0
        val allByteArray = ArrayList<ByteArray>()

        if(_checkBox_ADD_CRLF.isChecked == true){
            for (sa in sendArray){
                val Code = byteArrayOf(0x0d)+byteArrayOf(0x0a)
                val saByte = sa.toByteArray()
                val s = saByte + Code
                allByteArray.add(s)
            }
        }

        val bwc = BluetoothLeData.writeCallBack { status ->
            Log.i("writeCallBack", "" + indexSend)

            if (indexSend < sendSize) {
                val adMessage = sendArray.get(indexSend)
                val date = Date()
                val formatter = SimpleDateFormat("mm:ss.SSS")
                val adColorMessage = getColoredSpanned(
                    "APP -> MCU :[" + formatter.format(date) + "] " + adMessage + "\n",
                    "#0000ff"
                )//ＴＸ藍色 // 換色
                _mseeage = _mseeage + adColorMessage
                indexSend = indexSend + 1 //這才是當筆未處理
            }

            if (indexSend >= sendSize) { //傳送完畢
                requireActivity().runOnUiThread {
                    _sendButton!!.isEnabled = true
                }
                return@writeCallBack
            } else if (indexSend < sendSize) { //還有就送
                _Write_BC!!.write(allByteArray.get(indexSend))
//                _writeCount = _writeCount + 1
//                _writeCharSize = _writeCharSize + allByteArray.get(indexSend)
                _activity!!.TempRxText = _activity!!.TempRxText + sendArray.get(indexSend)
                _activity!!.TempRxCount =  _activity!!.TempRxCount + 1
            }

        }

        _Write_BC!!.write(allByteArray.get(0), bwc) //第一筆送出
//        _writeCount = _writeCount + 1
//        _writeCharSize = _writeCharSize + allByteArray.get(indexSend)
        _activity!!.TempRxText = _activity!!.TempRxText + sendArray.get(0)
        _activity!!.TempRxCount =  _activity!!.TempRxCount + 1
    }

    private fun LoopWrite() {

        val sendtext = _sendText!!.text.toString()

        val sendArray = sendtext.chunked(20)
        var sendSize = sendArray.size
        var indexSend = 0

        val bwc = BluetoothLeData.writeCallBack { status ->
            //回傳回來後
            if (indexSend < sendSize) {
                val adMessage = sendArray.get(indexSend)
                val date = Date()
                val formatter = SimpleDateFormat("mm:ss.SSS")

                val adColorMessage = getColoredSpanned(
                        "APP -> MCU :[" + formatter.format(date) + "] " + adMessage + "\n",
                        "#0000ff"
                )//ＴＸ藍色 // 換色
                _mseeage = _mseeage + adColorMessage

                indexSend = indexSend + 1 //這才是當筆未處理
            }

            if (indexSend >= sendSize) { //傳送完畢

                if (_checkBox!!.isChecked == false) {
                    requireActivity().runOnUiThread {
                        _sendButton!!.isEnabled = true
                    }
                    return@writeCallBack
                }

                indexSend = 0
                _Write_BC!!.write(sendArray.get(indexSend)) //新的第一筆送出
//                _writeCount = _writeCount + 1
//                _writeCharSize = _writeCharSize + sendArray.get(indexSend)
                _activity!!.TempRxText = _activity!!.TempRxText + sendArray.get(indexSend)
                _activity!!.TempRxCount =  _activity!!.TempRxCount + 1

            } else if (indexSend < sendSize) { //還有就送
                _Write_BC!!.write(sendArray.get(indexSend))
//                _writeCount = _writeCount + 1
//                _writeCharSize = _writeCharSize + sendArray.get(indexSend)
                _activity!!.TempRxText = _activity!!.TempRxText + sendArray.get(indexSend)
                _activity!!.TempRxCount =  _activity!!.TempRxCount + 1
            }

        }

        _Write_BC!!.write(sendArray.get(0), bwc) //第一筆送出
//        _writeCount = _writeCount + 1
//        _writeCharSize = _writeCharSize + sendArray.get(indexSend)

        _activity!!.TempRxText = _activity!!.TempRxText + sendArray.get(0)
        _activity!!.TempRxCount =  _activity!!.TempRxCount + 1
    }


}