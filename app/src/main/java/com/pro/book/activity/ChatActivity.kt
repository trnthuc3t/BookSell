package com.pro.book.activity

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
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

class ChatActivity : BaseActivity() {
    private var rcvChat: RecyclerView? = null
    private var edtMessage: EditText? = null
    private var imgSend: ImageView? = null
    private var progressBar: ProgressBar? = null

    private var chatAdapter: ChatAdapter? = null
    private val messages = mutableListOf<Message>()

    private lateinit var generativeModel: GenerativeModel
    private var productData = StringBuilder()
    private var categoryData = StringBuilder()
    private var orderData = StringBuilder()
    private var userData = StringBuilder()

    // Lưu lịch sử chat để AI có context
    private val chatHistory = mutableListOf<com.google.ai.client.generativeai.type.Content>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        initToolbar()
        initUi()
        initGemini()
        loadAllData()
        initListener()

        // Thêm tin nhắn chào mừng
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

    private fun initGemini() {
        // Thay YOUR_API_KEY bằng API key thực của bạn
        generativeModel = GenerativeModel(
            modelName = "gemini-pro",
            apiKey = "AIzaSyDczK3IFO1d9AZ4oHWkopkqA9VzXPh00-w"
        )
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
                    productData.append("=== DANH SÁCH SẢN PHẨM ===\n\n")

                    for (dataSnapshot in snapshot.children) {
                        val product = dataSnapshot.getValue(Product::class.java)
                        product?.let {
                            productData.append("📖 ${it.name}\n")
                            productData.append("   Mô tả: ${it.description}\n")
                            productData.append("   Giá: ${it.realPrice}.000vnd\n")
                            productData.append("   Danh mục: ${it.category_name}\n")
                            if (it.sale > 0) {
                                productData.append("   🔥 Khuyến mãi: ${it.sale}%\n")
                            }
                            productData.append("   Đánh giá: ${it.rate}⭐ (${it.countReviews} reviews)\n")
                            productData.append("\n")
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
                    categoryData.append("=== DANH MỤC SẢN PHẨM ===\n\n")

                    for (dataSnapshot in snapshot.children) {
                        val category = dataSnapshot.getValue(Category::class.java)
                        category?.let {
                            categoryData.append("📂 ${it.name}\n")
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

        // Load user's orders
        get(this).orderDatabaseReference
            .orderByChild("userEmail")
            .equalTo(DataStoreManager.user?.email)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    orderData.clear()
                    orderData.append("=== ĐơN HÀNG CỦA KHÁCH ===\n\n")

                    for (dataSnapshot in snapshot.children) {
                        val order = dataSnapshot.getValue(Order::class.java)
                        order?.let {
                            orderData.append("🛒 Đơn hàng #${it.id}\n")
                            orderData.append("   Ngày: ${it.dateTime}\n")
                            orderData.append("   Tổng tiền: ${it.total}.000vnd\n")
                            orderData.append("   Trạng thái: ${getOrderStatus(it.status)}\n")
                            orderData.append("   Sản phẩm: ${it.listProductsName}\n")
                            orderData.append("\n")
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

        // Load user info
        userData.clear()
        userData.append("=== THÔNG TIN KHÁCH HÀNG ===\n\n")
        userData.append("Email: ${DataStoreManager.user?.email}\n")
        userData.append("\n")

        loadedCount++
        if (loadedCount == totalLoads) showProgressDialog(false)
    }

    private fun getOrderStatus(status: Int): String {
        return when (status) {
            Order.STATUS_NEW -> "Đơn mới"
            Order.STATUS_DOING -> "Đang xử lý"
            Order.STATUS_ARRIVED -> "Đã giao"
            Order.STATUS_COMPLETE -> "Hoàn thành"
            else -> "Không xác định"
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
        // Thêm tin nhắn của user
        addUserMessage(message)
        edtMessage?.setText("")

        // Hiển thị loading
        progressBar?.visibility = View.VISIBLE
        imgSend?.isEnabled = false

        // Gọi Gemini API với chat history
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Thêm tin nhắn user vào history
                chatHistory.add(
                    content(role = "user") {
                        text(message)
                    }
                )

                val prompt = buildSystemPrompt() + "\n\nCâu hỏi: $message"

                val response = generativeModel.generateContent(prompt)
                val botReply = response.text ?: "Xin lỗi, tôi không thể trả lời câu hỏi này."

                // Thêm câu trả lời bot vào history
                chatHistory.add(
                    content(role = "model") {
                        text(botReply)
                    }
                )

                withContext(Dispatchers.Main) {
                    addBotMessage(botReply)
                    progressBar?.visibility = View.GONE
                    imgSend?.isEnabled = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    addBotMessage("Xin lỗi, đã có lỗi xảy ra: ${e.message}. Vui lòng thử lại.")
                    progressBar?.visibility = View.GONE
                    imgSend?.isEnabled = true
                }
            }
        }
    }

    private fun buildSystemPrompt(): String {
        return """
Bạn là trợ lý ảo thông minh của cửa hàng sách trực tuyến. Nhiệm vụ của bạn là hỗ trợ khách hàng một cách chuyên nghiệp, thân thiện và hữu ích.

$userData

$categoryData

$productData

$orderData

HƯỚNG DẪN TRẢ LỜI:
1. Luôn trả lời bằng tiếng Việt
2. Thân thiện, lịch sự và chuyên nghiệp
3. Sử dụng emoji phù hợp để tạo sự thân thiện 😊
4. Khi được hỏi về sản phẩm:
   - Đề xuất sản phẩm phù hợp dựa trên nhu cầu
   - Nêu rõ giá, khuyến mãi, đánh giá
   - So sánh các sản phẩm nếu khách yêu cầu
5. Khi được hỏi về đơn hàng:
   - Cung cấp thông tin chi tiết về trạng thái đơn
   - Giải thích quy trình giao hàng
6. Nếu không có thông tin trong dữ liệu:
   - Thừa nhận lịch sự
   - Đề xuất các sản phẩm/dịch vụ liên quan
7. Trả lời ngắn gọn, dễ hiểu, có cấu trúc rõ ràng
8. Chủ động hỏi thêm để hiểu rõ nhu cầu khách hàng
9. Luôn kết thúc bằng câu hỏi/gợi ý để tiếp tục cuộc trò chuyện

LƯU Ý ĐẶC BIỆT:
- Không bịa đặt thông tin không có trong dữ liệu
- Ưu tiên sản phẩm đang có khuyến mãi
- Giới thiệu sản phẩm có đánh giá cao
- Hỗ trợ khách tìm sản phẩm phù hợp với ngân sách
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
            rcvChat?.scrollToPosition(messages.size - 1)
        }, 100)
    }
}