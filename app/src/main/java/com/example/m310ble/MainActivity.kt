package com.example.m310ble

import android.bluetooth.le.ScanResult
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentManager
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.afollestad.materialdialogs.MaterialDialog
import com.clj.fastble.BleManager
import com.clj.fastble.scan.BleScanRuleConfig
import com.example.m310ble.Bluetooth.BluetoothLeData
import com.example.m310ble.Bluetooth.BluetoothLeDataManager
import com.example.m310ble.UtilTool.Log
import com.example.m310ble.UtilTool.PermissionManager
import com.example.m310ble.UtilTool.ResourceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.microsoft.appcenter.AppCenter
import com.microsoft.appcenter.analytics.Analytics
import com.microsoft.appcenter.crashes.Crashes
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    public var _BleData: BluetoothLeData? = null
    public var _Write_BC: BluetoothLeData.CharacteristicData? = null
    private val _bdm = BluetoothLeDataManager.getInstance()
    private val TAG = javaClass.name
    public lateinit var ALERT_DIALOG: MaterialDialog
    private var _scanResultArray: ArrayList<ScanResult> = ArrayList<ScanResult>()
    private var _scanResultDeviceArray: ArrayList<String> = ArrayList<String>()
    private lateinit var _bleDevice: TextView
    private lateinit var _bleStatus: TextView
    private var _notifyCount = 0
    private lateinit var _drawerLayout : DrawerLayout
    private lateinit var _fragmentManager: FragmentManager

    //幫Fragment記錄東西
    public var TempFragmentMseeage: String = ""
    public var TempFragmentText: String = ""
    public var TempTxText:String = ""
    public var TempRxText:String = ""
    public var TempTxCount:Int = 0
    public var TempRxCount:Int = 0
    public var TempBleData_UART: BluetoothLeData? = null
    public var TempWriteBC_UART: BluetoothLeData.CharacteristicData? = null
    public var TempNotifyBC_UART: BluetoothLeData.CharacteristicData? = null
    public var TempBleData_LED: BluetoothLeData? = null
    public var TempWriteBC_LED: BluetoothLeData.CharacteristicData? = null

    //OTA Temp
//    public var TempBleDevice_OTA: BleDevice? = null
//    public var bleGattCallback : BleGattCallback? = null
    //Rate Temp
