package com.xvadsan.mlcamera

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.xvadsan.blankmvp.base.extensions.getContent
import com.xvadsan.blankmvp.base.extensions.permissions
import com.xvadsan.blankmvp.base.extensions.takePhoto
import com.xvadsan.mlcamera.extensions.FileUtils
import com.xvadsan.mlcamera.extensions.onClick
import com.xvadsan.mlcamera.ml.MobilenetV110224Quant
import kotlinx.android.synthetic.main.fragment_main.*
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

class MainFragment : Fragment(R.layout.fragment_main) {

    private var imageUri: Uri? = null
    private lateinit var bitmap: Bitmap
    private var labels: List<String>? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initUi()
    }

    private fun initUi() {
        labels = requireActivity().application.assets.open("labels_mobilenet_quant_v1_224.txt").bufferedReader().use { it.readText() }.split("\n")
        btnMedia.onClick { onShowMediaDialog() }
    }

    private fun onShowMediaDialog() {
        val dialog = MediaDialog()
        dialog.onSetListener(object : MediaDialog.Listener {
            override fun onClickCamera() = takePhotoAndPermissions()
            override fun onClickGallery() = takePicAndPermissions()
        })
        dialog.isCancelable = true
        dialog.show(requireActivity().supportFragmentManager, TAG_MEDIA_DIALOG)
    }

    private fun takePhotoAndPermissions() {
        permissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA) {
            this.allGranted {
                imageUri = FileProvider.getUriForFile(requireContext(), "${BuildConfig.APPLICATION_ID}.fileprovider", FileUtils().createPhoto(requireContext()))
                takePhoto(imageUri) { success ->
                    if (success == true)
                        setImage(imageUri = imageUri)
                }
            }
            this.denied {
                Toast.makeText(requireContext(), getString(R.string.create_permission_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun takePicAndPermissions() {
        permissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE) {
            this.allGranted {
                getContent(IMAGE_MIME_TYPE) { uri ->
                    imageUri = uri
                    setImage(imageUri = imageUri)
                }
            }
            this.denied {
                Toast.makeText(requireContext(), getString(R.string.create_permission_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onCheckMl() {
        val resize = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        val model = MobilenetV110224Quant.newInstance(requireContext())
        val tbuffer = TensorImage.fromBitmap(resize)
        val byteBuffer = tbuffer.buffer
        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.UINT8)
        inputFeature0.loadBuffer(byteBuffer)
        val outputs = model.process(inputFeature0)
        val outputFeature0 = outputs.outputFeature0AsTensorBuffer
        val max = getMax(outputFeature0.floatArray)
        description.text = labels?.get(max)
        model.close()
    }

    private fun getMax(arr: FloatArray): Int {
        var ind = 0
        var min = 0.0f
        for (i in 0..1000) {
            if (arr[i] > min) {
                min = arr[i]
                ind = i
            }
        }
        return ind
    }

    private fun setImage(imageUri: Uri?) {
        bitmap = MediaStore.Images.Media.getBitmap(requireActivity().contentResolver, imageUri)
        Glide.with(this)
            .asBitmap()
            .load(imageUri)
            .into(image)
        onCheckMl()
    }

    companion object {
        private const val IMAGE_MIME_TYPE = "image/*"
        private const val TAG_MEDIA_DIALOG = "mediaDialog"
    }
}