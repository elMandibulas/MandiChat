package com.mandi.chat

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import coil.load
import coil.request.CachePolicy
import com.mandi.chat.data.AppwriteProvider
import io.appwrite.ID
import io.appwrite.Query
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

class EditProfileActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var myUserId: String
    private var profileDocId: String? = null
    private val selectedUris = mutableListOf<Uri>()
    private var existingFotosIds = mutableListOf<String>()
    private lateinit var containerFotos: LinearLayout
    private lateinit var etName: EditText
    private lateinit var etBio: EditText
    private lateinit var etAge: EditText
    
    // SEXO
    private var sexoSeleccionado: String? = null
    private lateinit var rgSexo: RadioGroup
    private lateinit var rbHombre: RadioButton
    private lateinit var rbMujer: RadioButton

    private val pickImages = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            val espacioLibre = 4 - existingFotosIds.size - selectedUris.size
            if (espacioLibre <= 0) {
                Toast.makeText(this, "Ya tienes 4 fotos (desliza para verlas todas)", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            val aAgregar = uris.take(espacioLibre)
            selectedUris.addAll(aAgregar)
            showCombined()
            if (uris.size > espacioLibre) {
                Toast.makeText(this, "Solo caben 4, se agregaron $espacioLibre", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { AppwriteProvider.init(this) } catch (e: Exception) {}
        myUserId = getSharedPreferences("mandi", 0).getString("myUserId", null) ?: "user_001"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
            setBackgroundColor(Color.parseColor("#000000"))
        }

        etName = EditText(this).apply { hint = "Nombre"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        etAge = EditText(this).apply { 
            hint = "Edad (18-100)" 
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        
        // --- SELECTOR SEXO ---
        val tvSexo = TextView(this).apply { text = "Yo soy:"; setTextColor(Color.WHITE); textSize = 16f; setPadding(0,24,0,8) }
        rgSexo = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        rbHombre = RadioButton(this).apply { 
            text = "♂ Hombre"; setTextColor(Color.WHITE)
            id = android.view.View.generateViewId()
        }
        rbMujer = RadioButton(this).apply { 
            text = "♀ Mujer"; setTextColor(Color.WHITE)
            id = android.view.View.generateViewId()
        }
        rgSexo.addView(rbHombre)
        rgSexo.addView(rbMujer)
        rgSexo.setOnCheckedChangeListener { _, checkedId ->
            sexoSeleccionado = when (checkedId) {
                rbHombre.id -> "hombre"
                rbMujer.id -> "mujer"
                else -> null
            }
        }

        etBio = EditText(this).apply { hint = "Bio..."; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); minLines = 3 }
        val btnFotos = Button(this).apply { text = "📷 Elegir fotos (max 4)" }
        
        containerFotos = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)
        }
        val scrollFotos = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = true
            addView(containerFotos)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val btnClear = Button(this).apply { text = "🗑️ Limpiar nuevas"; setBackgroundColor(Color.parseColor("#444444")); setTextColor(Color.WHITE) }
        val btnSave = Button(this).apply { text = "GUARDAR PERFIL"; setBackgroundColor(Color.parseColor("#25D366")); setTextColor(Color.WHITE) }
        val btnBack = Button(this).apply { text = "Volver sin guardar" }
        val btnDelete = Button(this).apply {
            text = "Borrar cuenta permanentemente"
            setBackgroundColor(Color.parseColor("#FF3B30"))
            setTextColor(Color.WHITE)
        }
        val lpDel = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = 100 }

        root.addView(TextView(this).apply { text = "Editar Perfil"; textSize = 22f; setTextColor(Color.WHITE); setPadding(0,0,0,24) })
        root.addView(etName)
        root.addView(etAge)
        root.addView(tvSexo)
        root.addView(rgSexo)
        root.addView(etBio)
        root.addView(btnFotos)
        root.addView(scrollFotos)
        root.addView(btnClear)
        root.addView(btnSave)
        root.addView(btnBack)
        root.addView(btnDelete, lpDel)
        
        val scrollRoot = ScrollView(this).apply { addView(root) }
        setContentView(scrollRoot)

        btnFotos.setOnClickListener { pickImages.launch("image/*") }
        btnClear.setOnClickListener {
            selectedUris.clear()
            showCombined()
            Toast.makeText(this, "Nuevas borradas", Toast.LENGTH_SHORT).show()
        }
        btnSave.setOnClickListener { saveProfile() }
        btnBack.setOnClickListener { finish() }
        
        btnDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("¿Borrar cuenta?")
                .setMessage("Se borrará tu perfil, fotos, matches y chats. No se puede deshacer.")
                .setPositiveButton("BORRAR") { _, _ -> borrarCuenta(btnDelete) }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        loadCurrentProfile()
    }

    private fun getImageUrl(fileId: String): String {
        return "${AppwriteProvider.ENDPOINT}/storage/buckets/${AppwriteProvider.BUCKET_AVATARS}/files/$fileId/view?project=${AppwriteProvider.PROJECT_ID}"
    }

    private fun showCombined() {
        containerFotos.removeAllViews()
        for (fileId in existingFotosIds.toList()) {
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(200, 200).apply { setMargins(8,8,8,8) }
            }
            iv.load(getImageUrl(fileId)) {
                memoryCachePolicy(CachePolicy.DISABLED)
                diskCachePolicy(CachePolicy.DISABLED)
            }
            iv.setOnLongClickListener {
                existingFotosIds.remove(fileId)
                showCombined()
                Toast.makeText(this, "Borrada, guarda para confirmar", Toast.LENGTH_SHORT).show()
                true
            }
            containerFotos.addView(iv)
        }
        for (uri in selectedUris.toList()) {
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(200, 200).apply { setMargins(8,8,8,8) }
                load(uri)
            }
            iv.setOnLongClickListener {
                selectedUris.remove(uri)
                showCombined()
                true
            }
            containerFotos.addView(iv)
        }
    }

    private fun loadCurrentProfile() {
        scope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    AppwriteProvider.databases.listDocuments(
                        databaseId = AppwriteProvider.DATABASE_ID,
                        collectionId = AppwriteProvider.COLLECTION_PROFILES,
                        queries = listOf(Query.equal("userId", myUserId))
                    )
                }
                val doc = res.documents.firstOrNull()
                if (doc != null) {
                    profileDocId = doc.id
                    etName.setText(doc.data["name"]?.toString() ?: "")
                    etBio.setText(doc.data["bio"]?.toString() ?: "")
                    etAge.setText((doc.data["edad"] ?: doc.data["age"])?.toString() ?: "")
                    
                    // CARGAR SEXO
                    val sexoActual = doc.data["sexo"]?.toString()?.lowercase() ?: ""
                    sexoSeleccionado = if(sexoActual.isNotEmpty()) sexoActual else null
                    if(sexoActual == "hombre") rgSexo.check(rbHombre.id)
                    if(sexoActual == "mujer") rgSexo.check(rbMujer.id)

                    val fotosRaw = doc.data["fotos"] as? List<*>
                    val fotos = fotosRaw?.mapNotNull { it?.toString() } ?: emptyList()
                    existingFotosIds = fotos.take(4).toMutableList()
                    showCombined()
                }
            } catch (e: Exception) {
                Toast.makeText(this@EditProfileActivity, "Aviso: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveProfile() {
        val ageInt = etAge.text.toString().toIntOrNull()
        if (ageInt == null || ageInt < 18 || ageInt > 100) {
            etAge.error = "Tiene que ser entre 18 y 100"
            Toast.makeText(this, "La edad debe ser entre 18 y 100", Toast.LENGTH_LONG).show()
            return
        }
        if(sexoSeleccionado.isNullOrEmpty()){
            Toast.makeText(this, "Selecciona si eres Hombre o Mujer", Toast.LENGTH_LONG).show()
            return
        }

        scope.launch {
            try {
                Toast.makeText(this@EditProfileActivity, "Subiendo ${selectedUris.size}...", Toast.LENGTH_SHORT).show()
                val uploadedIds = mutableListOf<String>()
                for (uri in selectedUris) {
                    val fileId = withContext(Dispatchers.IO) {
                        val input = contentResolver.openInputStream(uri) ?: throw Exception("No se pudo abrir imagen")
                        val tempFile = File.createTempFile("upload", ".jpg", cacheDir)
                        FileOutputStream(tempFile).use { input.copyTo(it) }
                        val file = AppwriteProvider.storage.createFile(
                            bucketId = AppwriteProvider.BUCKET_AVATARS,
                            fileId = ID.unique(),
                            file = io.appwrite.models.InputFile.fromFile(tempFile)
                        )
                        tempFile.delete()
                        file.id
                    }
                    uploadedIds.add(fileId)
                }
                withContext(Dispatchers.IO) {
                    if (profileDocId != null) {
                        val data = mutableMapOf<String, Any>(
                            "name" to etName.text.toString(),
                            "bio" to etBio.text.toString(),
                            "edad" to ageInt,
                            "sexo" to sexoSeleccionado!!
                        )
                        val defaultId = AppwriteProvider.DEFAULT_AVATAR_ID
                        val baseFotos = if (uploadedIds.isNotEmpty()) {
                            existingFotosIds.filter { it != defaultId }
                        } else {
                            existingFotosIds
                        }
                        val finalIds = (uploadedIds + baseFotos).distinct().take(4)
                        
                        if (finalIds.isNotEmpty()) data["fotos"] = finalIds
                        AppwriteProvider.databases.updateDocument(
                            databaseId = AppwriteProvider.DATABASE_ID,
                            collectionId = AppwriteProvider.COLLECTION_PROFILES,
                            documentId = profileDocId!!,
                            data = data
                        )
                    }
                }
                Toast.makeText(this@EditProfileActivity, "¡Guardado!", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@EditProfileActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun borrarCuenta(btn: Button) {
        btn.isEnabled = false
        btn.text = "Borrando..."
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    try {
                        val profs = AppwriteProvider.databases.listDocuments(AppwriteProvider.DATABASE_ID, AppwriteProvider.COLLECTION_PROFILES, listOf(Query.equal("userId", myUserId), Query.limit(10)))
                        for (doc in profs.documents) {
                            val fotos = doc.data["fotos"] as? List<*>
                            fotos?.forEach { val id = it.toString()
                                if (id == AppwriteProvider.DEFAULT_AVATAR_ID) return@forEach
                                try { AppwriteProvider.storage.deleteFile(AppwriteProvider.BUCKET_AVATARS, id) } catch (_:Exception) {} }
                            AppwriteProvider.databases.deleteDocument(AppwriteProvider.DATABASE_ID, AppwriteProvider.COLLECTION_PROFILES, doc.id)
                        }
                    } catch (_:Exception) {}
                    try {
                        val ms = AppwriteProvider.databases.listDocuments(AppwriteProvider.DATABASE_ID, AppwriteProvider.COLLECTION_MATCHES, listOf(Query.limit(100)))
                        for (doc in ms.documents) {
                            val users = (doc.data["users"] as? List<*>)?.map { it.toString() }?: continue
                            if (users.contains(myUserId)) AppwriteProvider.databases.deleteDocument(AppwriteProvider.DATABASE_ID, AppwriteProvider.COLLECTION_MATCHES, doc.id)
                        }
                    } catch (_:Exception) {}
                    try {
                        val q1 = AppwriteProvider.databases.listDocuments(AppwriteProvider.DATABASE_ID, AppwriteProvider.COLLECTION_MESSAGES, listOf(Query.equal("senderId", myUserId), Query.limit(100)))
                        for (d in q1.documents) try { AppwriteProvider.databases.deleteDocument(AppwriteProvider.DATABASE_ID, AppwriteProvider.COLLECTION_MESSAGES, d.id) } catch (_:Exception) {}
                        val q2 = AppwriteProvider.databases.listDocuments(AppwriteProvider.DATABASE_ID, AppwriteProvider.COLLECTION_MESSAGES, listOf(Query.equal("receiverId", myUserId), Query.limit(100)))
                        for (d in q2.documents) try { AppwriteProvider.databases.deleteDocument(AppwriteProvider.DATABASE_ID, AppwriteProvider.COLLECTION_MESSAGES, d.id) } catch (_:Exception) {}
                    } catch (_:Exception) {}
                }
                withContext(Dispatchers.Main) {
                    getSharedPreferences("mandi",0).edit().clear().apply()
                    Toast.makeText(this@EditProfileActivity, "Cuenta borrada", Toast.LENGTH_LONG).show()
                    finishAffinity()
                    startActivity(Intent(this@EditProfileActivity, MainActivity::class.java))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@EditProfileActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    btn.isEnabled = true
                    btn.text = "Borrar cuenta permanentemente"
                }
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}