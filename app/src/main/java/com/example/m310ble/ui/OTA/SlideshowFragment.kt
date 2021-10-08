package com.example.m310ble.ui.OTA

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.m310ble.R

class SlideshowFragment : Fragment() {


    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {

        val root = inflater.inflate(R.layout.fragment_slideshow, container, false)
//        val textView: TextView = root.findViewById(R.id.text_slideshow)
//        textView.text = "This is slideshow Fragment aaaa"

        return root
    }
}