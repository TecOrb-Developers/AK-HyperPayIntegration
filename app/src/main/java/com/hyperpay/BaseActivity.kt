package com.hyperpay

import android.app.AlertDialog
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity

open class BaseActivity : AppCompatActivity() {

    protected var progressBar: ProgressBar? = null

    protected fun showProgressBar() {
        progressBar?.visibility = View.VISIBLE
    }

    protected fun hideProgressBar() {
        progressBar?.visibility = View.INVISIBLE
    }

    protected fun showAlertDialog(message: String) {
        AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton(R.string.button_ok, null)
                .setCancelable(false)
                .show()
    }

    protected fun showAlertDialog(messageId: Int) {
        showAlertDialog(getString(messageId))
    }
}