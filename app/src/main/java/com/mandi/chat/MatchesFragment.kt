package com.mandi.chat

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mandi.chat.data.AppwriteProvider
import com.mandi.chat.utils.AvatarHelper
import io.appwrite.Query
import kotlinx.coroutines.*

data class MatchUi(
    val otherId: String,
    val matchId: String,
    val nombre: String,
    val avatarId: String,
    val hasUnread: Boolean
)

class MatchesFragment : Fragment() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var rvMatches: RecyclerView? = null
    private var pollingJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_matches, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rvMatches = view.findViewById(R.id.rvMatches)
        rvMatches?.layoutManager = LinearLayoutManager(requireContext())
        loadMatches()
    }

    override fun onResume() {
        super.onResume()
        loadMatches()
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive) {
                delay(3000)
                loadMatches()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        pollingJob?.cancel()
    }

    private fun openChat(otherId: String, matchId: String) {
        val prefs = requireContext().getSharedPreferences("mandi", 0)
        val myUserId = prefs.getString("myUserId", null) ?: return
        val intent = Intent(requireContext(), ChatActivity::class.java).apply {
            putExtra("otherUserId", otherId)
            putExtra("matchId", matchId)
            putExtra("myUserId", myUserId)
        }
        startActivity(intent)
    }

    private fun loadMatches() {
        val rv = rvMatches ?: return
        scope.launch {
            try {
                val prefs = requireContext().getSharedPreferences("mandi", 0)
                val myUserId = prefs.getString("myUserId", null) ?: return@launch

                val result = withContext(Dispatchers.IO) {
                    AppwriteProvider.databases.listDocuments(
                        databaseId = AppwriteProvider.DATABASE_ID,
                        collectionId = AppwriteProvider.COLLECTION_MATCHES,
                        queries = listOf(
                            Query.limit(100),
                            Query.orderDesc("\$createdAt")
                        )
                    )
                }

                val pairs = result.documents.mapNotNull { doc ->
                    val users = (doc.data["users"] as? List<*>)?.map { it.toString() } ?: return@mapNotNull null
                    if (!users.contains(myUserId)) return@mapNotNull null
                    val other = users.firstOrNull { it != myUserId } ?: return@mapNotNull null
                    Pair(other, doc.id)
                }.distinctBy { it.first }

                val items = withContext(Dispatchers.IO) {
                    pairs.map { (otherId, matchId) ->
                        var nombre = otherId.take(8)
                        var avatarId = ""

                        try {
                            val prof = AppwriteProvider.databases.listDocuments(
                                databaseId = AppwriteProvider.DATABASE_ID,
                                collectionId = AppwriteProvider.COLLECTION_PROFILES,
                                queries = listOf(Query.equal("userId", otherId), Query.limit(1))
                            )
                            val doc = prof.documents.firstOrNull()
                            nombre = doc?.data?.get("name")?.toString() ?: nombre
                            avatarId = (doc?.data?.get("fotos") as? List<*>)?.firstOrNull()?.toString() ?: ""
                        } catch (_: Exception) {}

                        val hasUnread = try {
                            val unreadResult = AppwriteProvider.databases.listDocuments(
                                databaseId = AppwriteProvider.DATABASE_ID,
                                collectionId = AppwriteProvider.COLLECTION_MESSAGES,
                                queries = listOf(
                                    Query.equal("matchId", matchId),
                                    Query.equal("receiverId", myUserId),
                                    Query.equal("read", false),
                                    Query.limit(1)
                                )
                            )
                            unreadResult.total > 0
                        } catch (_: Exception) {
                            false
                        }

                        MatchUi(otherId, matchId, nombre, avatarId, hasUnread)
                    }
                }

                if (rv.adapter == null) {
                    rv.adapter = MatchesAdapter(items) { otherId, matchId -> openChat(otherId, matchId) }
                } else {
                    (rv.adapter as? MatchesAdapter)?.update(items)
                }
            } catch (e: Exception) {
                android.util.Log.e("Mandi", "Error matches", e)
            }
        }
    }

    class MatchesAdapter(
        private var items: List<MatchUi>,
        private val onChatClick: (String, String) -> Unit
    ) : RecyclerView.Adapter<MatchesAdapter.ViewHolder>() {
        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.txtMatchName)
            val btn: Button = v.findViewById(R.id.btnChat)
            val avatar: ImageView = v.findViewById(R.id.imgMatchAvatar)
            val redDot: View = v.findViewById(R.id.redDot)
        }

        fun update(newItems: List<MatchUi>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_match, parent, false))
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: ViewHolder, p: Int) {
            val item = items[p]
            h.name.text = item.nombre
            try {
                if (item.avatarId.isNotBlank()) AvatarHelper.loadInto(h.avatar, item.avatarId)
            } catch (_: Exception) {}
            h.redDot.visibility = if (item.hasUnread) View.VISIBLE else View.GONE

            h.btn.setOnClickListener { onChatClick(item.otherId, item.matchId) }
            h.itemView.setOnClickListener { onChatClick(item.otherId, item.matchId) }

            val openProfile = {
                try {
                    val intent = Intent(h.itemView.context, ProfileViewActivity::class.java)
                    intent.putExtra("otherUserId", item.otherId)
                    h.itemView.context.startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e("Mandi", "Error abriendo perfil", e)
                }
            }
            h.avatar.setOnClickListener { openProfile() }
            h.name.setOnClickListener { openProfile() }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pollingJob?.cancel()
        rvMatches = null
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}