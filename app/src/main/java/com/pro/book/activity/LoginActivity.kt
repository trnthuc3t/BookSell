package com.pro.book.activity

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.pro.book.R
import com.pro.book.activity.admin.AdminMainActivity
import com.pro.book.model.User
import com.pro.book.prefs.DataStoreManager
import com.pro.book.utils.Constant
import com.pro.book.utils.GlobalFunction.startActivity
import com.pro.book.utils.StringUtil.isEmpty
import com.pro.book.utils.StringUtil.isValidEmail

class LoginActivity : BaseActivity() {

    // Views
    private lateinit var edtEmail: EditText
    private lateinit var edtPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var layoutRegister: LinearLayout
    private lateinit var tvForgotPassword: TextView

    // State
    private var isEnableButtonLogin = false

    // Firebase
    private lateinit var firebaseAuth: FirebaseAuth

    // Timeout
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutPosted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_in)

        firebaseAuth = FirebaseAuth.getInstance()
        initUi()
        initListener()
    }

    // ---------------- UI ----------------
    private fun initUi() {
        edtEmail = findViewById(R.id.edt_email)
        edtPassword = findViewById(R.id.edt_password)
        btnLogin = findViewById(R.id.btn_login)
        layoutRegister = findViewById(R.id.layout_register)
        tvForgotPassword = findViewById(R.id.tv_forgot_password)
    }

    private fun initListener() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = updateLoginButtonState()
        }
        edtEmail.addTextChangedListener(watcher)
        edtPassword.addTextChangedListener(watcher)

        layoutRegister.setOnClickListener { startActivity(this, RegisterActivity::class.java) }
        tvForgotPassword.setOnClickListener { startActivity(this, ForgotPasswordActivity::class.java) }
        btnLogin.setOnClickListener { onClickValidateLogin() }
    }

    private fun updateLoginButtonState() {
        // đổi background input theo trạng thái
        edtEmail.setBackgroundResource(
            if (!isEmpty(edtEmail.text.toString())) R.drawable.bg_white_corner_16_border_main
            else R.drawable.bg_white_corner_16_border_gray
        )
        edtPassword.setBackgroundResource(
            if (!isEmpty(edtPassword.text.toString())) R.drawable.bg_white_corner_16_border_main
            else R.drawable.bg_white_corner_16_border_gray
        )

        isEnableButtonLogin =
            !isEmpty(edtEmail.text.toString().trim()) && !isEmpty(edtPassword.text.toString().trim())

        btnLogin.setBackgroundResource(
            if (isEnableButtonLogin) R.drawable.bg_button_enable_corner_16
            else R.drawable.bg_button_disable_corner_16
        )
    }

    // ------------- Validate & Login -------------
    private fun onClickValidateLogin() {
        if (!isEnableButtonLogin) return

        val email = edtEmail.text.toString().trim()
        val pass = edtPassword.text.toString().trim()

        when {
            isEmpty(email) -> {
                showToastFlexible(R.string.msg_email_require, R.drawable.ic_info, R.color.toast_info)
            }
            isEmpty(pass) -> {
                showToastFlexible(R.string.msg_password_require, R.drawable.ic_info, R.color.toast_info)
            }
            !isValidEmail(email) -> {
                showToastFlexible(R.string.msg_email_invalid, R.drawable.ic_info, R.color.toast_info)
            }
            else -> loginUserFirebase(email, pass)
        }
    }

    private fun loginUserFirebase(email: String, password: String) {
        if (!isConnected()) {
            showToastFlexible(R.string.msg_network_error, R.drawable.ic_warning, R.color.toast_warning)
            return
        }

        showProgressDialog(true)
        startTimeout()

        firebaseAuth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                clearTimeout()
                showProgressDialog(false)

                val user = firebaseAuth.currentUser
                if (user == null) {
                    showToastFlexible(R.string.msg_login_error_general, R.drawable.ic_error, R.color.toast_error)
                    return@addOnSuccessListener
                }

                val userObject = User(user.email, password).apply {
                    if (user.email?.contains(Constant.ADMIN_EMAIL_FORMAT) == true) isAdmin = true
                }
                DataStoreManager.user = userObject

                // (Tuỳ chọn) báo thành công
                showToastFlexible(R.string.label_success, R.drawable.ic_success, R.color.toast_success)

                goToMainActivity()
            }
            .addOnFailureListener { ex ->
                clearTimeout()
                showProgressDialog(false)
                val (msgRes, iconRes, colorRes) = mapErrorToToast(ex)
                showToastFlexible(msgRes, iconRes, colorRes)
            }
            .addOnCanceledListener {
                clearTimeout()
                showProgressDialog(false)
                showToastFlexible(R.string.msg_login_error_general, R.drawable.ic_error, R.color.toast_error)
            }
    }

    // -------- Error mapping → (string, icon, color) --------
    private fun mapErrorToToast(ex: Exception?): Triple<Int, Int, Int> {
        return when (ex) {
            is FirebaseNetworkException ->
                Triple(R.string.msg_network_error, R.drawable.ic_warning, R.color.toast_warning)

            is FirebaseAuthInvalidCredentialsException ->
                Triple(R.string.msg_login_wrong_credentials, R.drawable.ic_error, R.color.toast_error)

            is FirebaseAuthInvalidUserException -> {
                when (ex.errorCode) {
                    "ERROR_USER_NOT_FOUND", "ERROR_USER_DISABLED" ->
                        Triple(R.string.msg_login_wrong_credentials, R.drawable.ic_error, R.color.toast_error)
                    else ->
                        Triple(R.string.msg_login_error_general, R.drawable.ic_error, R.color.toast_error)
                }
            }

            is FirebaseAuthException -> {
                when (ex.errorCode) {
                    "ERROR_WRONG_PASSWORD",
                    "ERROR_INVALID_EMAIL",
                    "ERROR_INVALID_CREDENTIAL",
                    "ERROR_USER_NOT_FOUND",
                    "ERROR_USER_DISABLED" ->
                        Triple(R.string.msg_login_wrong_credentials, R.drawable.ic_error, R.color.toast_error)
                    "ERROR_NETWORK_REQUEST_FAILED" ->
                        Triple(R.string.msg_network_error, R.drawable.ic_warning, R.color.toast_warning)
                    else ->
                        Triple(R.string.msg_login_error_general, R.drawable.ic_error, R.color.toast_error)
                }
            }

            else -> Triple(R.string.msg_login_error_general, R.drawable.ic_error, R.color.toast_error)
        }
    }

    // ---------------- Utils ----------------
    private fun isConnected(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        val hasInternet = caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return hasInternet && isValidated
    }

    private fun startTimeout(timeoutMs: Long = 15_000L) {
        clearTimeout()
        timeoutPosted = true
        handler.postDelayed({
            if (timeoutPosted) {
                showProgressDialog(false)
                showToastFlexible(R.string.msg_login_error_general, R.drawable.ic_error, R.color.toast_error)
            }
        }, timeoutMs)
    }

    private fun clearTimeout() {
        timeoutPosted = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun goToMainActivity() {
        if (DataStoreManager.user!!.isAdmin) {
            startActivity(this, AdminMainActivity::class.java)
        } else {
            startActivity(this, MainActivity::class.java)
        }
        finishAffinity()
    }

    override fun onDestroy() {
        clearTimeout()
        super.onDestroy()
    }
}
