package com.example.m310ble.UtilTool

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.LayoutInflater
import android.widget.EditText
import com.example.m310ble.R
import com.google.android.material.textfield.TextInputEditText

import java.util.*

object DialogTool {

    private var _progressDialog: ProgressDialog? = null
    private var _alertDialog : AlertDialog? = null
    private var _isTimeOut = false

    fun showAlertDialog(context: Context,title:String,message: String,isOkEnable: Boolean,isCancelEnable: Boolean,callback:((isClickOk:Boolean,isClickNo:Boolean) -> Unit)?) {

        this.dismissDialog()

        val builder = AlertDialog.Builder(context)
        _alertDialog = builder.create()
        _alertDialog!!.setTitle(title)
        _alertDialog!!.setMessage(message)
        if (isOkEnable) {
            _alertDialog!!.setButton(AlertDialog.BUTTON_POSITIVE,"ok",DialogInterface.OnClickListener { dialogInterface, i ->
                if(callback!=null)
                    callback.invoke(true,false)
            })
        }
        if (isCancelEnable) {
            _alertDialog!!.setButton(AlertDialog.BUTTON_NEGATIVE,"Cancel",DialogInterface.OnClickListener { dialogInterface, i ->
                if(callback!=null)
                    callback.invoke(false,true)
            })
        }

        _alertDialog!!.show()
    }

    fun showAlertDialog(context: Context,message: String,isOkEnable: Boolean,isCancelEnable: Boolean,callback:((isClickOk:Boolean,isClickNo:Boolean) -> Unit)?) {

        this.dismissDialog()

        val builder = AlertDialog.Builder(context)
        _alertDialog = builder.create()

        _alertDialog!!.setMessage(message)
        if (isOkEnable) {
            _alertDialog!!.setButton(AlertDialog.BUTTON_POSITIVE,"ok",DialogInterface.OnClickListener { dialogInterface, i ->
                if(callback!=null)
                    callback.invoke(true,false)
            })
        }
        if (isCancelEnable) {
            _alertDialog!!.setButton(AlertDialog.BUTTON_NEGATIVE,"Cancel",DialogInterface.OnClickListener { dialogInterface, i ->
                if(callback!=null)
                    callback.invoke(false,true)
            })
        }

        _alertDialog!!.show()
    }

    fun showProgressDialog(context: Context,title:String,message: String,isHorizontalStyle:Boolean) {

        this.dismissDialog()

        _progressDialog = ProgressDialog(context)
        _progressDialog!!.setTitle(title)
        _progressDialog!!.setMessage(message)
        if(isHorizontalStyle){_progressDialog!!.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);}
        _progressDialog!!.setProgress(0);
        _progressDialog!!.setCancelable(false);
        _progressDialog!!.show()
    }

    fun upDataProgressDialog(progress:Int) {
        if(_progressDialog == null){
            return
        }
        _progressDialog!!.setProgress(progress);
    }


    fun dismissDialog(){

        _isTimeOut = false

        if(_progressDialog != null ){
            _progressDialog!!.dismiss()
        }

        if(_alertDialog != null ){
            _alertDialog!!.dismiss()
        }
    }
}