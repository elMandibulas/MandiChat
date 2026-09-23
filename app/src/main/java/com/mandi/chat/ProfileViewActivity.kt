package com.mandi.chat

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.mandi.chat.data.AppwriteProvider
import io.appwrite.Query
import kotlinx.coroutines.*

class ProfileViewActivity : Activity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var viewPager: ViewPager2
    private lateinit var txtName: TextView
    private lateinit var txtBio: TextView
    private lateinit var txtIndicator: TextView
    private val fotos = mutableListOf<String>()

    private fun getBucketId(): String {
        // Intenta encontrar el nombre del bucket como sea que lo hayas llamado
        return try {
            val c = AppwriteProvider::class.java
            val fields = listOf("BUCKET_ID", "BUCKET_AVATARS", "BUCKET_FOTOS", "BUCKET_PHOTOS", "STORAGE_BUCKET_ID", "BUCKET")
            for (name in fields) {
                try {
                    val f = c.getDeclaredField(name)
                    f.isAccessible = true
                    val v = f.get(null) as? String
                    if (!v.isNullOrBlank()) return v
                } catch (_: Exception) {}
            }
            // si no, busca cualquier campo que parezca bucket
            c.declaredFields.firstOrNull { it.name.contains("BUCKET", ignoreCase = true) }?.let {
                it.isAccessible = true
                (it.get(null) as? String)
            }?: ""
        } catch (_: Exception) { "" }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_view)

        viewPager = findViewById(R.id.viewPager)
        txtName = findViewById(R.id.txtName)
        txtBio = findViewById(R.id.txtBio)
        txtIndicator = findViewById(R.id.txtIndicator)
        findViewById<View>(R.id.btnClose).setOnClickListener { finish() }

        val otherId = intent.getStringExtra("otherUserId")?: run { finish(); return }

        viewPager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val img = ImageView(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setBackgroundColor(Color.BLACK)
                    adjustViewBounds = true
                }
                return object : RecyclerView.ViewHolder(img) {}
            }
            override fun getItemCount() = fotos.size
            override fun onBindViewHolder(h: RecyclerView.ViewHolder, p: Int) {
                val img = h.itemView as ImageView
                try {
                    val fileId = fotos[p]
                    if (fileId.isBlank()) return
                    val bucketId = getBucketId()
                    val url = if (bucketId.isNotBlank()) {
                        "${AppwriteProvider.ENDPOINT}/storage/buckets/$bucketId/files/$fileId/view?project=${AppwriteProvider.PROJECT_ID}"
                    } else {
                        // fallback: si no encuentra bucket, usa el helper viejo pero sin círculo es mejor que nada
                        null
                    }

                    if (url!= null) {
                        Glide.with(img.context).load(url).fitCenter().into(img)
                    } else {
                        // último fallback: carga normal (aunque sea circular, al menos no crashea)
                        com.mandi.chat.utils.AvatarHelper.loadInto(img, fileId)
                    }
                } catch (_: Exception) {}
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (fotos.isNotEmpty()) txtIndicator.text = "${position + 1}/${fotos.size}"
            }
        })

        scope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    AppwriteProvider.databases.listDocuments(
                        databaseId = AppwriteProvider.DATABASE_ID,
                        collectionId = AppwriteProvider.COLLECTION_PROFILES,
                        queries = listOf(Query.equal("userId", otherId), Query.limit(1))
                    )
                }
                val doc = res.documents.firstOrNull()
                val name = doc?.data?.get("name")?.toString()?: "Usuario"
                val edad = doc?.data?.get("edad")?.toString()?: ""
                val bio = doc?.data?.get("bio")?.toString()?: ""
                val listaFotos = doc?.data?.get("fotos") as? List<*>

                txtName.text = if (edad.isNotBlank()) "$name, $edad" else name
                txtBio.text = bio

                fotos.clear()
                listaFotos?.forEach { it?.toString()?.let { id -> if (id.isNotBlank()) fotos.add(id) } }

                if (fotos.isNotEmpty()) {
                    viewPager.adapter?.notifyDataSetChanged()
                    txtIndicator.text = "1/${fotos.size}"
                } else {
                    txtIndicator.text = "0/0"
                }
            } catch (e: Exception) {
                android.util.Log.e("Mandi", "Error perfil", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}