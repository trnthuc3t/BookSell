package com.pro.book.activity

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.pro.book.R
import com.pro.book.model.User
import com.pro.book.prefs.DataStoreManager
import com.pro.book.utils.StringUtil.isEmpty

class ChangePasswordActivity : BaseActivity() {

    // Khai báo các view
    private var edtOldPassword: EditText? = null
    private var edtNewPassword: EditText? = null
    private var edtConfirmPassword: EditText? = null
    private var btnChangePassword: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        // Gọi hàm khởi tạo
        setupToolbar()
        initViews()
        setListeners()
    }

    /**
     * Cấu hình toolbar: nút quay lại và tiêu đề
     */
    private fun setupToolbar() {
        val imgBack = findViewById<ImageView>(R.id.img_toolbar_back)
        val tvTitle = findViewById<TextView>(R.id.tv_toolbar_title)

        imgBack.setOnClickListener { finish() }
        tvTitle.text = getString(R.string.change_password)
    }

    /**
     * Khởi tạo view từ layout
     */
    private fun initViews() {
        edtOldPassword = findViewById(R.id.edt_old_password)
        edtNewPassword = findViewById(R.id.edt_new_password)
        edtConfirmPassword = findViewById(R.id.edt_confirm_password)
        btnChangePassword = findViewById(R.id.btn_change_password)
    }

    /**
     * Lắng nghe sự kiện nhập liệu và nút bấm
     */
    private fun setListeners() {
        // Khi nhập mật khẩu cũ
        edtOldPassword?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable) {
                val res = if (!isEmpty(s.toString()))
                    R.drawable.bg_white_corner_16_border_main
                else
                    R.drawable.bg_white_corner_16_border_gray

                edtOldPassword?.setBackgroundResource(res)
            }
        })

        // Khi nhập mật khẩu mới
        edtNewPassword?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable) {
                val res = if (!isEmpty(s.toString()))
                    R.drawable.bg_white_corner_16_border_main
                else
                    R.drawable.bg_white_corner_16_border_gray

                edtNewPassword?.setBackgroundResource(res)
            }
        })

        // Khi nhập lại mật khẩu
        edtConfirmPassword?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable) {
                val res = if (!isEmpty(s.toString()))
                    R.drawable.bg_white_corner_16_border_main
                else
                    R.drawable.bg_white_corner_16_border_gray

                edtConfirmPassword?.setBackgroundResource(res)
            }
        })

        // Khi nhấn nút "Đổi mật khẩu"
        btnChangePassword?.setOnClickListener {
            validateChangePassword()
        }
    }

    /**
     * Kiểm tra dữ liệu nhập hợp lệ trước khi đổi mật khẩu
     */
    private fun validateChangePassword() {
        val oldPass = edtOldPassword?.text.toString().trim()
        val newPass = edtNewPassword?.text.toString().trim()
        val confirmPass = edtConfirmPassword?.text.toString().trim()

        when {
            isEmpty(oldPass) ->
                showToastMessage(getString(R.string.msg_old_password_require))

            isEmpty(newPass) ->
                showToastMessage(getString(R.string.msg_new_password_require))

            isEmpty(confirmPass) ->
                showToastMessage(getString(R.string.msg_confirm_password_require))

            !DataStoreManager.user?.password.equals(oldPass) ->
                showToastMessage(getString(R.string.msg_old_password_invalid))

            newPass != confirmPass ->
                showToastMessage(getString(R.string.msg_confirm_password_invalid))

            oldPass == newPass ->
                showToastMessage(getString(R.string.msg_new_password_invalid))

            else ->
                changePassword(newPass)
        }
    }

    /**
     * Gọi Firebase để cập nhật mật khẩu mới
     */
    private fun changePassword(newPassword: String) {
        showProgressDialog(true)

        val user = FirebaseAuth.getInstance().currentUser ?: return
        user.updatePassword(newPassword)
            .addOnCompleteListener { task: Task<Void?> ->
                showProgressDialog(false)

                if (task.isSuccessful) {
                    showToastMessage(getString(R.string.msg_change_password_successfully))

                    // Cập nhật lại password trong local DataStore
                    val userLogin: User = DataStoreManager.user!!
                    userLogin.password = newPassword
                    DataStoreManager.user = userLogin

                    // Xóa nội dung các ô nhập
                    edtOldPassword?.setText("")
                    edtNewPassword?.setText("")
                    edtConfirmPassword?.setText("")
                }
            }
    }
}
