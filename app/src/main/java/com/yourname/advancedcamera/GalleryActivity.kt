package com.yourname.advancedcamera

import android.os.Bundle
import android.widget.GridView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yourname.advancedcamera.utils.GalleryAdapter
import java.io.File

class GalleryActivity : AppCompatActivity() {

    private lateinit var gridView: GridView
    private lateinit var images: MutableList<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        gridView = findViewById(R.id.galleryGrid)
        images = loadImages()

        gridView.adapter = GalleryAdapter(this, images)

        gridView.setOnItemClickListener { _, _, position, _ ->
            val img = images[position]
            Toast.makeText(this, "Selected: $img", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadImages(): MutableList<String> {
        val list = mutableListOf<String>()
        val folder = File("${filesDir}/CapturedImages")

        if (!folder.exists()) folder.mkdirs()

        folder.listFiles()?.forEach {
            if (it.absolutePath.endsWith(".jpg") || it.absolutePath.endsWith(".png")) {
                list.add(it.absolutePath)
            }
        }
        return list
    }
}
