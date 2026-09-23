package com.mandi.chat

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.mandi.chat.data.AppwriteProvider
import com.mandi.chat.utils.AvatarHelper
import io.appwrite.ID
import io.appwrite.Query
import kotlinx.coroutines.*

class DiscoveryFragment : Fragment() {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentProfileUserId: String? = null
    private var currentProfileName: String? = null
    private val seenIds = mutableSetOf<String>()
    private var pageCallback: ViewPager2.OnPageChangeCallback? = null
    private var filtroActual = "todos"
    private lateinit var btnH: Button
    private lateinit var btnM: Button
    private lateinit var btnT: Button

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_discovery, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val viewPager = view.findViewById<ViewPager2>(R.id.viewPagerFotos)
        val txtIndicator = view.findViewById<TextView>(R.id.txtIndicator)
        val txtName = view.findViewById<TextView>(R.id.txtName)
        val txtBio = view.findViewById<TextView>(R.id.txtBio)
        val btnDislike = view.findViewById<Button>(R.id.btnDislike)
        val btnLike = view.findViewById<Button>(R.id.btnLike)
        val btnEdit = view.findViewById<ImageView>(R.id.btnEditProfile)
        btnEdit.setOnClickListener { startActivity(Intent(requireContext(), EditProfileActivity::class.java)) }

