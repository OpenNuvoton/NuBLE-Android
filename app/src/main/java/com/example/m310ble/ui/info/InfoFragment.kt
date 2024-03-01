package com.example.m310ble.ui.info

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.text.method.LinkMovementMethod
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.example.m310ble.R
import com.example.m310ble.UtilTool.Log

class InfoFragment : Fragment() {

    companion object {
        fun newInstance() = InfoFragment()
    }

    private lateinit var viewModel: InfoViewModel
    private lateinit var _policy: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.info_fragment, container, false)

//        _sendButton = root.findViewById(R.id.SEND_BUTTON)
//        _sendButton!!.setOnClickListener(onClickSendButton)
        _policy = root.findViewById<View>(R.id.policy_button) as Button
        _policy.setOnClickListener {
            Log.i("HomeFragment", "onCreateView1")
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setData(Uri.parse("http://www.nuvoton.com/app-privacy-policy"))
                startActivity(intent)
            } catch (e: Exception) {
            }
        }

        return root
    }



}