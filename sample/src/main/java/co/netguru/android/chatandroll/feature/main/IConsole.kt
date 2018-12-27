package co.netguru.android.chatandroll.feature.main

import co.netguru.android.chatandroll.BuildConfig

interface IConsole {

    fun printf(text: String, vararg args: Any)

    fun printf(resId: Int, vararg args: Any)

    fun d(text: String, vararg args: Any) {
        if (BuildConfig.DEBUG) printf("<font color=\"#AAAAAA\">$text</font>")
    }

    fun i(text: String, vararg args: Any) {
        if (BuildConfig.DEBUG) greenf(text, args)
    }

    fun e(text: String, vararg args: Any) {
        if (BuildConfig.DEBUG) redf(text, args)
    }

    fun greenf(text: String, vararg args: Any) {
        printf("<font color=\"#009900\">$text</font>")
    }

    fun bluef(text: String, vararg args: Any) {
        printf("<font color=\"#000099\">$text</font>")
    }

    fun redf(text: String, vararg args: Any) {
        printf("<font color=\"#990000\">$text</font>")
    }
}