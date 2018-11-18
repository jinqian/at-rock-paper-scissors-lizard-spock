/*
 * Copyright 2017 The Android Things Samples Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.xebia.athandgame.classifier

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*

/**
 * Helper functions for the TensorFlow image classifier.
 */
object TensorFlowHelper {

    private const val IMAGE_MEAN = 128
    private const val IMAGE_STD = 128.0f

    private const val RESULTS_TO_SHOW = 3

    /**
     * Memory-map the model file in Assets.
     */
    @Throws(IOException::class)
    fun loadModelFile(context: Context, modelFile: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFile)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun readLabels(context: Context, labelsFile: String): List<String> {
        val labels = ArrayList<String>()
        labels.addAll(context.assets.open(labelsFile).bufferedReader().readLines())
        return labels
    }

    fun getTopLabel(labelProbArray: Array<FloatArray>,
                    labelList: List<String>): Pair<String, Float> {
        for (i in labelList.indices) {
            Log.d("ImageRecognition", "$i.toString() ${labelList[i]} ${labelProbArray[0][i]}")
        }

        return labelList.asSequence().mapIndexed { i, label ->
            Pair(label, labelProbArray[0][i])
        }.sortedBy { it.second }.last()
    }

    /**
     * Find the best classifications.
     */
    fun getBestResults(labelProbArray: Array<FloatArray>,
                       labelList: List<String>): Collection<Recognition> {
        val sortedLabels = PriorityQueue<Recognition>(RESULTS_TO_SHOW
        ) { lhs, rhs -> java.lang.Float.compare(lhs.confidence!!, rhs.confidence!!) }

        for (i in labelList.indices) {
            val r = Recognition(i.toString(),
                    labelList[i], labelProbArray[0][i])
            sortedLabels.add(r)
            if (r.confidence > 0) {
                Log.d("ImageRecognition", r.toString())
            }
            if (sortedLabels.size > RESULTS_TO_SHOW) {
                sortedLabels.poll()
            }
        }

        val results = ArrayList<Recognition>(RESULTS_TO_SHOW)
        for (r in sortedLabels) {
            results.add(0, r)
        }

        return results
    }

    /**
     * Writes Image data into a `ByteBuffer`.
     */
    fun convertBitmapToByteBuffer(bitmap: Bitmap, intValues: IntArray, imgData: ByteBuffer?) {
        if (imgData == null) {
            return
        }
        imgData.rewind()
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0,
                bitmap.width, bitmap.height)
        // Encode the image pixels into a byte buffer representation matching the expected
        // input of the Tensorflow model
        var pixel = 0
        for (i in 0 until bitmap.width) {
            for (j in 0 until bitmap.height) {
                val `val` = intValues[pixel++]
                imgData.putFloat(((`val` shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                imgData.putFloat(((`val` shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                imgData.putFloat(((`val` and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
            }
        }
    }
}
