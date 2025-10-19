package com.pro.book.activity

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.pro.book.MyApplication.Companion.get
import com.pro.book.R
import com.pro.book.adapter.AddressAdapter
import com.pro.book.event.AddressSelectedEvent
import com.pro.book.model.Address
import com.pro.book.prefs.DataStoreManager
import com.pro.book.utils.Constant
import com.pro.book.utils.GlobalFunction
import com.pro.book.utils.StringUtil
import org.greenrobot.eventbus.EventBus

class AddressActivity : BaseActivity() {
    private var listAddress: MutableList<Address>? = null
    private var addressAdapter: AddressAdapter? = null
    private var addressSelectedId: Long = 0
    private var mValueEventListener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_address)
        loadDataIntent()
        initToolbar()
        initUi()
        loadListAddressFromFirebase()
    }

    private fun loadDataIntent() {
        addressSelectedId = intent.getLongExtra(Constant.ADDRESS_ID, 0)
    }

    private fun initToolbar() {
        findViewById<ImageView>(R.id.img_toolbar_back).setOnClickListener { onBackPressed() }
        findViewById<TextView>(R.id.tv_toolbar_title).text = getString(R.string.address_title)
    }

    private fun initUi() {
        val rcvAddress = findViewById<RecyclerView>(R.id.rcv_address)
        rcvAddress.layoutManager = LinearLayoutManager(this)
        listAddress = ArrayList()

        // Thay đổi: Triển khai 3 hàm của interface
        addressAdapter = AddressAdapter(listAddress, object : AddressAdapter.IClickAddressListener {
            override fun onClickAddressItem(address: Address) {
                // Gửi sự kiện chọn địa chỉ về cho CartActivity và đóng màn hình
                EventBus.getDefault().post(AddressSelectedEvent(address))
                finish()
            }

            override fun onClickEditAddress(address: Address) {
                // Mở BottomSheet ở chế độ Sửa
                openAddressBottomSheet(address)
            }

            override fun onClickDeleteAddress(address: Address) {
                // Hiển thị dialog xác nhận xóa
                showConfirmDeleteAddress(address)
            }
        })
        rcvAddress.adapter = addressAdapter

        // Mở BottomSheet ở chế độ Thêm mới
        findViewById<Button>(R.id.btn_add_address).setOnClickListener { openAddressBottomSheet(null) }
    }

    // Thêm vào: Hàm hiển thị dialog xác nhận xóa
    private fun showConfirmDeleteAddress(address: Address) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_delete_address_title))
            .setMessage(getString(R.string.confirm_delete_address_message))
            .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                get(this).addressDatabaseReference.child(address.id.toString()).removeValue { _, _ ->
                    GlobalFunction.showToastMessage(this, getString(R.string.msg_delete_address_success))
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadListAddressFromFirebase() {
        showProgressDialog(true)
        val userEmail = DataStoreManager.user?.email ?: return

        mValueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                showProgressDialog(false)
                listAddress?.clear()
                for (dataSnapshot in snapshot.children) {
                    val address = dataSnapshot.getValue(Address::class.java)
                    if (address != null) {
                        listAddress?.add(0, address)
                    }
                }
                updateAddressSelection()
                addressAdapter?.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {
                showProgressDialog(false)
                GlobalFunction.showToastMessage(this@AddressActivity, getString(R.string.msg_get_date_error))
            }
        }
        get(this).addressDatabaseReference
            .orderByChild("userEmail").equalTo(userEmail)
            .addValueEventListener(mValueEventListener!!)
    }

    private fun updateAddressSelection() {
        if (addressSelectedId > 0 && !listAddress.isNullOrEmpty()) {
            listAddress?.forEach { it.isSelected = it.id == addressSelectedId }
        }
    }

    // Thay đổi lớn: Tái cấu trúc hàm để dùng cho cả Thêm và Sửa
    @SuppressLint("InflateParams")
    private fun openAddressBottomSheet(address: Address?) {
        val viewDialog = layoutInflater.inflate(R.layout.layout_bottom_sheet_add_address, null)
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(viewDialog)
        bottomSheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED

        val edtName = viewDialog.findViewById<EditText>(R.id.edt_name)
        val edtPhone = viewDialog.findViewById<EditText>(R.id.edt_phone)
        val edtAddress = viewDialog.findViewById<EditText>(R.id.edt_address)
        val tvCancel = viewDialog.findViewById<TextView>(R.id.tv_cancel)
        val tvAddOrUpdate = viewDialog.findViewById<TextView>(R.id.tv_add)
        val tvTitle = viewDialog.findViewById<TextView>(R.id.tv_title)

        val isUpdate = address != null
        if (isUpdate) {
            tvTitle.text = getString(R.string.title_edit_address)
            tvAddOrUpdate.text = getString(R.string.action_update)
            edtName.setText(address?.name)
            edtPhone.setText(address?.phone)
            edtAddress.setText(address?.address)
        } else {
            tvTitle.text = getString(R.string.title_add_address)
            tvAddOrUpdate.text = getString(R.string.action_add)
        }

        tvCancel.setOnClickListener { bottomSheetDialog.dismiss() }

        tvAddOrUpdate.setOnClickListener {
            val strName = edtName.text.toString().trim()
            val strPhone = edtPhone.text.toString().trim()
            val strAddress = edtAddress.text.toString().trim()

            if (StringUtil.isEmpty(strName) || StringUtil.isEmpty(strPhone) || StringUtil.isEmpty(strAddress)) {
                GlobalFunction.showToastMessage(this, getString(R.string.message_enter_infor))
                return@setOnClickListener
            }

            val id = address?.id ?: System.currentTimeMillis()
            val newAddress = Address(id, strName, strPhone, strAddress, DataStoreManager.user?.email)

            get(this).addressDatabaseReference.child(id.toString()).setValue(newAddress) { _, _ ->
                val message = if (isUpdate) getString(R.string.msg_edit_address_success) else getString(R.string.msg_add_address_success)
                GlobalFunction.showToastMessage(this, message)
                GlobalFunction.hideSoftKeyboard(this)
                bottomSheetDialog.dismiss()
            }
        }
        bottomSheetDialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        mValueEventListener?.let { get(this).addressDatabaseReference.removeEventListener(it) }
    }
}