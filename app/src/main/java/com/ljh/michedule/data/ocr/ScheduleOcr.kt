package com.ljh.michedule.data.ocr

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private const val TAG = "ScheduleOcr"

data class OcrTextBlock(
    val text: String,
    val centerX: Float,
    val centerY: Float,
    val top: Float,
    val left: Float,
    val width: Float,
    val height: Float
)

object ScheduleOcr {

    suspend fun recognizeBlocks(context: Context, uri: Uri): List<OcrTextBlock> {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(
            KoreanTextRecognizerOptions.Builder().build()
        )
        val result = suspendCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        Log.d(TAG, "OCR full text:\n${result.text}")

        val blocks = mutableListOf<OcrTextBlock>()
        for (textBlock in result.textBlocks) {
            for (line in textBlock.lines) {
                for (i in 0 until line.elements.size) {
                    val element = line.elements[i]
                    val box = element.boundingBox ?: continue
                    blocks.add(
                        OcrTextBlock(
                            text = element.text.trim(),
                            centerX = box.centerX().toFloat(),
                            centerY = box.centerY().toFloat(),
                            top = box.top.toFloat(),
                            left = box.left.toFloat(),
                            width = box.width().toFloat(),
                            height = box.height().toFloat()
                        )
                    )
                }
            }
        }
        Log.d(TAG, "Recognized ${blocks.size} text elements")
        return blocks
    }

    suspend fun recognizeFullText(context: Context, uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(
            KoreanTextRecognizerOptions.Builder().build()
        )
        val result = suspendCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
        return result.text
    }
}
