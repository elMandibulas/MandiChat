package com.mandi.chat

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load

class ProfileDetailActivity : AppCompatActivity() {

    private val BUCKET_ID = "6aaa6ee200b3055dd78"
    private val PROJECT_ID = "6aaa64e40001df66f2df"
    private val ENDPOINT = "https://fra.cloud.appwrite.io/v1"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_detail)

        val name = intent.getStringExtra("name")?: ""
        val bio = intent.getStringExtra("bio")?: ""
        val fotos = intent.getStringArrayListExtra("fotos")?: arrayListOf()

        findViewById<TextView>(R.id.tvName).text = name
        findViewById<TextView>(R.id.tvBio).text = bio

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        viewPager.adapter = ImageAdapter(fotos, BUCKET_ID, PROJECT_ID, ENDPOINT)

        findViewById<Button>(R.id.btnNope).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnLike).setOnClickListener { 
            // aquí luego ponemos tu lógica de like
            finish() 
        }
    }

    class ImageAdapter(
        private val fotos: List<String>,
        private val bucketId: String,
        private val projectId: String,
        private val endpoint: String
    ) : RecyclerView.Adapter<ImageAdapter.Holder>() {
        class Holder(val iv: ImageView) : RecyclerView.ViewHolder(iv)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val iv = ImageView(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(-1, -1)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            return Holder(iv)
        }
        override fun getItemCount() = if (fotos.isEmpty()) 1 else fotos.size
        override fun onBindViewHolder(holder: Holder, pos: Int) {
            if (fotos.isEmpty()) return
            val url = "$endpoint/storage/buckets/$bucketId/files/${fotos[pos]}/view?project=$projectId"
            holder.iv.load(url)
        }
    }
}