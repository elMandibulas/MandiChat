package com.mandi.chat

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.mandi.chat.data.AppwriteProvider
import io.appwrite.ID
import io.appwrite.Query
import kotlinx.coroutines.*

class RegisterFragment : Fragment() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var sexoSeleccionado: String? = null

    private data class Holder(
        val etName: EditText,
        val etEdad: EditText,
        val etCiudad: EditText,
        val rgSexo: RadioGroup,
        val rbHombre: RadioButton,
        val rbMujer: RadioButton,
        val btn: Button,
        val btnDelete: Button
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val scroll = ScrollView(ctx)
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        scroll.addView(layout)

        val etName = EditText(ctx).apply { hint = "Nombre" }
        val etEdad = EditText(ctx).apply { hint = "Edad (18-100)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val etCiudad = EditText(ctx).apply { hint = "Ciudad - Oviedo" }

        val tvSexo = TextView(ctx).apply { text = "Yo soy:"; textSize = 16f; setPadding(0,24,0,8) }
        val rgSexo = RadioGroup(ctx).apply { orientation = RadioGroup.HORIZONTAL }
        val rbHombre = RadioButton(ctx).apply { text = "♂ Hombre"; id = View.generateViewId() }
        val rbMujer = RadioButton(ctx).apply { text = "♀ Mujer"; id = View.generateViewId() }
        rgSexo.addView(rbHombre)
        rgSexo.addView(rbMujer)
        rgSexo.setOnCheckedChangeListener { _, checkedId ->
            sexoSeleccionado = when (checkedId) {
                rbHombre.id -> "hombre"
                rbMujer.id -> "mujer"
                else -> null
            }
        }

        val btn = Button(ctx).apply { text = "Registrarse con foto Mandi" }
        val btnDelete = Button(ctx).apply {
            text = "Borrar cuenta permanentemente"
            setBackgroundColor(Color.parseColor("#FF3B30"))
            setTextColor(Color.WHITE)
            visibility = View.GONE
        }

        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val lpTop = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 24 }
        val lpTopBig = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 48 }
        val lpDel = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 120 }

        layout.addView(etName, lp)
        layout.addView(etEdad, lpTop)
        layout.addView(tvSexo, lpTop)
        layout.addView(rgSexo, lp)
        layout.addView(etCiudad, lpTop)
        layout.addView(btn, lpTopBig)
        layout.addView(btnDelete, lpDel)

        scroll.tag = Holder(etName, etEdad, etCiudad, rgSexo, rbHombre, rbMujer, btn, btnDelete)
        return scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val holder = (view as ScrollView).tag as Holder
        val etName = holder.etName
        val etEdad = holder.etEdad
        val etCiudad = holder.etCiudad
        val rgSexo = holder.rgSexo
        val rbHombre = holder.rbHombre
        val rbMujer = holder.rbMujer
        val btnRegister = holder.btn
        val btnDelete = holder.btnDelete

        val prefs = requireContext().getSharedPreferences("mandi", 0)
        val myUserId = prefs.getString("myUserId", null)

        if (myUserId!= null) {
            btnRegister.text = "Guardar cambios"
            btnDelete.visibility = View.VISIBLE
            scope.launch {
                try {
                    val docs = withContext(Dispatchers.IO) {
                        AppwriteProvider.databases.listDocuments(
                            AppwriteProvider.DATABASE_ID,
                            AppwriteProvider.COLLECTION_PROFILES,
                            listOf(Query.equal("userId", myUserId), Query.limit(1))
                        )
                    }
                    if (docs.documents.isNotEmpty()) {
                        val d = docs.documents[0]
                        withContext(Dispatchers.Main) {
                            etName.setText(d.data["name"].toString())
                            etEdad.setText((d.data["edad"] as? Number)?.toInt()?.toString()?: "")
                            etCiudad.setText(d.data["ciudad"]?.toString()?: "Oviedo")
                            val sexo = d.data["sexo"]?.toString()?.lowercase()?: ""
                            sexoSeleccionado = sexo
                            if(sexo == "hombre") rgSexo.check(rbHombre.id)
                            if(sexo == "mujer") rgSexo.check(rbMujer.id)
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        btnDelete.setOnClickListener {
            val uid = prefs.getString("myUserId", null)?: return@setOnClickListener
            AlertDialog.Builder(requireContext())
              .setTitle("¿Borrar cuenta?")
              .setMessage("Se borrará tu perfil, fotos, likes, matches y chats. No se puede deshacer.")
              .setPositiveButton("BORRAR") { _, _ ->
                    btnDelete.isEnabled = false
                    btnDelete.text = "Borrando todo..."
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                try {
                                    val profs = AppwriteProvider.databases.listDocuments(AppwriteProvider.DATABASE_ID, AppwriteProvider.COLLECTION_PROFILES, listOf(Query.equal("userId", uid), Query.limit(10)))
                                    for (doc in profs.documents) {
                                        val fotos = doc.data["fotos"] as? List<*>
                                        fotos?.forEach { try { AppwriteProvider.storage.deleteFile(AppwriteProvider.BUCKET_AVATARS, it.toString()) } catch (_: Exception) {} }
                                        AppwriteProvider.databases.deleteDocument(AppwriteProvider.DATABASE_ID, AppwriteProvider.COLLECTION_PROFILES, doc.id)
                                    }
                                } catch (_: Exception) {}
                                try {
                                    val l1 = AppwriteProvider.databases.listDocuments(AppwriteProvider.DATABASE_ID, AppwriteProvider.COLLECTION_LIKES, listOf(Query.equal("from", uid), Query.limit(100)))
                                    for (d in l1.documents) try { AppwriteProvider.databases.deleteDocument(AppwriteProvider.DATABASE_ID, AppwriteProvider.COLLECTION_LIKES, d.id) } catch (_: Exception) {}
                                    val l2 = AppwriteProvider.databases.listDocuments(AppwriteProvider.DATABASE_ID, AppwriteProvider.COLLECTION_LIKES, listOf(Query.equal("to", uid), Query.limit(100)))
                                    for (d in l2.documents) try { AppwriteProvider.databases.deleteDocument(AppwriteProvider.DATABASE_ID, AppwriteProvider.COLLECTION_LIKES, d.id) } catch (_: Exception) {}
                                } catch (_: Exception) {}
                                try {
                                    val matches = AppwriteProvider.databases.listDocuments(AppwriteProvider.DATABASE_ID, AppwriteProvider.COLLECTION_MATCHES, listOf(Query.limit(100)))
                                    for (doc in matches.documents) {
                                        val users = (doc.data["users"] as? List<*>)?.map { it.toString() }?: continue
                                        if (users.contains(uid)) {
                                            try { AppwriteProvider.databases.deleteDocument(AppwriteProvider.DATABASE_ID, AppwriteProvider.COLLECTION_MATCHES, doc.id) } catch (_: Exception) {}
                                        }
                                    }
                                } catch (_: Exception) {}
                                try {
                                    val m1 = AppwriteProvider.databases.listDocuments(AppwriteProvider.DATABASE_ID, AppwriteProvider.COLLECTION_MESSAGES, listOf(Query.equal("senderId", uid), Query.limit(100)))
                                    for (d in m1.documents) try { AppwriteProvider.databases.deleteDocument(AppwriteProvider.DATABASE_ID, AppwriteProvider.COLLECTION_MESSAGES, d.id) } catch (_: Exception) {}
                                    val m2 = AppwriteProvider.databases.listDocuments(AppwriteProvider.DATABASE_ID, AppwriteProvider.COLLECTION_MESSAGES, listOf(Query.equal("receiverId", uid), Query.limit(100)))
                                    for (d in m2.documents) try { AppwriteProvider.databases.deleteDocument(AppwriteProvider.DATABASE_ID, AppwriteProvider.COLLECTION_MESSAGES, d.id) } catch (_: Exception) {}
                                } catch (_: Exception) {}
                            }
                            withContext(Dispatchers.Main) {
                                prefs.edit().clear().apply()
                                Toast.makeText(requireContext(), "Todo borrado", Toast.LENGTH_LONG).show()
                                requireActivity().finishAffinity()
                                startActivity(Intent(requireContext(), MainActivity::class.java))
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                btnDelete.isEnabled = true
                                btnDelete.text = "Borrar cuenta permanentemente"
                            }
                        }
                    }
                }
              .setNegativeButton("Cancelar", null)
              .show()
        }

        btnRegister.setOnClickListener {
            val name = etName.text.toString().trim()
            val edadInt = etEdad.text.toString().toIntOrNull()
            val ciudad = etCiudad.text.toString().ifEmpty { "Oviedo" }

            if (name.isEmpty()) {
                Toast.makeText(requireContext(), "Pon nombre", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (edadInt == null || edadInt < 18 || edadInt > 100) {
                Toast.makeText(requireContext(), "Edad debe ser entre 18 y 100", Toast.LENGTH_LONG).show()
                etEdad.error = "18-100"
                return@setOnClickListener
            }
            if (sexoSeleccionado.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "Selecciona si eres Hombre o Mujer", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            btnRegister.isEnabled = false
            btnRegister.text = if (myUserId!= null) "Guardando..." else "Creando..."

            scope.launch {
                try {
                    if (myUserId!= null) {
                        withContext(Dispatchers.IO) {
                            val docs = AppwriteProvider.databases.listDocuments(
                                AppwriteProvider.DATABASE_ID,
                                AppwriteProvider.COLLECTION_PROFILES,
                                listOf(Query.equal("userId", myUserId), Query.limit(1))
                            )
                            if (docs.documents.isNotEmpty()) {
                                AppwriteProvider.databases.updateDocument(
                                    AppwriteProvider.DATABASE_ID,
                                    AppwriteProvider.COLLECTION_PROFILES,
                                    docs.documents[0].id,
                                    mapOf(
                                        "name" to name,
                                        "edad" to edadInt,
                                        "ciudad" to ciudad,
                                        "sexo" to sexoSeleccionado!!
                                    )
                                )
                            }
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Guardado!", Toast.LENGTH_SHORT).show()
                            (activity as? MainActivity)?.showMainApp()
                        }
                    } else {
                        val docId = ID.unique()
                        val newUserId = "user_$docId"
                        withContext(Dispatchers.IO) {
                            AppwriteProvider.databases.createDocument(
                                databaseId = AppwriteProvider.DATABASE_ID,
                                collectionId = AppwriteProvider.COLLECTION_PROFILES,
                                documentId = docId,
                                data = mapOf(
                                    "name" to name,
                                    "edad" to edadInt,
                                    "ciudad" to ciudad,
                                    "sexo" to sexoSeleccionado!!,
                                    "userId" to newUserId,
                                    "fotos" to listOf(AppwriteProvider.DEFAULT_AVATAR_ID)
                                )
                            )
                        }
                        prefs.edit().putString("myUserId", newUserId).apply()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "$name creado!", Toast.LENGTH_LONG).show()
                            (activity as? MainActivity)?.showMainApp()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        btnRegister.isEnabled = true
                        btnRegister.text = if (myUserId!= null) "Guardar cambios" else "Registrarse con foto Mandi"
                    }
                }
            }
        }
    }
    override fun onDestroyView() { super.onDestroyView(); scope.cancel() }
}