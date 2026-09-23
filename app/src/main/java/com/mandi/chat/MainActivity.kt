package com.mandi.chat

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.mandi.chat.data.AppwriteProvider
import io.appwrite.ID
import io.appwrite.Query
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var badgeJob: Job? = null
    private var unreadBadge: TextView? = null
    private var matchBadge: TextView? = null
    lateinit var bottomNav: BottomNavigationView
    private val TEST_MODE = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppwriteProvider.init(this)
        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottomNav)
        val prefs = getSharedPreferences("mandi", 0)
        var myUserId = prefs.getString("myUserId", null)

        if (TEST_MODE) {
            if (myUserId == null || myUserId == "user_001" || myUserId.startsWith("mandiFinal")) {
                myUserId = "test_" + (100..999).random() + "_" + ID.unique().takeLast(4)
                prefs.edit().putString("myUserId", myUserId).apply()
                Toast.makeText(this, "MODO TEST: eres $myUserId", Toast.LENGTH_LONG).show()
            }
        }

        if (myUserId == null) {
            bottomNav.visibility = View.GONE
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, RegisterFragment())
                .commit()
            return
        }

        setupApp()
    }

    private fun setupApp() {
        loadFragment(DiscoveryFragment())
        setupBottomNav()
        createBadges()
        startUnreadChecker()
    }

    private fun setupBottomNav() {
        bottomNav.visibility = View.VISIBLE
        bottomNav.setOnItemSelectedListener { item ->
            when(item.itemId) {
                R.id.nav_discovery -> { loadFragment(DiscoveryFragment()); true }
                R.id.nav_matches -> {
                    clearMatchHighlight()
                    loadFragment(MatchesFragment()); true
                }
                else -> false
            }
        }
    }

    fun goToMatches() {
        bottomNav.selectedItemId = R.id.nav_matches
    }

    fun highlightMatchesTab() {
        runOnUiThread {
            matchBadge?.let { b ->
                b.visibility = View.VISIBLE
                b.text = "❤"
                b.bringToFront()
                b.scaleX = 0f
                b.scaleY = 0f
                b.animate().scaleX(1.2f).scaleY(1.2f).setDuration(250).withEndAction {
                    b.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                }.start()
            }
            try {
                bottomNav.getOrCreateBadge(R.id.nav_matches).apply {
                    isVisible = true
                    backgroundColor = Color.parseColor("#25D366")
                    badgeTextColor = Color.WHITE
                }
            } catch (_: Exception) {}
            try { getSystemService(android.os.Vibrator::class.java)?.vibrate(200) } catch (_: Exception) {}
        }
    }

    fun clearMatchHighlight() {
        runOnUiThread {
            matchBadge?.visibility = View.GONE
            try { bottomNav.removeBadge(R.id.nav_matches) } catch (_: Exception) {}
        }
    }

    private fun createBadges() {
        if (unreadBadge != null && matchBadge != null) return
        val root = findViewById<FrameLayout>(android.R.id.content)

        // Badge de mensajes NO LEIDOS - a la derecha
        if (unreadBadge == null) {
            unreadBadge = TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 11f
                gravity = Gravity.CENTER
                visibility = View.GONE
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.RED)
                }
            }
            val paramsUnread = FrameLayout.LayoutParams(52, 52, Gravity.BOTTOM or Gravity.END).apply {
                marginEnd = 62
                bottomMargin = 62
            }
            root.addView(unreadBadge, paramsUnread)
        }

        // Badge de NUEVO MATCH - a la izquierda de Matches
        if (matchBadge == null) {
            matchBadge = TextView(this).apply {
                text = "❤"
                setTextColor(Color.WHITE)
                textSize = 12f
                gravity = Gravity.CENTER
                visibility = View.GONE
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#25D366")) // verde, así se ve el corazón blanco
                    setStroke(2, Color.WHITE)
                }
            }
            val paramsMatch = FrameLayout.LayoutParams(56, 56, Gravity.BOTTOM or Gravity.END).apply {
                marginEnd = 165  // <- IZQUIERDA del icono Matches
                bottomMargin = 62
            }
            root.addView(matchBadge, paramsMatch)
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.container, fragment).commit()
    }

    fun showMainApp() { setupApp() }

    fun startUnreadChecker() {
        badgeJob?.cancel()
        badgeJob = scope.launch {
            val prefs = getSharedPreferences("mandi", 0)
            val myUserId = prefs.getString("myUserId", null) ?: return@launch
            while(isActive) {
                try {
                    val unread = withContext(Dispatchers.IO) {
                        AppwriteProvider.databases.listDocuments(
                            databaseId = AppwriteProvider.DATABASE_ID,
                            collectionId = AppwriteProvider.COLLECTION_MESSAGES,
                            queries = listOf(
                                Query.equal("receiverId", myUserId),
                                Query.equal("read", false),
                                Query.limit(100)
                            )
                        ).total
                    }
                    withContext(Dispatchers.Main) {
                        unreadBadge?.let { b ->
                            if (unread > 0) {
                                b.visibility = View.VISIBLE
                                b.text = if (unread > 9) "9+" else unread.toString()
                                b.bringToFront()
                            } else {
                                b.visibility = View.GONE
                            }
                        }
                    }
                } catch (e: Exception) {
                    // si falla, intenta sin filtro de read (algunas colecciones guardan 0/1)
                    try {
                        val total = withContext(Dispatchers.IO) {
                            AppwriteProvider.databases.listDocuments(
                                AppwriteProvider.DATABASE_ID,
                                AppwriteProvider.COLLECTION_MESSAGES,
                                listOf(Query.equal("receiverId", myUserId), Query.limit(100))
                            ).documents.count { it.data["read"] == false || it.data["read"] == 0 || it.data["read"] == "false" }
                        }
                        withContext(Dispatchers.Main) {
                            if (total > 0) {
                                unreadBadge?.visibility = View.VISIBLE
                                unreadBadge?.text = total.toString()
                            }
                        }
                    } catch (_: Exception) {}
                }
                delay(4000)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::bottomNav.isInitialized && bottomNav.visibility == View.VISIBLE) {
            startUnreadChecker()
            unreadBadge?.bringToFront()
            matchBadge?.bringToFront()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}