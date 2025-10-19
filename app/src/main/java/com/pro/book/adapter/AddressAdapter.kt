package com.pro.book.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.pro.book.R
import com.pro.book.adapter.AddressAdapter.AddressViewHolder
import com.pro.book.model.Address

class AddressAdapter(
    private val listAddress: List<Address>?,
    private val iClickAddressListener: IClickAddressListener
) : RecyclerView.Adapter<AddressViewHolder>() {

    // Thay đổi: Thêm 2 hàm mới vào interface
    interface IClickAddressListener {
        fun onClickAddressItem(address: Address)
        fun onClickEditAddress(address: Address)
        fun onClickDeleteAddress(address: Address)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AddressViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_address, parent, false)
        return AddressViewHolder(view)
    }

    override fun onBindViewHolder(holder: AddressViewHolder, position: Int) {
        val address = listAddress!![position]
        holder.tvName.text = address.name
        holder.tvPhone.text = address.phone
        holder.tvAddress.text = address.address

        if (address.isSelected) {
            holder.imgStatus.setImageResource(R.drawable.ic_item_selected)
        } else {
            holder.imgStatus.setImageResource(R.drawable.ic_item_unselect)
        }

        // Sự kiện chọn địa chỉ (để quay về giỏ hàng)
        holder.layoutItem.setOnClickListener {
            iClickAddressListener.onClickAddressItem(address)
        }

        // Thêm vào: Gán sự kiện cho nút sửa
        holder.imgEdit.setOnClickListener {
            iClickAddressListener.onClickEditAddress(address)
        }

        // Thêm vào: Gán sự kiện cho nút xóa
        holder.imgDelete.setOnClickListener {
            iClickAddressListener.onClickDeleteAddress(address)
        }
    }

    override fun getItemCount(): Int {
        return listAddress?.size ?: 0
    }

    // Thêm vào: Khai báo ImageView cho nút sửa và xóa
    class AddressViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val layoutItem: LinearLayout = itemView.findViewById(R.id.layout_item)
        val imgStatus: ImageView = itemView.findViewById(R.id.img_status)
        val tvName: TextView = itemView.findViewById(R.id.tv_name)
        val tvPhone: TextView = itemView.findViewById(R.id.tv_phone)
        val tvAddress: TextView = itemView.findViewById(R.id.tv_address)
        val imgEdit: ImageView = itemView.findViewById(R.id.img_edit)
        val imgDelete: ImageView = itemView.findViewById(R.id.img_delete)
    }
}