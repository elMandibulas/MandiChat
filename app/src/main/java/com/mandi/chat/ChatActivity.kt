package com.mandi.chat

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.mandi.chat.data.AppwriteProvider
import io.appwrite.Query
import io.appwrite.ID
import io.appwrite.models.Document
import kotlinx.coroutines.*

class ChatActivity : AppCompatActivity() {

    private val DATABASE_ID = AppwriteProvider.DATABASE_ID
    private val COLLECTION_MESSAGES = AppwriteProvider.COLLECTION_MESSAGES
    private val COLLECTION_PROFILES = AppwriteProvider.COLLECTION_PROFILES
    private val COLLECTION_MATCHES = AppwriteProvider.COLLECTION_MATCHES
    private val COLLECTION_LIKES = AppwriteProvider.COLLECTION_LIKES
    private val PROJECT_ID = AppwriteProvider.PROJECT_ID
    private val ENDPOINT = AppwriteProvider.ENDPOINT
    private val BUCKET_AVATARS = AppwriteProvider.BUCKET_AVATARS
    private val DEFAULT_AVATAR_ID = AppwriteProvider.DEFAULT_AVATAR_ID
    private lateinit var myUserId: String

    private val messages = mutableListOf<Document<Map<String, Any>>>()
    private lateinit var adapter: MessageAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var matchId: String
    private lateinit var otherUserId: String
    private lateinit var imgAvatar: ImageView
    private lateinit var txtTitle: TextView
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppwriteProvider.init(this)

        val prefs = getSharedPreferences("mandi", 0)
        myUserId = intent.getStringExtra("myUserId")?: prefs.getString("myUserId", null)?: "user_001"

