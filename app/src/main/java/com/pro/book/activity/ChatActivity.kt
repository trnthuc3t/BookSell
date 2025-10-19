package com.pro.book.activity

import android.content.SharedPreferences
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
import com.pro.book.model.Address
import com.pro.book.model.Category
import com.pro.book.model.Message
import com.pro.book.model.Order
import com.pro.book.model.Product
import com.pro.book.model.ProductOrder
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
import java.io.File
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

    val geminiApiKey: String
        get() {
            val properties = java.util.Properties()
            val file = File("${System.getProperty("user.dir")}/local.properties")
            if (file.exists()) {
                properties.load(file.inputStream())
                return properties.getProperty("GEMINI_API_KEY", "")
            }
            return ""
        }


    // Enhanced data storage
    private var productData = StringBuilder()
    private var categoryData = StringBuilder()
    private var orderData = StringBuilder()
    private var userData = StringBuilder()
    private var analyticsData = StringBuilder()
    private var feedbackData = StringBuilder()
    private var voucherData = StringBuilder()
    
    private lateinit var sharedPreferences: SharedPreferences
    private val chatHistoryKey = "chat_history_${DataStoreManager.user?.email}"
    
    // Data analysisk
    private val productSalesMap = mutableMapOf<Long, Int>()
    private val productRatingMap = mutableMapOf<Long, Double>()
    private val categoryStats = mutableMapOf<String, Int>()
    
    // Book content storage
    private val bookContentMap = mutableMapOf<Long, String>()
    
    // Order creation state
    private var isCreatingOrder = false
    private var orderStep = 0
    private var customerName = ""
    private var customerPhone = ""
    private var customerAddress = ""
    private var selectedProducts = mutableListOf<Pair<Product, Int>>() // Product and quantity
    private var selectedVoucher: com.pro.book.model.Voucher? = null
    private var availableProductsList = mutableListOf<Product>()
    

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        initToolbar()
        initUi()
        initSharedPreferences()
        loadChatHistory()
        loadAllData()
        initListener()

        // Only show welcome message if chat history is empty
        if (messages.isEmpty()) {
            addBotMessage("Xin chào ${DataStoreManager.user?.email}! 👋\n\nTôi là trợ lý ảo thông minh của cửa hàng sách. Tôi có thể giúp bạn:\n\n📚 Tìm kiếm và tư vấn sản phẩm\n📦 Kiểm tra đơn hàng\n🛒 Đặt hàng trực tiếp qua chat\n💰 Xem khuyến mãi và voucher\n📊 Phân tích sản phẩm bán chạy\n⭐ Xem đánh giá và phản hồi\n❓ Trả lời các câu hỏi\n\n🛒 ĐẶT HÀNG QUA CHAT:\n• Nói 'đặt hàng' hoặc 'mua sách'\n• Tôi sẽ hướng dẫn từng bước:\n  - Họ tên\n  - Số điện thoại\n  - Địa chỉ giao hàng\n  - Chọn sách\n  - Số lượng\n  - Voucher (nếu có)\n• Đơn hàng sẽ tự động tạo với trạng thái 'Đang xử lý'\n\nBạn cần giúp gì không?")
        }
        
        // Test order creation (remove this in production)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (availableProductsList.isNotEmpty()) {
                android.util.Log.d("ChatActivity", "Available products: ${availableProductsList.size}")
            }
        }, 2000)
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

    private fun initSharedPreferences() {
        sharedPreferences = getSharedPreferences("chat_history", MODE_PRIVATE)
    }

    private fun loadChatHistory() {
        val historyJson = sharedPreferences.getString(chatHistoryKey, null)
        if (historyJson != null) {
            try {
                val historyArray = JSONArray(historyJson)
                for (i in 0 until historyArray.length()) {
                    val messageObj = historyArray.getJSONObject(i)
                    val content = messageObj.getString("content")
                    val isFromUser = messageObj.getBoolean("isFromUser")
                    val timestamp = messageObj.getLong("timestamp")
                    
                    val message = Message(content, isFromUser, timestamp)
                    messages.add(message)
                }
                chatAdapter?.notifyDataSetChanged()
                scrollToBottom()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveChatHistory() {
        try {
            val historyArray = JSONArray()
            for (message in messages) {
                val messageObj = JSONObject()
                messageObj.put("content", message.content)
                messageObj.put("isFromUser", message.isFromUser)
                messageObj.put("timestamp", message.timestamp)
                historyArray.put(messageObj)
            }
            
            sharedPreferences.edit()
                .putString(chatHistoryKey, historyArray.toString())
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadAllData() {
        showProgressDialog(true)
        
        // Simple approach: Load data with timeout
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            showProgressDialog(false)
        }, 3000) // 3 seconds timeout

        // Load products with enhanced data
        get(this).productDatabaseReference
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    productData.clear()
                    productData.append("=== SẢN PHẨM ===\n\n")

                    val allProducts = mutableListOf<Product>()
                    for (dataSnapshot in snapshot.children) {
                        val product = dataSnapshot.getValue(Product::class.java)
                        product?.let { allProducts.add(it) }
                    }

                    // Sort products by rating and sales
                    val sortedProducts = allProducts.sortedWith(compareByDescending<Product> { it.rate }
                        .thenByDescending { it.sale })

                    var count = 0
                    for (product in sortedProducts) {
                        if (count >= 20) break // Increased from 15 to 20

                        productData.append("${count + 1}. ${product.name}\n")
                        productData.append("   Giá: ${product.realPrice}k")
                        if (product.sale > 0) {
                            productData.append(" (Giảm ${product.sale}%)")
                        }
                        productData.append("\n")
                        productData.append("   Danh mục: ${product.category_name}\n")
                        productData.append("   Đánh giá: ${product.rate}⭐ (${product.countReviews} đánh giá)\n")
                        if (product.isFeatured) {
                            productData.append("   ⭐ SẢN PHẨM NỔI BẬT\n")
                        }
                        
                        // Thêm mô tả sách
                        if (!product.description.isNullOrEmpty()) {
                            productData.append("   📖 Mô tả: ${product.description}\n")
                        }
                        
                        // Thêm thông tin chi tiết sách
                        if (!product.info.isNullOrEmpty()) {
                            productData.append("   📚 Nội dung: ${product.info}\n")
                        }
                        
                        productData.append("\n")
                        
                        // Store for analytics
                        productRatingMap[product.id] = product.rate
                        categoryStats[product.category_name ?: ""] = (categoryStats[product.category_name ?: ""] ?: 0) + 1
                        
                        // Store book content for detailed queries
                        val bookContent = StringBuilder()
                        if (!product.description.isNullOrEmpty()) {
                            bookContent.append("Mô tả: ${product.description}\n")
                        }
                        if (!product.info.isNullOrEmpty()) {
                            bookContent.append("Nội dung: ${product.info}\n")
                        }
                        if (bookContent.isNotEmpty()) {
                            bookContentMap[product.id] = bookContent.toString()
                        }
                        
                        // Store product for order creation
                        availableProductsList.add(product)
                        
                        count++
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle error silently
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
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle error silently
                }
            })

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
                            orderData.append("- Trạng thái: ${getOrderStatus(it.status)}\n")
                            orderData.append("- Ngày đặt: ${it.dateTime}\n")
                            orderData.append("- Phương thức thanh toán: ${it.paymentMethod}\n")
                            
                            // Chi tiết sản phẩm trong đơn hàng
                            if (!it.products.isNullOrEmpty()) {
                                orderData.append("- Sản phẩm đã mua:\n")
                                for (product in it.products!!) {
                                    orderData.append("  • ${product.name} (SL: ${product.count}, Giá: ${product.price}k)\n")
                                }
                            }
                            
                            // Thông tin địa chỉ
                            it.address?.let { address ->
                                orderData.append("- Địa chỉ giao hàng:\n")
                                orderData.append("  • Tên: ${address.name}\n")
                                orderData.append("  • SĐT: ${address.phone}\n")
                                orderData.append("  • Địa chỉ: ${address.address}\n")
                            }
                            
                            orderData.append("\n")
                            count++
                        }
                    }

                    if (count == 0) {
                        orderData.append("Chưa có đơn hàng.\n")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle error silently
                }
            })

        // Load vouchers
        get(this).voucherDatabaseReference
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    voucherData.clear()
                    voucherData.append("=== VOUCHER & KHUYẾN MÃI ===\n\n")

                    var count = 0
                    for (dataSnapshot in snapshot.children) {
                        if (count >= 10) break
                        
                        val voucher = dataSnapshot.getValue(com.pro.book.model.Voucher::class.java)
                        voucher?.let {
                            voucherData.append("${count + 1}. ${it.name}\n")
                            voucherData.append("   Giảm giá: ${it.discount}%\n")
                            voucherData.append("   Mô tả: ${it.description}\n\n")
                            count++
                        }
                    }

                    if (count == 0) {
                        voucherData.append("Hiện tại chưa có voucher nào.\n")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle error silently
                }
            })

        // Load feedback
        get(this).feedbackDatabaseReference
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    feedbackData.clear()
                    feedbackData.append("=== PHẢN HỒI KHÁCH HÀNG ===\n\n")

                    var count = 0
                    for (dataSnapshot in snapshot.children) {
                        if (count >= 10) break
                        
                        val feedback = dataSnapshot.getValue(com.pro.book.model.Feedback::class.java)
                        feedback?.let {
                            feedbackData.append("${count + 1}. ${it.content}\n")
                            feedbackData.append("   Đánh giá: ${it.rate}⭐\n")
                            feedbackData.append("   Từ: ${it.userEmail}\n\n")
                            count++
                        }
                    }

                    if (count == 0) {
                        feedbackData.append("Chưa có phản hồi nào.\n")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle error silently
                }
            })

        // Load analytics data
        loadAnalyticsData()

        // User info
        userData.clear()
        userData.append("Khách hàng: ${DataStoreManager.user?.email}\n\n")
    }

    private fun loadAnalyticsData() {
        analyticsData.clear()
        analyticsData.append("=== PHÂN TÍCH DỮ LIỆU ===\n\n")
        
        generateAnalyticsSummary()
    }

    private fun generateAnalyticsSummary() {
        analyticsData.append(getProductAnalyticsSummary())
    }

    private fun getOrderStatus(status: Int): String {
        return when (status) {
            Order.STATUS_NEW -> "Mới"
            Order.STATUS_DOING -> "Đang xử lý"
            Order.STATUS_ARRIVED -> "Đã giao"
            Order.STATUS_COMPLETE -> "Hoàn thành"
            Order.STATUS_CANCELLED -> "Đã hủy"
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

        // Check if user wants to create order
        if (message.contains("đặt hàng", ignoreCase = true) || 
            message.contains("tạo đơn", ignoreCase = true) ||
            message.contains("mua sách", ignoreCase = true)) {
            startOrderCreation()
            return
        }

        // Check if we're in order creation mode
        if (isCreatingOrder) {
            processOrderStep(message)
            return
        }

        progressBar?.visibility = View.VISIBLE
        imgSend?.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = callGeminiAPI(message)

                withContext(Dispatchers.Main) {
                    addBotMessage(response)
                    progressBar?.visibility = View.GONE
                    imgSend?.isEnabled = true
                }

            } catch (e: Exception) {
                e.printStackTrace()

                withContext(Dispatchers.Main) {
                    val errorMsg = when {
                        e.message?.contains("API_KEY_INVALID") == true ||
                                e.message?.contains("API key not valid") == true ||
                                e.message?.contains("400") == true -> {
                            "❌ API key không hợp lệ!\n\nVui lòng:\n1. Vào https://aistudio.google.com/app/apikey\n2. Tạo key mới\n3. Paste vào ChatActivity.kt (dòng 35)"
                        }
                        e.message?.contains("RESOURCE_EXHAUSTED") == true ||
                                e.message?.contains("429") == true -> {
                            "⏰ Vượt giới hạn 60 requests/phút.\n\nĐợi 1 phút nhé!"
                        }
                        e.message?.contains("PERMISSION_DENIED") == true ||
                                e.message?.contains("403") == true -> {
                            "🔒 API key không có quyền.\n\nEnable Generative Language API trong Google Cloud Console."
                        }
                        e.message?.contains("timeout") == true -> {
                            "⏱️ Timeout. Thử lại nhé!"
                        }
                        e.message?.contains("Unable to resolve host") == true -> {
                            "📡 Không có mạng. Kiểm tra WiFi/Data!"
                        }
                        else -> {
                            "⚠️ Lỗi: ${e.message}\n\nThử lại nhé!"
                        }
                    }

                    addBotMessage(errorMsg)
                    progressBar?.visibility = View.GONE
                    imgSend?.isEnabled = true
                }
            }
        }
    }

    private fun callGeminiAPI(userMessage: String): String {

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

        val prompt = buildPrompt(userMessage)

        // Body giống Postman
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
        }

        val requestBody = jsonRequest.toString()
            .toRequestBody("application/json".toMediaType())

        // Quan trọng: Dùng header x-goog-api-key thay vì query parameter
        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", geminiApiKey)  // ← Theo hướng dẫn Google
            .addHeader("Content-Type", "application/json")
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
        // Check if user is asking about book content
        val bookContentInfo = if (userMessage.contains("nội dung", ignoreCase = true) || 
                                  userMessage.contains("mô tả", ignoreCase = true) ||
                                  userMessage.contains("giới thiệu", ignoreCase = true) ||
                                  userMessage.contains("tóm tắt", ignoreCase = true)) {
            "\n=== NỘI DUNG SÁCH CHI TIẾT ===\n\n" + 
            bookContentMap.values.joinToString("\n---\n")
        } else ""

        return """
Bạn là trợ lý ảo thông minh của cửa hàng sách trực tuyến.
Trả lời bằng tiếng Việt, ngắn gọn (2-3 câu), thân thiện.

$userData
$categoryData
$productData
$orderData
$voucherData
$feedbackData
$analyticsData
$bookContentInfo

HƯỚNG DẪN:
- Trả lời ngắn gọn, dễ hiểu
- Dùng emoji phù hợp 😊
- Ưu tiên sản phẩm có khuyến mãi
- Gợi ý sản phẩm phù hợp với nhu cầu
- Có thể phân tích sản phẩm bán chạy/ít bán (không nói số lượng cụ thể)
- Xem được voucher và khuyến mãi hiện có
- Đọc được phản hồi khách hàng
- CÓ THỂ TRẢ LỜI CHI TIẾT VỀ ĐƠN HÀNG:
  • Sách nào đã mua trong đơn hàng
  • Địa chỉ giao hàng (tên, SĐT, địa chỉ)
  • Trạng thái đơn hàng
  • Phương thức thanh toán
  • Ngày đặt hàng
- CÓ THỂ ĐỌC NỘI DUNG SÁCH:
  • Mô tả chi tiết của sách
  • Nội dung, tóm tắt sách
  • Thông tin về tác giả, nhà xuất bản
  • Giới thiệu về cuốn sách
  • Trả lời câu hỏi về nội dung sách
- CÓ THỂ TẠO ĐƠN HÀNG:
  • Khi người dùng nói "đặt hàng", "tạo đơn", "mua sách"
  • Hướng dẫn từng bước: họ tên, SĐT, chọn sách, số lượng, voucher
  • Tạo đơn hàng với phương thức thanh toán tiền mặt
- CÓ THỂ HỦY ĐƠN HÀNG (CHỈ ADMIN):
  • Chỉ hủy được đơn hàng ở trạng thái "Mới" (giai đoạn 1)
  • Không hủy được đơn hàng thanh toán bằng ZaloPay
  • Các phương thức khác: tiền mặt, GoPay, thẻ tín dụng, chuyển khoản có thể hủy
- Nếu không có thông tin, thừa nhận lịch sự

Câu hỏi: $userMessage
        """.trimIndent()
    }

    private fun addUserMessage(message: String) {
        val msg = Message(message, true)
        chatAdapter?.addMessage(msg)
        saveChatHistory()
        scrollToBottom()
    }

    private fun addBotMessage(message: String) {
        val msg = Message(message, false)
        chatAdapter?.addMessage(msg)
        saveChatHistory()
        scrollToBottom()
    }

    private fun scrollToBottom() {
        rcvChat?.postDelayed({
            if (messages.isNotEmpty()) {
                rcvChat?.scrollToPosition(messages.size - 1)
            }
        }, 100)
    }

    // Method to clear chat history
    private fun clearChatHistory() {
        messages.clear()
        chatAdapter?.notifyDataSetChanged()
        sharedPreferences.edit()
            .remove(chatHistoryKey)
            .apply()
        
        addBotMessage("Lịch sử chat đã được xóa! 👋\n\nTôi là trợ lý ảo thông minh của cửa hàng sách. Tôi có thể giúp bạn:\n\n📚 Tìm kiếm và tư vấn sản phẩm\n📦 Kiểm tra đơn hàng\n💰 Xem khuyến mãi và voucher\n📊 Phân tích sản phẩm bán chạy\n⭐ Xem đánh giá và phản hồi\n❓ Trả lời các câu hỏi\n\nBạn cần giúp gì không?")
    }

    // Method to get product analytics summary
    private fun getProductAnalyticsSummary(): String {
        val summary = StringBuilder()
        summary.append("📊 PHÂN TÍCH SẢN PHẨM:\n\n")
        
        // Top rated products
        val topRated = productRatingMap.toList()
            .sortedByDescending { it.second }
            .take(3)
        
        if (topRated.isNotEmpty()) {
            summary.append("⭐ SẢN PHẨM ĐƯỢC ĐÁNH GIÁ CAO:\n")
            for ((productId, rating) in topRated) {
                summary.append("- Sản phẩm ID $productId: ${String.format("%.1f", rating)}⭐\n")
            }
            summary.append("\n")
        }
        
        // Category distribution
        if (categoryStats.isNotEmpty()) {
            summary.append("📈 PHÂN BỐ DANH MỤC:\n")
            val sortedCategories = categoryStats.toList().sortedByDescending { it.second }
            for ((category, count) in sortedCategories.take(5)) {
                summary.append("- $category: $count sản phẩm\n")
            }
            summary.append("\n")
        }
        
        // Sales insights (without specific numbers)
        if (productSalesMap.isNotEmpty()) {
            summary.append("🔥 SẢN PHẨM BÁN CHẠY:\n")
            summary.append("- Các sản phẩm được khách hàng yêu thích\n")
            summary.append("- Sản phẩm có doanh số cao\n")
            summary.append("- Sản phẩm được đặt hàng nhiều nhất\n\n")
        }
        
        return summary.toString()
    }

    // Method to load detailed book content
    private fun loadBookContent(productId: Long) {
        get(this).getProductDetailDatabaseReference(productId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val product = snapshot.getValue(Product::class.java)
                    product?.let {
                        val bookContent = StringBuilder()
                        if (!it.description.isNullOrEmpty()) {
                            bookContent.append("📖 Mô tả: ${it.description}\n\n")
                        }
                        if (!it.info.isNullOrEmpty()) {
                            bookContent.append("📚 Nội dung chi tiết: ${it.info}\n\n")
                        }
                        if (bookContent.isNotEmpty()) {
                            bookContentMap[productId] = bookContent.toString()
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle error silently
                }
            })
    }

    // Method to get book content by name
    private fun getBookContentByName(bookName: String): String {
        for ((productId, content) in bookContentMap) {
            if (content.contains(bookName, ignoreCase = true)) {
                return content
            }
        }
        return ""
    }

    // Order creation methods
    private fun startOrderCreation() {
        isCreatingOrder = true
        orderStep = 1
        selectedProducts.clear()
        selectedVoucher = null
        
        addBotMessage("🛒 Bắt đầu tạo đơn hàng!\n\nBước 1: Vui lòng cho tôi biết họ tên của bạn:")
    }

    private fun processOrderStep(userMessage: String) {
        // Debug: Log current step
        android.util.Log.d("ChatActivity", "Processing order step: $orderStep, message: $userMessage")
        
        when (orderStep) {
            1 -> processCustomerName(userMessage)
            2 -> processCustomerPhone(userMessage)
            3 -> processCustomerAddress(userMessage)
            4 -> processProductSelection(userMessage)
            5 -> processQuantitySelection(userMessage)
            6 -> processVoucherSelection(userMessage)
            7 -> {
                if (userMessage.contains("có", ignoreCase = true)) {
                    createOrder()
                } else if (userMessage.contains("không", ignoreCase = true)) {
                    addBotMessage("❌ Đã hủy tạo đơn hàng.")
                    resetOrderState()
                } else {
                    addBotMessage("❌ Vui lòng trả lời 'có' hoặc 'không':")
                }
            }
        }
    }

    private fun processCustomerName(message: String) {
        if (message.trim().isNotEmpty()) {
            customerName = message.trim()
            orderStep = 2
            addBotMessage("✅ Đã lưu tên: $customerName\n\nBước 2: Vui lòng cho tôi biết số điện thoại của bạn:")
        } else {
            addBotMessage("❌ Vui lòng nhập họ tên hợp lệ:")
        }
    }

    private fun processCustomerPhone(message: String) {
        val phone = message.trim()
        if (phone.matches(Regex("^[0-9]{10,11}$"))) {
            customerPhone = phone
            orderStep = 3
            addBotMessage("✅ Đã lưu SĐT: $customerPhone\n\nBước 3: Vui lòng cho tôi biết địa chỉ giao hàng của bạn:")
        } else {
            addBotMessage("❌ Số điện thoại không hợp lệ. Vui lòng nhập số điện thoại 10-11 chữ số:")
        }
    }

    private fun processCustomerAddress(message: String) {
        if (message.trim().isNotEmpty()) {
            customerAddress = message.trim()
            orderStep = 4
            addBotMessage("✅ Đã lưu địa chỉ: $customerAddress\n\n")
            showProductList()
        } else {
            addBotMessage("❌ Vui lòng nhập địa chỉ hợp lệ:")
        }
    }

    private fun showProductList() {
        val productList = StringBuilder()
        productList.append("📚 Danh sách sản phẩm có sẵn:\n\n")
        
        for ((index, product) in availableProductsList.withIndex()) {
            productList.append("${index + 1}. ${product.name} - Giá: ${product.realPrice}k")
            if (product.sale > 0) {
                productList.append(" (Giảm ${product.sale}%)")
            }
            productList.append("\n")
        }
        
        productList.append("\nBước 4: Vui lòng chọn sản phẩm bằng cách nhập số thứ tự (ví dụ: 1, 2, 3):")
        addBotMessage(productList.toString())
    }

    private fun processProductSelection(message: String) {
        try {
            val productNumbers = message.split(",").map { it.trim().toInt() }
            val availableProducts = getAvailableProducts()
            
            selectedProducts.clear() // Clear previous selections
            
            for (number in productNumbers) {
                if (number in 1..availableProducts.size) {
                    selectedProducts.add(Pair(availableProducts[number - 1], 1))
                }
            }
            
            if (selectedProducts.isNotEmpty()) {
                orderStep = 5
                showQuantitySelection()
            } else {
                addBotMessage("❌ Vui lòng chọn ít nhất 1 sản phẩm hợp lệ:")
            }
        } catch (e: Exception) {
            addBotMessage("❌ Vui lòng nhập số thứ tự sản phẩm hợp lệ (ví dụ: 1, 2, 3):")
        }
    }

    private fun getAvailableProducts(): List<Product> {
        return availableProductsList
    }

    private fun showQuantitySelection() {
        val quantityText = StringBuilder()
        quantityText.append("📦 Sản phẩm đã chọn:\n\n")
        
        for ((index, productPair) in selectedProducts.withIndex()) {
            quantityText.append("${index + 1}. ${productPair.first.name} - Số lượng: ${productPair.second}\n")
        }
        
        quantityText.append("\nBước 5: Vui lòng nhập số lượng cho từng sản phẩm (ví dụ: 1,2,1):")
        addBotMessage(quantityText.toString())
    }

    private fun processQuantitySelection(message: String) {
        try {
            val quantities = message.split(",").map { it.trim().toInt() }
            
            if (quantities.size == selectedProducts.size) {
                for ((index, quantity) in quantities.withIndex()) {
                    if (quantity > 0) {
                        selectedProducts[index] = Pair(selectedProducts[index].first, quantity)
                    }
                }
                
                orderStep = 6
                showVoucherSelection()
            } else {
                addBotMessage("❌ Số lượng không khớp với số sản phẩm. Vui lòng nhập lại:")
            }
        } catch (e: Exception) {
            addBotMessage("❌ Vui lòng nhập số lượng hợp lệ (ví dụ: 1,2,1):")
        }
    }

    private fun showVoucherSelection() {
        val voucherText = StringBuilder()
        voucherText.append("🎫 Khuyến mãi có sẵn:\n\n")
        
        // Load vouchers from voucherData
        val voucherLines = voucherData.toString().split("\n")
        var count = 1
        for (line in voucherLines) {
            if (line.contains(".") && line.contains("Giảm giá:")) {
                voucherText.append("$count. $line\n")
                count++
            }
        }
        
        if (count == 1) {
            voucherText.append("Hiện tại chưa có voucher nào.\n")
        }
        
        voucherText.append("\nBước 6: Chọn voucher (nhập số thứ tự) hoặc nhập 'không' để bỏ qua:")
        addBotMessage(voucherText.toString())
    }

    private fun processVoucherSelection(message: String) {
        if (message.trim().lowercase() == "không" || message.trim().lowercase() == "khong") {
            orderStep = 7
            confirmOrder()
        } else {
            try {
                val voucherNumber = message.trim().toInt()
                // This would need to be implemented based on your voucher loading logic
                orderStep = 7
                confirmOrder()
            } catch (e: Exception) {
                addBotMessage("❌ Vui lòng nhập số voucher hợp lệ hoặc 'không':")
            }
        }
    }

    private fun confirmOrder() {
        val orderSummary = StringBuilder()
        orderSummary.append("📋 Tóm tắt đơn hàng:\n\n")
        orderSummary.append("👤 Khách hàng: $customerName\n")
        orderSummary.append("📞 SĐT: $customerPhone\n")
        orderSummary.append("📍 Địa chỉ: $customerAddress\n\n")
        orderSummary.append("📚 Sản phẩm:\n")
        
        var totalPrice = 0
        for (productPair in selectedProducts) {
            val product = productPair.first
            val quantity = productPair.second
            val price = product.realPrice * quantity
            totalPrice += price
            
            orderSummary.append("• ${product.name} x$quantity = ${price}k\n")
        }
        
        orderSummary.append("\n💰 Tổng tiền: ${totalPrice}k\n")
        orderSummary.append("💳 Phương thức: Tiền mặt\n\n")
        orderSummary.append("Xác nhận tạo đơn hàng? (có/không):")
        
        addBotMessage(orderSummary.toString())
    }

    private fun createOrder() {
        showProgressDialog(true)
        
        // First, create and save address
        val addressId = System.currentTimeMillis()
        val address = Address(
            addressId,
            customerName,
            customerPhone,
            customerAddress,
            DataStoreManager.user?.email
        )
        
        // Save address to Firebase
        get(this).addressDatabaseReference
            .child(addressId.toString())
            .setValue(address) { addressError, _ ->
                if (addressError == null) {
                    android.util.Log.d("ChatActivity", "Address saved successfully: $addressId")
                    
                    // Now create the order
                    val order = Order().apply {
                        id = System.currentTimeMillis()
                        userEmail = DataStoreManager.user?.email ?: "guest@example.com"
                        dateTime = System.currentTimeMillis().toString()
                        products = selectedProducts.map { productPair ->
                            ProductOrder(
                                productPair.first.id,
                                productPair.first.name,
                                productPair.first.description,
                                productPair.second,
                                productPair.first.realPrice,
                                productPair.first.image
                            )
                        }
                        price = selectedProducts.sumOf { it.first.realPrice * it.second }
                        voucher = selectedVoucher?.getPriceDiscount(price) ?: 0
                        total = price - voucher
                        paymentMethod = "Tiền mặt"
                        status = Order.STATUS_DOING // Đặt trạng thái "Đang xử lý"
                        this.address = address
                    }
                    
                    // Debug: Log order details
                    android.util.Log.d("ChatActivity", "Creating order: ID=${order.id}, userEmail=${order.userEmail}, status=${order.status}")
                    
                    // Save order to Firebase
                    get(this).orderDatabaseReference
                        .child(order.id.toString())
                        .setValue(order) { orderError, _ ->
                            showProgressDialog(false)
                            if (orderError == null) {
                                android.util.Log.d("ChatActivity", "Order saved successfully: ${order.id}")
                                addBotMessage("✅ Đơn hàng #${order.id} đã được tạo thành công!\n\n📦 Trạng thái: Đang xử lý\n💳 Thanh toán: Tiền mặt\n📧 Email: ${order.userEmail}\n📍 Địa chỉ: $customerAddress\n\nCảm ơn bạn đã đặt hàng! 🎉")
                                
                                // Verify order was saved by reading it back
                                verifyOrderSaved(order.id)
                                resetOrderState()
                            } else {
                                android.util.Log.e("ChatActivity", "Error saving order: ${orderError.message}")
                                addBotMessage("❌ Có lỗi xảy ra khi tạo đơn hàng: ${orderError.message}")
                            }
                        }
                } else {
                    showProgressDialog(false)
                    android.util.Log.e("ChatActivity", "Error saving address: ${addressError.message}")
                    addBotMessage("❌ Có lỗi xảy ra khi lưu địa chỉ: ${addressError.message}")
                }
            }
    }

    private fun verifyOrderSaved(orderId: Long) {
        get(this).orderDatabaseReference
            .child(orderId.toString())
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val savedOrder = snapshot.getValue(Order::class.java)
                    if (savedOrder != null) {
                        android.util.Log.d("ChatActivity", "Order verification: Found order ${savedOrder.id} with userEmail ${savedOrder.userEmail}")
                    } else {
                        android.util.Log.e("ChatActivity", "Order verification: Order not found!")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    android.util.Log.e("ChatActivity", "Order verification failed: ${error.message}")
                }
            })
    }

    private fun resetOrderState() {
        isCreatingOrder = false
        orderStep = 0
        customerName = ""
        customerPhone = ""
        customerAddress = ""
        selectedProducts.clear()
        selectedVoucher = null
    }

    // Override onDestroy to save chat history
    override fun onDestroy() {
        super.onDestroy()
        saveChatHistory()
    }
}