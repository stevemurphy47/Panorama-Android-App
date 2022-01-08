package com.android.example.panoramacamera.fragments

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.android.example.panoramacamera.R
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class StitcherFragment: Fragment() {
    private lateinit var container: ConstraintLayout
    private val pickImageCode = 1
    private val imagesMat = ArrayList<Mat>()
    private var bmpArray = ArrayList<Bitmap>()
    private var result: Bitmap? = null

    //demo
    private val OK = 0
    private val ERR_NEED_MORE_IMGS = 1
    private val ERR_HOMOGRAPHY_EST_FAIL = 2
    private val ERR_CAMERA_PARAMS_ADJUST_FAIL = 3

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_stitcher, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Handle back button press
        view.findViewById<Button>(R.id.pick_images_button).setOnClickListener{
            imagesMat.clear()
            bmpArray.clear()
            chooseImages()
        }

        view.findViewById<Button>(R.id.stitch_images_button).setOnClickListener{
            Log.d(TAG, "imagesMat.size: " + imagesMat.size)
            for(i in 0 until imagesMat.size){
                val bmp= Bitmap.createBitmap(imagesMat[i].cols(), imagesMat[i].rows(), Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(imagesMat[i], bmp)
                bmpArray.add(bmp)
            }
            val array: Array<Bitmap> = bmpArray.toArray(arrayOfNulls<Bitmap>(imagesMat.size))
            //result = Bitmap.createBitmap(imagesMat[0].cols(), imagesMat[0].rows(), Bitmap.Config.ARGB_8888);
            //Utils.matToBitmap(imagesMat[0], result)
            //processPanorama(array, result!!)
            val wh: IntArray = processPanorama(array)
            when (wh[0]) {
                OK -> {
                    val bitmap = Bitmap.createBitmap(wh[1], wh[2], Bitmap.Config.ARGB_8888)
                    //val result: Int = getBitmap(bitmap)
                    val iv = view.findViewById<ImageView>(R.id.panorama_image_view)
                    iv.setImageBitmap(bitmap)
                }
                ERR_NEED_MORE_IMGS -> {
                    Log.d(TAG,"require more images")
                }
                ERR_HOMOGRAPHY_EST_FAIL -> {
                    Log.d(TAG,"Image does not correspond")
                }
                ERR_CAMERA_PARAMS_ADJUST_FAIL -> {
                    Log.d(TAG,"Image parameter processing failed")
                }
            }
            //val iv = view.findViewById<ImageView>(R.id.panorama_image_view)
            //iv.setImageBitmap(result)
        }

        view.findViewById<Button>(R.id.save_image_button).setOnClickListener {

        }

        view.findViewById<ImageButton>(R.id.back_button).setOnClickListener {

        }

    }

    private fun chooseImages(){
        // Create an intent.
        val openAlbumIntent = Intent()
        openAlbumIntent.type = "image/*"
        openAlbumIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        openAlbumIntent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(openAlbumIntent, "Select Image(s)"), pickImageCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickImageCode && resultCode == Activity.RESULT_OK && data != null) {
            if (data.clipData != null) {
                val count = data.clipData!!.itemCount
                Log.d(TAG, "Number of images:$count")
                for (i in 0 until count) {
                    val imageUri = data.clipData!!.getItemAt(i).uri
                    val imageStream: InputStream? = imageUri?.let { context?.contentResolver?.openInputStream(it) }
                    val bitmap = BitmapFactory.decodeStream(imageStream)
                    val mat = Mat()
                    val bmp32 = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                    Utils.bitmapToMat(bmp32, mat)
                    imagesMat.add(mat)
                }
            }
            else {
                val imageUri = data.data
                val imageStream: InputStream? = imageUri?.let { context?.contentResolver?.openInputStream(it) }
                val bitmap = BitmapFactory.decodeStream(imageStream)
                val mat = Mat()
                val bmp32 = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                Utils.bitmapToMat(bmp32, mat)
                imagesMat.add(mat)
            }
        }
        activity?.contentResolver
    }

    private external fun processPanorama(imagesIn: Array<Bitmap>): IntArray

    interface onStitchResultListener {
        fun onSuccess(bitmap: Bitmap?)
        fun onError(errorMsg: String?)
    }

    private fun getImageUriFromBitmap(context: Context, bitmap: Bitmap): Uri {
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path = MediaStore.Images.Media.insertImage(context.contentResolver, bitmap, "Title", null)
        return Uri.parse(path.toString())
    }

    companion object {

        private const val TAG = "PanoramaCamera"
        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_EXTENSION = ".jpg"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0

        init {
            System.loadLibrary("native-lib")
        }

        /** Helper function used to create a timestamped file */
        private fun createFile(baseFolder: File, format: String, extension: String) =
                File(baseFolder, SimpleDateFormat(format, Locale.JAPAN)
                        .format(System.currentTimeMillis()) + extension)
    }
}