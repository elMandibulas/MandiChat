package com.mandi.chat.utils

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.mandi.chat.data.AppwriteProvider

object AvatarHelper {

    private fun buildViewUrl(fileId: String): String {
        return "${AppwriteProvider.ENDPOINT}/storage/buckets/${AppwriteProvider.BUCKET_AVATARS}/files/$fileId/view?project=${AppwriteProvider.PROJECT_ID}"
    }

    fun loadRect(iv: ImageView, fileId: String) {
        if (fileId.isBlank()) return
        val url = buildViewUrl(fileId)
        Glide.with(iv.context)
            .load(url)
            .centerCrop()
            .into(iv)
    }

    fun loadInto(iv: ImageView, fileId: String) {
        if (fileId.isBlank()) return
        val url = buildViewUrl(fileId)
        Glide.with(iv.context)
            .load(url)
            .circleCrop()
            .into(iv)
    }
}