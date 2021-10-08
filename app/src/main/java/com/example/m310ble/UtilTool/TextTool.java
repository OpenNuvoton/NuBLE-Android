package com.example.m310ble.UtilTool;

public class TextTool {


    public static String getColoredSpanned(String text,String color) {

        String message = text.replace("\n", "<br />");

        return "<font color="+color+">"+message+"</font>";
    }
}
