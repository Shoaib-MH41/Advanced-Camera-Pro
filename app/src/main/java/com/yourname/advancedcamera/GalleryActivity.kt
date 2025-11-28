package com.yourname.advancedcamera

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class GalleryActivity : AppCompatActivity() {
    
    private lateinit var galleryRecyclerView: RecyclerView
    private lateinit var galleryAdapter: GalleryAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)
        
        initializeViews()
        setupGallery()
    }
    
    private fun initializeViews() {
        // Initialize RecyclerView
        galleryRecyclerView = findViewById(R.id.galleryGrid)
        
        // Setup toolbar if needed
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // Back button handler
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }
    
    private fun setupGallery() {
        // Create sample data - replace with actual image paths
        val sampleImages = listOf(
            "image1.jpg",
            "image2.jpg", 
            "image3.jpg",
            "image4.jpg"
        )
        
        // Initialize adapter with CORRECT parameters
        galleryAdapter = GalleryAdapter(
            images = sampleImages,
            onItemClick = { imagePath ->
                // Handle image click
                onImageClicked(imagePath)
            }
        )
        
        // Setup layout manager
        val layoutManager = GridLayoutManager(this, 3) // 3 columns grid
        galleryRecyclerView.layoutManager = layoutManager
        galleryRecyclerView.adapter = galleryAdapter
    }
    
    private fun onImageClicked(imagePath: String) {
        // Handle image click - open full screen, share, etc.
        // You can implement this later
        android.widget.Toast.makeText(this, "Clicked: $imagePath", android.widget.Toast.LENGTH_SHORT).show()
    }
}
