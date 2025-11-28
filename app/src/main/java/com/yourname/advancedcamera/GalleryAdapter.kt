package com.yourname.advancedcamera

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class GalleryAdapter(
    private val images: List<String>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    // ViewHolder class - har image item ka view hold karega
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.image_item)
    }

    // ViewHolder create karega
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gallery, parent, false)
        return ViewHolder(view)
    }

    // Data ko view mein bind karega
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val imagePath = images[position]
        
        // Yahan ap image loading library use kar sakte hain (Glide, Picasso etc.)
        // Temporary placeholder
        holder.imageView.setImageResource(android.R.drawable.ic_menu_gallery)
        
        // Item click listener
        holder.itemView.setOnClickListener {
            onItemClick(imagePath)
        }
    }

    // Total items count
    override fun getItemCount(): Int = images.size
}