        val filterRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 16)
        }

        fun makeChip(text: String): Button {
            return Button(requireContext()).apply {
                this.text = text
                textSize = 10.5f
                setPadding(8, 0, 8, 0)
                minHeight = 0
                minimumHeight = 0
                isAllCaps = false
                val d = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 50f
                    setColor(Color.parseColor("#2A2A2A"))
                }
                background = d
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, 80, 1f).apply { setMargins(8,0,8,0) }
            }
        }

        btnT = makeChip("Todos")
        btnH = makeChip("♂ Hombres")
        btnM = makeChip("♀ Mujeres")
        filterRow.addView(btnT)
        filterRow.addView(btnH)
        filterRow.addView(btnM)

        val btnContainer = btnDislike.parent as ViewGroup
        val mainContainer = btnContainer.parent as ViewGroup
        val index = mainContainer.indexOfChild(btnContainer)
        mainContainer.addView(filterRow, index)

        pageCallback?.let { viewPager.unregisterOnPageChangeCallback(it) }
        pageCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val total = viewPager.adapter?.itemCount?: 0
                if (total > 0) txtIndicator.text = "${position + 1}/$total"
            }
        }
        viewPager.registerOnPageChangeCallback(pageCallback!!)

        val prefs = requireContext().getSharedPreferences("mandi", 0)
        val myUserId = prefs.getString("myUserId", null)?: return
        filtroActual = prefs.getString("filtroVer", "todos")?: "todos"

        fun pintarFiltro() {
            val active = Color.parseColor("#25D366")
            val inactive = Color.parseColor("#2A2A2A")
            fun paint(b: Button, isActive: Boolean) {
                val d = b.background as GradientDrawable
                d.setColor(if(isActive) active else inactive)
                d.setStroke(if(isActive) 0 else 2, if(isActive) active else Color.parseColor("#444444"))
                b.setTextColor(if(isActive) Color.WHITE else Color.parseColor("#AAAAAA"))
            }
            paint(btnT, filtroActual=="todos")
            paint(btnH, filtroActual=="hombres")
            paint(btnM, filtroActual=="mujeres")
        }

        fun loadRandom() {
            scope.launch {
                try {
                    txtName.text = "Buscando..."
                    val queries = mutableListOf<String>().apply {
                        add(Query.limit(100))
                        if (filtroActual == "hombres") add(Query.equal("sexo", "hombre"))
                        if (filtroActual == "mujeres") add(Query.equal("sexo", "mujer"))
                    }
                    val result = withContext(Dispatchers.IO) {
                        AppwriteProvider.databases.listDocuments(
                            AppwriteProvider.DATABASE_ID,
                            AppwriteProvider.COLLECTION_PROFILES,
                            queries
                        )
                    }
                    val docs = result.documents.filter {
                        val uid = it.data["userId"] as? String
                        uid!= null && uid!= myUserId && uid!in seenIds
                    }
                    val doc = docs.randomOrNull()
                    if (doc == null) {
                        txtName.text = if(filtroActual=="todos") "No hay más usuarios 😅" else "No hay más ${filtroActual} 😅"
                        txtBio.text = ""
                        viewPager.adapter = null
                        txtIndicator.text = "0/0"
                        currentProfileUserId = null
                        return@launch
                    }
                    currentProfileUserId = doc.data["userId"] as? String
                    currentProfileName = doc.data["name"] as? String?: "Sin nombre"
                    val name = currentProfileName!!
                    val edad = (doc.data["edad"] as? Number)?.toInt()?: 30
                    val bio = doc.data["bio"] as? String?: ""
                    val fotos = (doc.data["fotos"] as? List<*>)?.mapNotNull { it as? String }
                     ?.ifEmpty { listOf(AppwriteProvider.DEFAULT_AVATAR_ID) }
                     ?: listOf(AppwriteProvider.DEFAULT_AVATAR_ID)

                    txtName.text = "$name, $edad"
                    txtBio.text = bio
                    txtIndicator.text = "1/${fotos.size}"
                    viewPager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_photo, parent, false)
                            return object : RecyclerView.ViewHolder(v) {}
                        }
                        override fun getItemCount() = fotos.size
                        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                            val img = holder.itemView.findViewById<ImageView>(R.id.imgPhoto)
                            AvatarHelper.loadRect(img, fotos[position])
                        }
                    }
                    viewPager.setCurrentItem(0, false)
                } catch (e: Exception) {
                    Log.e("Mandi", "Error load", e)
                    txtName.text = "Error: ${e.message}"
                }
            }
        }

        fun aplicarFiltro(nuevo: String) {
            filtroActual = nuevo
            prefs.edit().putString("filtroVer", nuevo).apply()
            pintarFiltro()
            seenIds.clear()
            loadRandom()
        }

        btnH.setOnClickListener { aplicarFiltro("hombres") }
        btnM.setOnClickListener { aplicarFiltro("mujeres") }
        btnT.setOnClickListener { aplicarFiltro("todos") }

        btnDislike.setOnClickListener { currentProfileUserId?.let { seenIds.add(it) }; loadRandom() }

        btnLike.setOnClickListener {
            val otherId = currentProfileUserId?: return@setOnClickListener
            val otherName = currentProfileName?: "esa persona"
            seenIds.add(otherId)
            scope.launch {
                var esMatch = false
                try {
                    withContext(Dispatchers.IO) {
                        try {
                            val all = AppwriteProvider.databases.listDocuments(AppwriteProvider.DATABASE_ID, AppwriteProvider.COLLECTION_LIKES, listOf(Query.limit(1000)))
                            for (d in all.documents.filter { it.data["from"]?.toString() == myUserId && it.data["to"]?.toString() == otherId }) {
                                AppwriteProvider.databases.deleteDocument(AppwriteProvider.DATABASE_ID, AppwriteProvider.COLLECTION_LIKES, d.id)
                            }
                        } catch (_: Exception) {}
                        AppwriteProvider.databases.createDocument(AppwriteProvider.DATABASE_ID, AppwriteProvider.COLLECTION_LIKES, ID.unique(), mapOf("from" to myUserId, "to" to otherId))
                        delay(600)
                        val allLikes = AppwriteProvider.databases.listDocuments(AppwriteProvider.DATABASE_ID, AppwriteProvider.COLLECTION_LIKES, listOf(Query.limit(1000)))
                        val rec = allLikes.documents.any { it.data["from"]?.toString() == otherId && it.data["to"]?.toString() == myUserId }
                        if (rec) {
                            val allMatches = AppwriteProvider.databases.listDocuments(AppwriteProvider.DATABASE_ID, AppwriteProvider.COLLECTION_MATCHES, listOf(Query.limit(1000)))
                            val exists = allMatches.documents.any { (it.data["users"] as? List<*>)?.contains(myUserId) == true && (it.data["users"] as? List<*>)?.contains(otherId) == true }
                            if (!exists) {
                                AppwriteProvider.databases.createDocument(AppwriteProvider.DATABASE_ID, AppwriteProvider.COLLECTION_MATCHES, ID.unique(), mapOf("users" to listOf(myUserId, otherId)))
                            }
                            esMatch = true
                        }
                    }
                } catch (e: Exception) { Log.e("Mandi", "LIKE ERROR", e) }

                withContext(Dispatchers.Main) {
                    if (esMatch) {
                        AlertDialog.Builder(requireContext())
                           .setTitle("¡Es un Match! 🔥")
                           .setMessage("Tú y $otherName os habéis gustado. ¡Empieza a chatear!")
                           .setPositiveButton("Chatear") { _, _ ->
                                (activity as? MainActivity)?.goToMatches()
                            }
                           .setNegativeButton("Seguir viendo", null)
                           .show()
                        (activity as? MainActivity)?.highlightMatchesTab()
                    }
                }
                loadRandom()
            }
        }

        pintarFiltro()
        loadRandom()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        view?.findViewById<ViewPager2>(R.id.viewPagerFotos)?.let { vp -> pageCallback?.let { vp.unregisterOnPageChangeCallback(it) } }
        scope.cancel()
    }
}