        matchId = intent.getStringExtra("matchId")?: "test_match"
        otherUserId = intent.getStringExtra("otherUserId")?: "user_002"
        val otherUserName = intent.getStringExtra("otherUserName")?: otherUserId

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#ECE5DD"))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#075E54"))
            setPadding(32, 32, 32, 32)
            gravity = Gravity.CENTER_VERTICAL
        }
        imgAvatar = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(120, 120)
        }
        txtTitle = TextView(this).apply {
            text = " 💬 $otherUserName"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(24,0,0,0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnDelete = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(100, 100)
            setImageResource(android.R.drawable.ic_menu_delete)
            setColorFilter(Color.parseColor("#FFCDD2"))
            setPadding(20,20,20,20)
            isClickable = true
            isFocusable = true
        }
        header.addView(imgAvatar)
        header.addView(txtTitle)
        header.addView(btnDelete)

        recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply { stackFromEnd = true }
            setPadding(12,12,12,12)
        }
        val inputLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.WHITE)
        }
        val input = EditText(this).apply {
            hint = "Escribe..."
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            background = null
        }
        val btnSend = Button(this).apply {
            text = "➤"
            setBackgroundColor(Color.parseColor("#25D366"))
            setTextColor(Color.WHITE)
        }

        inputLayout.addView(input)
        inputLayout.addView(btnSend)
        root.addView(header)
        root.addView(recycler, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(inputLayout)
        setContentView(root)

        adapter = MessageAdapter(messages, myUserId)
        recycler.adapter = adapter

        loadOtherUserAvatar()
        loadMessages(true)

        scope.launch {
            while (isActive) {
                delay(2000)
                loadMessages(false)
            }
        }

        btnSend.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                input.text.clear()
            }
        }

        btnDelete.setOnClickListener {
            android.app.AlertDialog.Builder(this)
              .setTitle("¿Eliminar chat?")
              .setMessage("Se borrarán:\n• Mensajes de ambos\n• Los 2 likes (from/to)\n• El match\n\nVolverás a Discovery.")
              .setPositiveButton("Borrar todo") { _, _ ->
                    scope.launch { deleteFullChat() }
                }
              .setNegativeButton("Cancelar", null)
              .show()
        }

        imgAvatar.setOnClickListener {
            val i = android.content.Intent(this, ProfileViewActivity::class.java)
            i.putExtra("otherUserId", otherUserId)
            startActivity(i)
        }
    }

    private fun getAvatarUrl(avatarId: String?): String {
        val fileId = if (avatarId.isNullOrEmpty()) DEFAULT_AVATAR_ID else avatarId
        return "$ENDPOINT/storage/buckets/$BUCKET_AVATARS/files/$fileId/view?project=$PROJECT_ID"
    }

    private fun loadOtherUserAvatar() {
        txtTitle.text = " 💬 $otherUserId"
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    AppwriteProvider.databases.listDocuments(
                        databaseId = DATABASE_ID,
                        collectionId = COLLECTION_PROFILES,
                        queries = listOf(Query.equal("userId", otherUserId))
                    )
                }
                val doc = result.documents.firstOrNull()?: return@launch
                val nombreReal = doc.data["name"]?.toString()?: otherUserId
                val fotos = doc.data["fotos"] as? List<*>
                val avatarId = fotos?.firstOrNull()?.toString()?: DEFAULT_AVATAR_ID
                withContext(Dispatchers.Main) {
                    txtTitle.text = " 💬 $nombreReal"
                    imgAvatar.load(getAvatarUrl(avatarId)) {
                        crossfade(true)
                        transformations(CircleCropTransformation())
                        placeholder(android.R.drawable.sym_def_app_icon)
                        error(android.R.drawable.sym_def_app_icon)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatActivity, "Error perfil: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadMessages(showError: Boolean = false) {
        scope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    AppwriteProvider.databases.listDocuments(
                        databaseId = DATABASE_ID,
                        collectionId = COLLECTION_MESSAGES,
                        queries = listOf(
                            Query.equal("matchId", matchId),
                            Query.orderAsc("\$createdAt"),
                            Query.limit(100)
                        )
                    )
                }
                withContext(Dispatchers.Main){
                    // refresca siempre para que cambien los ticks a azul
                    messages.clear()
                    messages.addAll(res.documents)
                    adapter.notifyDataSetChanged()
                    if (messages.isNotEmpty()) recycler.scrollToPosition(messages.size - 1)
                }
                markAllAsRead()
            } catch (e: Exception) {
                if (showError) {
                    withContext(Dispatchers.Main){
                        Toast.makeText(this@ChatActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun markAllAsRead() {
        scope.launch(Dispatchers.IO) {
            try {
                val unreadDocs = AppwriteProvider.databases.listDocuments(
                    DATABASE_ID, COLLECTION_MESSAGES,
                    queries = listOf(
                        Query.equal("matchId", matchId),
                        Query.equal("receiverId", myUserId),
                        Query.equal("read", false),
                        Query.limit(100)
                    )
                )
                for (doc in unreadDocs.documents) {
                    try {
                        AppwriteProvider.databases.updateDocument(
                            DATABASE_ID, COLLECTION_MESSAGES, doc.id,
                            data = mapOf("read" to true)
                        )
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
    }

    private fun sendMessage(text: String) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    AppwriteProvider.databases.createDocument(
                        DATABASE_ID, COLLECTION_MESSAGES, ID.unique(),
                        data = mapOf(
                            "matchId" to matchId,
                            "senderId" to myUserId,
                            "receiverId" to otherUserId,
                            "text" to text,
                            "read" to false
                        )
                    )
                }
                loadMessages(true)
            } catch (e: Exception) {
                withContext(Dispatchers.Main){
                    Toast.makeText(this@ChatActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun deleteFullChat() = withContext(Dispatchers.IO) {
        try {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@ChatActivity, "Borrando todo...", Toast.LENGTH_SHORT).show()
            }

            var hasMoreMsg = true
            while (hasMoreMsg) {
                val msgs = AppwriteProvider.databases.listDocuments(
                    DATABASE_ID, COLLECTION_MESSAGES,
                    queries = listOf(Query.equal("matchId", matchId), Query.limit(100))
                )
                for (m in msgs.documents) {
                    try { AppwriteProvider.databases.deleteDocument(DATABASE_ID, COLLECTION_MESSAGES, m.id) } catch(_: Exception){}
                }
                hasMoreMsg = msgs.documents.size == 100
            }
            try {
                val old1 = AppwriteProvider.databases.listDocuments(DATABASE_ID, COLLECTION_MESSAGES,
                    queries = listOf(Query.equal("senderId", myUserId), Query.equal("receiverId", otherUserId), Query.limit(100)))
                old1.documents.forEach { try { AppwriteProvider.databases.deleteDocument(DATABASE_ID, COLLECTION_MESSAGES, it.id) } catch(_: Exception){} }
                val old2 = AppwriteProvider.databases.listDocuments(DATABASE_ID, COLLECTION_MESSAGES,
                    queries = listOf(Query.equal("senderId", otherUserId), Query.equal("receiverId", myUserId), Query.limit(100)))
                old2.documents.forEach { try { AppwriteProvider.databases.deleteDocument(DATABASE_ID, COLLECTION_MESSAGES, it.id) } catch(_: Exception){} }
            } catch(_: Exception){}

            try {
                val l1 = AppwriteProvider.databases.listDocuments(
                    DATABASE_ID, COLLECTION_LIKES,
                    queries = listOf(Query.equal("from", myUserId), Query.equal("to", otherUserId), Query.limit(1000))
                )
                for (d in l1.documents) try { AppwriteProvider.databases.deleteDocument(DATABASE_ID, COLLECTION_LIKES, d.id) } catch(_: Exception){}

                val l2 = AppwriteProvider.databases.listDocuments(
                    DATABASE_ID, COLLECTION_LIKES,
                    queries = listOf(Query.equal("from", otherUserId), Query.equal("to", myUserId), Query.limit(1000))
                )
                for (d in l2.documents) try { AppwriteProvider.databases.deleteDocument(DATABASE_ID, COLLECTION_LIKES, d.id) } catch(_: Exception){}
            } catch (e: Exception) {
                try {
                    val all = AppwriteProvider.databases.listDocuments(DATABASE_ID, COLLECTION_LIKES, listOf(Query.limit(1000)))
                    for (d in all.documents) {
                        val from = d.data["from"]?.toString()
                        val to = d.data["to"]?.toString()
                        if ((from == myUserId && to == otherUserId) || (from == otherUserId && to == myUserId)) {
                            try { AppwriteProvider.databases.deleteDocument(DATABASE_ID, COLLECTION_LIKES, d.id) } catch(_: Exception){}
                        }
                    }
                } catch(_: Exception){}
            }

            try {
                AppwriteProvider.databases.deleteDocument(DATABASE_ID, COLLECTION_MATCHES, matchId)
            } catch(_: Exception){
                try {
                    val m = AppwriteProvider.databases.listDocuments(
                        DATABASE_ID, COLLECTION_MATCHES,
                        queries = listOf(Query.contains("users", myUserId), Query.contains("users", otherUserId), Query.limit(10))
                    )
                    for (doc in m.documents) try { AppwriteProvider.databases.deleteDocument(DATABASE_ID, COLLECTION_MATCHES, doc.id) } catch(_: Exception){}
                } catch(_: Exception){}
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@ChatActivity, "Chat borrado", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@ChatActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }

    // ADAPTER CON DOBLE CHECK INLINE A LA DERECHA
    class MessageAdapter(val list: List<Document<Map<String, Any>>>, val myId: String) : RecyclerView.Adapter<MessageAdapter.VH>() {

        class VH(val container: LinearLayout, val bubbleLayout: LinearLayout, val tvText: TextView, val tvCheck: TextView) : RecyclerView.ViewHolder(container)

        override fun onCreateViewHolder(p: android.view.ViewGroup, t: Int): VH {
            val container = LinearLayout(p.context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(12, 4, 12, 4)
                layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)
            }

            // Burbuja horizontal: texto + tick al lado
            val bubbleLayout = LinearLayout(p.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
                setPadding(28, 16, 22, 16)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val tvText = TextView(p.context).apply {
                textSize = 15.5f
                setTextColor(Color.BLACK)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val tvCheck = TextView(p.context).apply {
                textSize = 10f
                setPadding(12, 0, 0, 0) // separacion del texto
                includeFontPadding = false
            }

            bubbleLayout.addView(tvText)
            bubbleLayout.addView(tvCheck)
            container.addView(bubbleLayout)

            return VH(container, bubbleLayout, tvText, tvCheck)
        }

        override fun getItemCount() = list.size

        override fun onBindViewHolder(h: VH, p: Int) {
            val d = list[p].data
            val sender = d["senderId"]?.toString()?: ""
            val isMe = sender == myId
            val isRead = d["read"] as? Boolean?: false

            h.tvText.text = d["text"]?.toString()?: ""

            val bubble = GradientDrawable().apply {
                cornerRadius = 28f
                if (isMe) setColor(Color.parseColor("#DCF8C6")) else setColor(Color.WHITE)
            }
            h.bubbleLayout.background = bubble
            h.container.gravity = if (isMe) Gravity.END else Gravity.START

            if (isMe) {
                h.tvCheck.visibility = android.view.View.VISIBLE
                h.tvCheck.text = "✓✓"
                h.tvCheck.setTextColor(
                    if (isRead) Color.parseColor("#34B7F1") else Color.parseColor("#9E9E9E")
                )
            } else {
                h.tvCheck.visibility = android.view.View.GONE
            }
        }
    }
}