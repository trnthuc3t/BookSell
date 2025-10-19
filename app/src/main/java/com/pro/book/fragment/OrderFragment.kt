package com.pro.book.fragment

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.pro.book.MyApplication.Companion.get
import com.pro.book.R
import com.pro.book.activity.ReceiptOrderActivity
import com.pro.book.activity.TrackingOrderActivity
import com.pro.book.adapter.OrderAdapter
import com.pro.book.adapter.OrderAdapter.IClickOrderListener
import com.pro.book.model.Order
import com.pro.book.model.TabOrder
import com.pro.book.prefs.DataStoreManager
import com.pro.book.utils.Constant
import com.pro.book.utils.GlobalFunction.startActivity

class OrderFragment : Fragment() {
    private var mView: View? = null

    private var orderTabType = 0
    private var listOrder: MutableList<Order>? = null
    private var orderAdapter: OrderAdapter? = null
    private var mOrderAllValueEventListener: ValueEventListener? = null
    private var mOrderValueEventListener: ValueEventListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        mView = inflater.inflate(R.layout.fragment_order, container, false)

        dataArguments
        initUi()
        if (DataStoreManager.user!!.isAdmin) {
            listOrderAllUsersFromFirebase
        } else {
            listOrderFromFirebase
        }

        return mView
    }

    private val dataArguments: Unit
        get() {
            val bundle = arguments ?: return
            orderTabType = bundle.getInt(Constant.ORDER_TAB_TYPE)
        }

    private fun initUi() {
        listOrder = ArrayList()
        val rcvOrder = mView!!.findViewById<RecyclerView>(R.id.rcv_order)
        val linearLayoutManager = LinearLayoutManager(activity)
        rcvOrder.layoutManager = linearLayoutManager
        orderAdapter = OrderAdapter(activity, listOrder, object : IClickOrderListener {
            override fun onClickTrackingOrder(orderId: Long) {
                val bundle = Bundle()
                bundle.putLong(Constant.ORDER_ID, orderId)
                startActivity(activity!!, TrackingOrderActivity::class.java, bundle)
            }

            override fun onClickReceiptOrder(order: Order?) {
                val bundle = Bundle()
                bundle.putLong(Constant.ORDER_ID, order!!.id)
                startActivity(activity!!, ReceiptOrderActivity::class.java, bundle)
            }

            override fun onClickCancelOrder(order: Order?) {
                showCancelOrderDialog(order!!)
            }
        })
        rcvOrder.adapter = orderAdapter
    }

    @get:SuppressLint("NotifyDataSetChanged")
    private val listOrderAllUsersFromFirebase: Unit
        get() {
            if (activity == null) return
            mOrderAllValueEventListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (listOrder != null) {
                        listOrder!!.clear()
                    } else {
                        listOrder = ArrayList()
                    }
                    for (dataSnapshot in snapshot.children) {
                        val order = dataSnapshot.getValue(
                            Order::class.java
                        )
                        if (order != null) {
                            if (TabOrder.TAB_ORDER_PROCESS == orderTabType) {
                                if (Order.STATUS_COMPLETE != order.status && Order.STATUS_CANCELLED != order.status) {
                                    listOrder!!.add(0, order)
                                }
                            } else if (TabOrder.TAB_ORDER_DONE == orderTabType) {
                                if (Order.STATUS_COMPLETE == order.status || Order.STATUS_CANCELLED == order.status) {
                                    listOrder!!.add(0, order)
                                }
                            }
                        }
                    }
                    if (orderAdapter != null) orderAdapter!!.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                }
            }
            get(requireActivity()).orderDatabaseReference
                .addValueEventListener(mOrderAllValueEventListener!!)
        }

    @get:SuppressLint("NotifyDataSetChanged")
    private val listOrderFromFirebase: Unit
        get() {
            if (activity == null) return
            mOrderValueEventListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (listOrder != null) {
                        listOrder!!.clear()
                    } else {
                        listOrder = ArrayList()
                    }
                    for (dataSnapshot in snapshot.children) {
                        val order = dataSnapshot.getValue(
                            Order::class.java
                        )
                        if (order != null) {
                            if (TabOrder.TAB_ORDER_PROCESS == orderTabType) {
                                if (Order.STATUS_COMPLETE != order.status && Order.STATUS_CANCELLED != order.status) {
                                    listOrder!!.add(0, order)
                                }
                            } else if (TabOrder.TAB_ORDER_DONE == orderTabType) {
                                if (Order.STATUS_COMPLETE == order.status || Order.STATUS_CANCELLED == order.status) {
                                    listOrder!!.add(0, order)
                                }
                            }
                        }
                    }
                    if (orderAdapter != null) orderAdapter!!.notifyDataSetChanged()
                }

                override fun onCancelled(error: DatabaseError) {
                }
            }
            get(requireActivity()).orderDatabaseReference
                .orderByChild("userEmail").equalTo(DataStoreManager.user?.email)
                .addValueEventListener(mOrderValueEventListener!!)
        }

    override fun onDestroyView() {
        super.onDestroyView()
        if (orderAdapter != null) orderAdapter!!.release()
        if (activity != null && mOrderAllValueEventListener != null) {
            get(requireActivity()).orderDatabaseReference
                .removeEventListener(mOrderAllValueEventListener!!)
        }
        if (activity != null && mOrderValueEventListener != null) {
            get(requireActivity()).orderDatabaseReference
                .removeEventListener(mOrderValueEventListener!!)
        }
    }

    private fun showCancelOrderDialog(order: Order) {
        val message = buildString {
            append("Bạn có chắc chắn muốn hủy đơn hàng #${order.id}?\n\n")
            append("📋 Thông tin đơn hàng:\n")
            append("• Khách hàng: ${order.userEmail}\n")
            append("• Tổng tiền: ${order.total}k\n")
            append("• Phương thức: ${order.paymentMethod}\n")
            append("• Trạng thái: Mới (giai đoạn 1)\n\n")
            append("⚠️ Lưu ý: Chỉ có thể hủy đơn hàng ở giai đoạn 1 và không thanh toán bằng ZaloPay")
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("Xác nhận hủy đơn hàng")
            .setMessage(message)
            .setPositiveButton("Hủy đơn hàng") { _, _ ->
                cancelOrder(order)
            }
            .setNegativeButton("Không", null)
            .show()
    }

    private fun cancelOrder(order: Order) {
        // Kiểm tra lại điều kiện trước khi hủy
        if (order.status != Order.STATUS_NEW) {
            Toast.makeText(requireContext(), "Chỉ có thể hủy đơn hàng ở trạng thái 'Mới'", Toast.LENGTH_SHORT).show()
            return
        }
        
        val paymentMethod = order.paymentMethod?.lowercase() ?: ""
        if (paymentMethod.contains("zalo") || paymentMethod.contains("zalopay")) {
            Toast.makeText(requireContext(), "Không thể hủy đơn hàng thanh toán bằng ZaloPay", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Cập nhật trạng thái đơn hàng thành CANCELLED
        get(requireActivity()).orderDatabaseReference
            .child(order.id.toString())
            .child("status")
            .setValue(Order.STATUS_CANCELLED)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(requireContext(), "Đã hủy đơn hàng #${order.id}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Có lỗi xảy ra khi hủy đơn hàng", Toast.LENGTH_SHORT).show()
                }
            }
    }

    companion object {
        fun newInstance(type: Int): OrderFragment {
            val orderFragment = OrderFragment()
            val bundle = Bundle()
            bundle.putInt(Constant.ORDER_TAB_TYPE, type)
            orderFragment.arguments = bundle
            return orderFragment
        }
    }
}