//    public var TempBleDevice_Rate: BleDevice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppCenter.start( //AppCenter
            application, "769a3e47-b798-479f-a9fc-0a3f195bc084",
            Analytics::class.java, Crashes::class.java
        )

        setContentView(R.layout.activity_main)
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        val fab: FloatingActionButton = findViewById(R.id.fab)
        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()
        }

        ResourceManager.context = this

        _drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val navController = findNavController(R.id.nav_host_fragment)
        val versionText: TextView = navView.getHeaderView(0).findViewById(R.id.versionText)
        try {
            val pInfo: PackageInfo = this.getPackageManager().getPackageInfo(
                this.getPackageName(),
                0
            )
            val version: String = pInfo.versionName
            versionText.text = "version: "+version
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        _bleDevice = findViewById(R.id.BLE_DEVICE_TEXT)
        _bleStatus = findViewById(R.id.BLE_STATUS_TEXT)

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration( //新增頁面
            setOf(
                R.id.nav_home,
                R.id.nav_gallery,
                R.id.nav_slideshow,
                R.id.nav_datarate,
                R.id.nav_info
            ), _drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

//        navView.setNavigationItemSelectedListener(nvsl)
//        _fragmentManager = supportFragmentManager

        this.setPermission()

        BleManager.getInstance().init(getApplication())

        val scanRuleConfig = BleScanRuleConfig.Builder()
            .setScanTimeOut(0) // 掃描超時時間，可選，默認50秒小於等於0表示不限制掃描時間
            .build()
        BleManager.getInstance().initScanRule(scanRuleConfig)
    }

//     private val nvsl =  NavigationView.OnNavigationItemSelectedListener {
//         val id = it!!.itemId
//         Log.i(TAG, "onNavigationItemSelected " + id)
//
//         // Handle navigation view item clicks here.
//         when (it.itemId) {
//             R.id.nav_home -> {
//                 _fragmentManager.beginTransaction().replace(R.id.drawer_layout, HomeFragment()).commit()
//             }
//             R.id.nav_gallery -> {
//                 _fragmentManager.beginTransaction().replace(R.id.drawer_layout, GalleryFragment()).commit()
//             }
//             R.id.nav_slideshow -> {
//                 _fragmentManager.beginTransaction().replace(R.id.drawer_layout, SlideshowFragment()).commit()
//             }
//
//
//         }
//         // 關閉拉出的抽屜式側邊選單
//         _drawerLayout.closeDrawer(GravityCompat.START)
//         true
//     }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return super.onContextItemSelected(item)
        val id = item!!.groupId
        Log.i(TAG, "onContextItemSelected " + id)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
//        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        val id = item!!.groupId
        Log.i(TAG, "onOptionsItemSelected " + id)

        if(item.toString() == "Disconnect"){
            if(_BleData != null){
                _BleData!!.setDisConnect()
            }
            return false
        }

        if(_bdm.isBluetoothEnabled(this) == false){
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            return false
        }
        if(_bdm.isGPSEnabled(this) == false){
            Toast.makeText(this, R.string.GPS_not_supported, Toast.LENGTH_SHORT).show();
            return false
        }

        if(item.toString() != "Information"){
            return false
        }

//        this.ScanBleDevice()

        AlertDialog.Builder(this)
                .setMessage("service: \n00112233-4455-6677-8899-AABBCCDDEEFF\n\ncharacteristic: \n0000FA02-0000-1000-8000-00805F9B34FB\n\ndescriptor: \n00002902-0000-1000-8000-00805F9B34FB\n\nwriteUUID: \n50515253-5455-5657-5859-5A5B5C5D5E5F")
                .setTitle("BLE")
                .show()

        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            TempBleData_UART?.setDisClose()
            TempBleData_LED?.setDisClose()
            finish()
        }
        return super.onKeyDown(keyCode, event)
    }

//    /**
//     *  打開ＢＬＥ搜尋
//     */
//    public fun ScanBleDevice() {
//
//        _scanResultDeviceArray.clear()
//        _scanResultArray.clear()
//
//        ALERT_DIALOG = MaterialDialog(this)
//                .title(text = "BLE Device")
//                .listItems(items = _scanResultDeviceArray) { dialog, index, text ->
//                    //TODO:點擊ＢＬＥ裝置事件
//
//                    _bleDevice.setText("BLE Device: " + _scanResultArray.get(index).device.name)
//                    Log.d(
//                        TAG,
//                        "onOptionsItemSelected:" + _scanResultArray.get(index).device.name + " " + _scanResultArray.get(
//                            index
//                        ).device.uuids
//                    )
//                    _BleData = _bdm.getBluetoothLeData(
//                        this,
//                        _scanResultArray.get(index).device.address
//                    )
//                    this.connectBle(bleData = this._BleData!!)//藍芽連線
//                }
//        ALERT_DIALOG.show()
//
//        _bdm.scanLeDevice(true, this, scanCallback)
//    }
//
    /**
     * settng the Permission [權限] 註冊使用權限
     * @return boolean
     */
    private fun setPermission(): Boolean {

        val pm = PermissionManager(this)
        val permissionArray = ArrayList<PermissionManager.PermissionType>()
        permissionArray.add(PermissionManager.PermissionType.GPS)
        permissionArray.add(PermissionManager.PermissionType.READ_EXTERNAL_STORAGE)
        permissionArray.add(PermissionManager.PermissionType.WRITE_EXTERNAL_STORAGE)
        permissionArray.add(PermissionManager.PermissionType.BLUETOOTH)
        permissionArray.add(PermissionManager.PermissionType.BLUETOOTH_ADMIN)

        pm.selfPermission("權限", permissionArray)

        return false
    }


