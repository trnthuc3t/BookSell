package com.pro.book.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.pro.book.MyApplication.Companion.get
import com.pro.book.R
import com.pro.book.database.ProductDatabase.Companion.getInstance
import com.pro.book.event.DisplayCartEvent
import com.pro.book.event.OrderSuccessEvent
import com.pro.book.model.Order
import com.pro.book.utils.Constant
import org.greenrobot.eventbus.EventBus

class PaymentSuccessActivity : BaseActivity() {

    private var order: Order? = null
    private var transactionId: String? = null

    private lateinit var tvTransactionMessage: TextView
    private lateinit var tvTransactionSubMessage: TextView
    private lateinit var tvOrderId: TextView
    private lateinit var tvTransactionId: TextView
    private lateinit var btnViewOrder: Button
    private lateinit var btnBackHome: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payment_success)

        bindViews()
        extractDataFromIntent()
        if (order == null) {
            finish()
            return
        }

        populateOrderInfo()
        setActionButtonsEnabled(false)
        createOrderOnFirebase()
        initListeners()
    }

    private fun bindViews() {
        tvTransactionMessage = findViewById(R.id.tv_transaction_message)
        tvTransactionSubMessage = findViewById(R.id.tv_transaction_sub_message)
        tvOrderId = findViewById(R.id.tv_order_id)
        tvTransactionId = findViewById(R.id.tv_transaction_id)
        btnViewOrder = findViewById(R.id.btn_view_order)
        btnBackHome = findViewById(R.id.btn_back_home)
    }

    private fun extractDataFromIntent() {
        val extras = intent.extras ?: return
        order = extras.getSerializable(Constant.ORDER_OBJECT) as? Order
        transactionId = extras.getString(EXTRA_TRANSACTION_ID)
    }

    private fun populateOrderInfo() {
        tvTransactionMessage.text = getString(R.string.label_thank_you)
        tvTransactionSubMessage.text = getString(R.string.label_your_transaction_success)
        tvOrderId.text = getString(R.string.label_id) + " #" + order!!.id
        if (!transactionId.isNullOrEmpty()) {
            tvTransactionId.text =
                getString(R.string.label_id_transaction) + ": " + transactionId
            tvTransactionId.visibility = View.VISIBLE
        } else {
            tvTransactionId.text = ""
            tvTransactionId.visibility = View.GONE
        }
    }

    private fun initListeners() {
        btnViewOrder.setOnClickListener {
            if (order == null) return@setOnClickListener
            val bundle = Bundle().apply { putLong(Constant.ORDER_ID, order!!.id) }
            val intent = Intent(this, TrackingOrderActivity::class.java)
            intent.putExtras(bundle)
            startActivity(intent)
            finishAffinity()
        }

        btnBackHome.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
    }

    private fun createOrderOnFirebase() {
        showProgressDialog(true)
        get(this).orderDatabaseReference
            .child(order!!.id.toString())
            .setValue(order) { databaseError, _ ->
                showProgressDialog(false)
                if (databaseError != null) {
                    showToastMessage(getString(R.string.msg_get_date_error))
                    setActionButtonsEnabled(true)
                    return@setValue
                }
                getInstance(this)!!.productDAO()!!.deleteAllProduct()
                EventBus.getDefault().post(DisplayCartEvent())
                EventBus.getDefault().post(OrderSuccessEvent())
                setActionButtonsEnabled(true)
            }
    }

    private fun setActionButtonsEnabled(enabled: Boolean) {
        btnViewOrder.isEnabled = enabled
        btnBackHome.isEnabled = enabled
        btnViewOrder.alpha = if (enabled) 1f else 0.5f
        btnBackHome.alpha = if (enabled) 1f else 0.5f
    }

    companion object {
        const val EXTRA_TRANSACTION_ID = "extra_transaction_id"
    }
}
