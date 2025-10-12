package com.pro.book.activity

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.pro.book.MyApplication.Companion.get
import com.pro.book.R
import com.pro.book.adapter.ChatAdapter
import com.pro.book.model.Category
import com.pro.book.model.Message
import com.pro.book.model.Order
import com.pro.book.model.Product
import com.pro.book.prefs.DataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ChatActivity : BaseActivity() {
    private var rcvChat: RecyclerView? = null
    private var edtMessage: EditText? = null
    private var imgSend: ImageView? = null
    private var progressBar: ProgressBar? = null

    private var chatAdapter: ChatAdapter? = null
    private val messages = mutableListOf<Message>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

//    ApiKey Gemni
    private val apiKey = ""

    private var productData = StringBuilder()
    private var categoryData = StringBuilder()
    private var orderData = StringBuilder()
    private var userData = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        initToolbar()
        initUi()
        loadAllData()
        initListener()

        addBotMessage("Xin chào ${DataStoreManager.user?.email}! 👋\n\nTôi là trợ lý ảo của cửa hàng sách. Tôi có thể giúp bạn:\n\n📚 Tìm kiếm và tư vấn sản phẩm\n📦 Kiểm tra đơn hàng\n💰 Xem khuyến mãi\n❓ Trả lời các câu hỏi\n\nBạn cần giúp gì không?")
    }

    private fun initToolbar() {
        val imgToolbarBack = findViewById<ImageView>(R.id.img_toolbar_back)
        val tvToolbarTitle = findViewById<TextView>(R.id.tv_toolbar_title)
        imgToolbarBack.setOnClickListener { finish() }
        tvToolbarTitle.text = getString(R.string.chat_assistant)
    }

    private fun initUi() {
        rcvChat = findViewById(R.id.rcv_chat)
        edtMessage = findViewById(R.id.edt_message)
        imgSend = findViewById(R.id.img_send)
        progressBar = findViewById(R.id.progress_bar)

        val layoutManager = LinearLayoutManager(this)
        rcvChat?.layoutManager = layoutManager

        chatAdapter = ChatAdapter(messages)
        rcvChat?.adapter = chatAdapter
    }

    private fun loadAllData() {
        showProgressDialog(true)
        var loadedCount = 0
        val totalLoads = 4

        // Load products
        get(this).productDatabaseReference
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    productData.clear()
                    productData.append("=== SẢN PHẨM ===\n\n")

                    var count = 0
                    for (dataSnapshot in snapshot.children) {
                        if (count >= 15) break

                        val product = dataSnapshot.getValue(Product::class.java)
                        product?.let {
                            productData.append("${count + 1}. ${it.name}\n")
                            productData.append("   Giá: ${it.realPrice}k")
                            if (it.sale > 0) {
                                productData.append(" (Giảm ${it.sale}%)")
                            }
                            productData.append("\n")
                            productData.append("   Danh mục: ${it.category_name}\n\n")
                            count++
                        }
                    }

                    loadedCount++
                    if (loadedCount == totalLoads) showProgressDialog(false)
                }

                override fun onCancelled(error: DatabaseError) {
                    loadedCount++
                    if (loadedCount == totalLoads) showProgressDialog(false)
                }
            })

        // Load categories
        get(this).categoryDatabaseReference
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    categoryData.clear()
                    categoryData.append("=== DANH MỤC ===\n")

                    for (dataSnapshot in snapshot.children) {
                        val category = dataSnapshot.getValue(Category::class.java)
                        category?.let {
                            categoryData.append("- ${it.name}\n")
                        }
                    }
                    categoryData.append("\n")

                    loadedCount++
                    if (loadedCount == totalLoads) showProgressDialog(false)
                }

                override fun onCancelled(error: DatabaseError) {
                    loadedCount++
                    if (loadedCount == totalLoads) showProgressDialog(false)
                }
            })

        // Load orders
        get(this).orderDatabaseReference
            .orderByChild("userEmail")
            .equalTo(DataStoreManager.user?.email)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    orderData.clear()
                    orderData.append("=== ĐƠN HÀNG ===\n\n")

                    var count = 0
                    for (dataSnapshot in snapshot.children) {
                        val order = dataSnapshot.getValue(Order::class.java)
                        order?.let {
                            orderData.append("Đơn #${it.id}\n")
                            orderData.append("- Tổng: ${it.total}k\n")
                            orderData.append("- Trạng thái: ${getOrderStatus(it.status)}\n\n")
                            count++
                        }
                    }

                    if (count == 0) {
                        orderData.append("Chưa có đơn hàng.\n")
                    }

                    loadedCount++
                    if (loadedCount == totalLoads) showProgressDialog(false)
                }

                override fun onCancelled(error: DatabaseError) {
                    loadedCount++
                    if (loadedCount == totalLoads) showProgressDialog(false)
                }
            })

        // User info
        userData.clear()
        userData.append("Khách: ${DataStoreManager.user?.email}\n\n")

        loadedCount++
        if (loadedCount == totalLoads) showProgressDialog(false)
    }

    private fun getOrderStatus(status: Int): String {
        return when (status) {
            Order.STATUS_NEW -> "Mới"
            Order.STATUS_DOING -> "Đang xử lý"
            Order.STATUS_ARRIVED -> "Đã giao"
            Order.STATUS_COMPLETE -> "Hoàn thành"
            else -> "Không rõ"
        }
    }

    private fun initListener() {
        imgSend?.setOnClickListener {
            val message = edtMessage?.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
            }
        }
    }

    private fun sendMessage(message: String) {
        addUserMessage(message)
        edtMessage?.setText("")

        progressBar?.visibility = View.VISIBLE
        imgSend?.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prompt = buildPrompt(message)
                val response = callGeminiAPI(prompt)

                withContext(Dispatchers.Main) {
                    addBotMessage(response)
                    progressBar?.visibility = View.GONE
                    imgSend?.isEnabled = true
                }

            } catch (e: Exception) {
                e.printStackTrace()

                withContext(Dispatchers.Main) {
                    val errorMsg = when {
                        e.message?.contains("API key") == true -> {
                            "❌ API key không hợp lệ.\n\nVui lòng lấy key mới tại:\nhttps://aistudio.google.com/app/apikey"
                        }
                        e.message?.contains("403") == true -> {
                            "🔒 API key không có quyền.\n\nVui lòng enable Gemini API trong project."
                        }
                        e.message?.contains("429") == true -> {
                            "⏰ Vượt giới hạn (60/phút).\n\nVui lòng đợi 1 phút."
                        }
                        e.message?.contains("timeout") == true -> {
                            "⏱️ Request timeout.\n\nVui lòng thử lại."
                        }
                        else -> {
                            "⚠️ Lỗi: ${e.message}\n\nVui lòng thử lại."
                        }
                    }

                    addBotMessage(errorMsg)
                    progressBar?.visibility = View.GONE
                    imgSend?.isEnabled = true
                }
            }
        }
    }

    private fun callGeminiAPI(prompt: String): String {
        // URL mới với API version v1 (không phải v1beta)
        val url = "https://generativelanguage.googleapis.com/v1/models/gemini-pro:generateContent?key=$apiKey"

        // Tạo JSON request
        val jsonRequest = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("topK", 40)
                put("topP", 0.95)
                put("maxOutputTokens", 1024)
            })
        }

        val requestBody = jsonRequest.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()

        if (!response.isSuccessful) {
            throw Exception("API Error ${response.code}: $responseBody")
        }

        // Parse response
        val jsonResponse = JSONObject(responseBody ?: "")
        val candidates = jsonResponse.optJSONArray("candidates")

        if (candidates != null && candidates.length() > 0) {
            val content = candidates.getJSONObject(0)
                .getJSONObject("content")
            val parts = content.getJSONArray("parts")

            if (parts.length() > 0) {
                return parts.getJSONObject(0).getString("text")
            }
        }

        return "Xin lỗi, tôi không thể trả lời câu hỏi này."
    }

    private fun buildPrompt(userMessage: String): String {
        return """
Bạn là trợ lý cửa hàng sách. Trả lời ngắn gọn, thân thiện bằng tiếng Việt.

$userData
$categoryData
$productData
$orderData

HƯỚNG DẪN:
- Trả lời ngắn gọn, tối đa 3-4 câu
- Dùng emoji phù hợp
- Ưu tiên sản phẩm giảm giá
- Nếu không rõ, gợi ý sản phẩm khác

Câu hỏi: $userMessage
        """.trimIndent()
    }

    private fun addUserMessage(message: String) {
        val msg = Message(message, true)
        chatAdapter?.addMessage(msg)
        scrollToBottom()
    }

    private fun addBotMessage(message: String) {
        val msg = Message(message, false)
        chatAdapter?.addMessage(msg)
        scrollToBottom()
    }

    private fun scrollToBottom() {
        rcvChat?.postDelayed({
            if (messages.isNotEmpty()) {
                rcvChat?.scrollToPosition(messages.size - 1)
            }
        }, 100)
    }
}