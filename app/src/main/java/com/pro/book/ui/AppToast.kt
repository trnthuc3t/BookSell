package com.pro.book.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.pro.book.R

object AppToast {

    private var current: Toast? = null

    /** Gọi với message từ string resources */
    @JvmOverloads
    fun show(
        context: Context,
        @StringRes messageRes: Int,
        @DrawableRes iconRes: Int? = null,
        @ColorRes bgColorRes: Int? = null,
        duration: Int = Toast.LENGTH_SHORT,
        bottomMarginDp: Int = 72
    ) {
        val msg = context.getString(messageRes)
        val colorInt = bgColorRes?.let { ContextCompat.getColor(context, it) }
        showInternal(context, msg, iconRes, colorInt, duration, bottomMarginDp)
    }

    /** Gọi với message là CharSequence tùy ý */
    @JvmOverloads
    fun showText(
        context: Context,
        text: CharSequence,
        @DrawableRes iconRes: Int? = null,
        @ColorInt bgColor: Int? = null,
        duration: Int = Toast.LENGTH_SHORT,
        bottomMarginDp: Int = 72
    ) {
        showInternal(context, text, iconRes, bgColor, duration, bottomMarginDp)
    }

    fun cancel() { current?.cancel(); current = null }

    // ---- Internal ----
    private fun showInternal(
        context: Context,
        text: CharSequence,
        @DrawableRes iconRes: Int?,
        @ColorInt bgColor: Int?,
        duration: Int,
        bottomMarginDp: Int
    ) {
        current?.cancel()

        val v = LayoutInflater.from(context).inflate(R.layout.view_toast_pill, null)
        val root = v.findViewById<View>(R.id.toast_root)
        val tv = v.findViewById<TextView>(R.id.toast_text)
        val iv = v.findViewById<ImageView>(R.id.toast_icon)

        tv.text = text

        // set icon (nếu có)
        if (iconRes != null) {
            iv.setImageResource(iconRes)
            iv.visibility = View.VISIBLE
        } else {
            iv.visibility = View.GONE
        }

        // set background color runtime từ shape
        (ContextCompat.getDrawable(context, R.drawable.bg_toast_pill) as? GradientDrawable)?.let { gd ->
            val color = bgColor ?: ContextCompat.getColor(context, R.color.toast_info)
            gd.mutate()
            gd.setColor(color)
            root.background = gd
        }

        val t = Toast(context.applicationContext).apply {
            this.duration = duration
            this.view = v
            val yOffset = dp(context, bottomMarginDp) + navBarInsetBottom(context)
            setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, yOffset)
        }
        t.show()
        current = t
    }

    private fun dp(ctx: Context, value: Int): Int {
        return (value * ctx.resources.displayMetrics.density).toInt()
    }

    private fun navBarInsetBottom(ctx: Context): Int {
        return if (Build.VERSION.SDK_INT >= 30) {
            val wm = ctx.getSystemService(WindowManager::class.java)
            val insets = wm.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
            insets.bottom
        } else 0
    }
}