//
//    /**
//     * ＢＬＥ搜尋結果
//     */
//    private val scanCallback: ScanCallback = object : ScanCallback() {
//        override fun onScanResult(callbackType: Int, result: ScanResult) {
//            super.onScanResult(callbackType, result)
//            Log.d(TAG, "onScanResult  callbackType:$callbackType   result:$result")
//
//            if (!_scanResultArray.contains(result) && !_scanResultDeviceArray.contains(result.device.name)) {
//                if (result.device.name != null) {
//                    _scanResultArray.add(result)
//                    Log.d(TAG, "onScanResult  deviceName:" + result.device.name)
//                    _scanResultDeviceArray.add(result.device.name)
//                    ALERT_DIALOG.updateListItems(items = _scanResultDeviceArray)
//                }
//            }
//
//        }
//
//        override fun onBatchScanResults(results: List<ScanResult>) {
//            super.onBatchScanResults(results)
//            Log.d(TAG, "results:$results")
//        }
//
//        override fun onScanFailed(errorCode: Int) {
//            super.onScanFailed(errorCode)
//            Log.d(TAG, "errorCode:$errorCode")
//        }
//    }
//
//    /**
//     * ＢＬＥ藍芽連線
//     */
//    private fun connectBle(bleData: BluetoothLeData) {
//
//        if (bleData == null) {
//            return
//        }
//
//        runOnUiThread {
//            _bleStatus.setText("BLE Status: CONNECTING")
//            _bleStatus.setTextColor(Color.BLUE)
//        }
//
//        bleData!!.setOnStateChangeListener(onStateChangeListener)
//
//        bleData!!.connectLeDevice {
//            Log.i("MainActivity", "connectLeDevice:" + it)
//            if (it != true) {
//                onStateChangeListener.onStateChange(bleData.getbleMacAddress(), 0, 0)
//                return@connectLeDevice
//            }
//            for (bs in bleData!!.servicesDataArray) {
//                for (bc in bs.characteristicDataArray) {
//                    Log.i("MainActivity", "characteristic:" + bc.uuid)
//
//                    //專門用來寫入之特徵
//                    if (bc.uuid.indexOf("50515253-5455-5657-5859-5a5b5c5d5e5f") > -1) {
//                        _Write_BC = bc
//                    }
//
//                    //專門用來監聽之特徵
//                    if (bc.uuid.indexOf("0000fa02-0000-1000-8000-00805f9b34fb") > -1) {
//                        bc.setNotify(true, myNotifyListener)
//                        Log.i("MainActivity", "setNotify:" + bc.uuid)
//                    }
//                }
//            }
//        }
//    }
//    /**
//     * 監聽ＢＬＥ連線變化
//     */
//    private var onStateChangeListener = BluetoothLeData.onStateChange { MAC, status, newState ->
//
//        Log.i(
//            "onStateChangeListener",
//            "MAC:" + MAC + "  status:" + status + "  newState:" + newState
//        )
//
//        runOnUiThread {
//            if (newState == BluetoothProfile.STATE_CONNECTING) {
//                        runOnUiThread {
//                            _bleStatus.setText("BLE Status: CONNECTING")
//                            _bleStatus.setTextColor(Color.BLUE)
//                        }
//            }
//
//            if (newState == BluetoothProfile.STATE_CONNECTED) {
//                this._notifyCount = 0
//                runOnUiThread {
//                    _bleStatus.setText("BLE Status: CONNECTED")
//                    _bleStatus.setTextColor(Color.GREEN)
//                }
//            }
//        }
//
//        if (newState == BluetoothProfile.STATE_DISCONNECTED) {
//            this._BleData = null
//            runOnUiThread {
//                _bleStatus.setText("BLE Status: DISCONNECTED")
//                _bleStatus.setTextColor(Color.RED)
//            }
//        }
//    }
//    /***
//     * 收通知的地方
//     */
//    private val myNotifyListener = BluetoothLeData.notifCallBack { bleMAC, UUID, Value ->
//        nuBleInterface!!.notifyCallback(bleMAC, UUID, Value, _notifyCount)
//        _notifyCount = _notifyCount + 1
//    }
//    private var nuBleInterface: bleInterface? = null
//    public fun setbleInterfaceListener(callback: bleInterface){
//        nuBleInterface = callback
//    }
//    interface bleInterface {
//        fun notifyCallback(bleMAC: String, UUID: UUID, Value: ByteArray, count: Int)
//    }

}

