package com.pro.book.activity

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.afollestad.materialdialogs.MaterialDialog
import com.google.android.material.snackbar.Snackbar
import com.pro.book.R
import com.pro.book.ui.AppToast
import androidx.annotation.ColorRes as ColorRes1

abstract class BaseActivity : AppCompatActivity() {
    private var progressDialog: MaterialDialog? = null
    private var alertDialog: MaterialDialog? = null

    // giữ reference để hủy toast cũ
    private var currentToast: Toast? = null

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        createProgressDialog()
        createAlertDialog()
    }

    private fun createProgressDialog() {
        progressDialog = MaterialDialog.Builder(this)
            .content(R.string.msg_waiting_message)
            .progress(true, 0)
            .build()
    }

    fun showProgressDialog(value: Boolean) {
        if (value) {
            if (progressDialog != null && !progressDialog!!.isShowing) {
                progressDialog!!.show()
                progressDialog!!.setCancelable(false)
            }
        } else {
            if (progressDialog != null && progressDialog!!.isShowing) {
                progressDialog!!.dismiss()
            }
        }
    }

    fun dismissProgressDialog() {
        if (progressDialog != null && progressDialog!!.isShowing) {
            progressDialog!!.dismiss()
        }

        if (alertDialog != null && alertDialog!!.isShowing) {
            alertDialog!!.dismiss()
        }
    }

    private fun createAlertDialog() {
        alertDialog = MaterialDialog.Builder(this)
            .title(R.string.app_name)
            .positiveText(R.string.action_ok)
            .cancelable(false)
            .build()
    }

    fun showAlertDialog(errorMessage: String?) {
        alertDialog!!.setContent(errorMessage)
        alertDialog!!.show()
    }

    fun showAlertDialog(@StringRes resourceId: Int) {
        alertDialog!!.setContent(resourceId)
        alertDialog!!.show()
    }

    fun setCancelProgress(isCancel: Boolean) {
        if (progressDialog != null) {
            progressDialog!!.setCancelable(isCancel)
        }
    }
    /**
     * Giữ API cũ để không phải thay đổi nhiều nơi trong project.
     * showToastMessage sẽ render custom toast (info style).
     */
    fun showToastMessage(message: String?) {
        AppToast.showText(
            context = this,
            text = message ?: "",
            iconRes = R.drawable.ic_info,
            bgColor = ContextCompat.getColor(this, R.color.toast_info),
            duration = Toast.LENGTH_SHORT
        )
    }
    /**
     * Hàm chính dùng để hiện custom toast.
     * ToastType: INFO / SUCCESS / ERROR
     */
    fun showCustomToast(message: String, type: ToastType = ToastType.INFO) {
        val (icon, colorRes) = when (type) {
            ToastType.SUCCESS -> R.drawable.ic_success to R.color.toast_success
            ToastType.ERROR   -> R.drawable.ic_error   to R.color.toast_error
            ToastType.INFO    -> R.drawable.ic_info   to R.color.toast_info
        }
        AppToast.showText(
            context = this,
            text = message,
            iconRes = icon,
            bgColor = ContextCompat.getColor(this, colorRes),
            duration = Toast.LENGTH_SHORT
        )
    }
    fun showToastFlexible(
        @StringRes msgRes: Int,
        @DrawableRes iconRes: Int? = null,
        @ColorRes1 bgColorRes: Int? = null,
        duration: Int = Toast.LENGTH_SHORT
    ) {
        AppToast.show(
            context = this,
            messageRes = msgRes,
            iconRes = iconRes,
            bgColorRes = bgColorRes,
            duration = duration
        )
    }
    /**
     * Snackbar tiện ích: truyền view root (hoặc null để lấy content view)
     * actionText có thể null nếu không cần action
     */
    fun showSnackBar(rootView: View? = null, message: String, actionText: String? = null, action: (() -> Unit)? = null) {
        val parent = rootView ?: findViewById(android.R.id.content)
        val sb = Snackbar.make(parent, message, Snackbar.LENGTH_LONG)
        if (actionText != null && action != null) {
            sb.setAction(actionText) { action() }
        }
        val view = sb.view
        view.setBackgroundResource(R.drawable.bg_snackbar)
        val tv = view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        tv.maxLines = 3
        sb.show()
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    override fun onDestroy() {
        if (progressDialog != null && progressDialog!!.isShowing) {
            progressDialog!!.dismiss()
        }

        if (alertDialog != null && alertDialog!!.isShowing) {
            alertDialog!!.dismiss()
        }
        currentToast?.cancel()
        super.onDestroy()
    }
}

enum class ToastType {
    INFO, SUCCESS, ERROR
}
