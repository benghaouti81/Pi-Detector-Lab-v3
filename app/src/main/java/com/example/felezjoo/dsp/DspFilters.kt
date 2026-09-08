package com.example.felezjoo.dsp

import com.example.felezjoo.models.FilterType
import kotlin.math.abs

object DspFilters {

    fun applyFilter(raw: IntArray, filterType: FilterType): DoubleArray {
        val size = raw.size
        val output = DoubleArray(size)

        when (filterType) {
            FilterType.NONE -> {
                for (i in 0 until size) output[i] = raw[i].toDouble()
            }
            FilterType.MOVING_AVERAGE_3 -> {
                for (i in 0 until size) {
                    val prev = if (i > 0) raw[i - 1] else raw[i]
                    val curr = raw[i]
                    val next = if (i < size - 1) raw[i + 1] else raw[i]
                    output[i] = (prev + curr + next) / 3.0
                }
            }
            FilterType.MOVING_AVERAGE_5 -> {
                for (i in 0 until size) {
                    var sum = 0.0
                    var count = 0
                    for (k in -2..2) {
                        val idx = (i + k).coerceIn(0, size - 1)
                        sum += raw[idx]
                        count++
                    }
                    output[i] = sum / count
                }
            }
            FilterType.MEDIAN_3 -> {
                for (i in 0 until size) {
                    val a = if (i > 0) raw[i - 1] else raw[i]
                    val b = raw[i]
                    val c = if (i < size - 1) raw[i + 1] else raw[i]
                    output[i] = medianOf3(a, b, c).toDouble()
                }
            }
            FilterType.MEDIAN_5 -> {
                for (i in 0 until size) {
                    val window = IntArray(5) { k ->
                        raw[(i + k - 2).coerceIn(0, size - 1)]
                    }
                    window.sort()
                    output[i] = window[2].toDouble()
                }
            }
            FilterType.EXPONENTIAL_IIR -> {
                val alpha = 0.35
                var current = raw[0].toDouble()
                for (i in 0 until size) {
                    current = alpha * raw[i] + (1.0 - alpha) * current
                    output[i] = current
                }
            }
            FilterType.SAVITZKY_GOLAY -> {
                // 5-point quadratic Savitzky-Golay smoothing weights: [-3, 12, 17, 12, -3] / 35
                val weights = intArrayOf(-3, 12, 17, 12, -3)
                for (i in 0 until size) {
                    var sum = 0.0
                    for (k in -2..2) {
                        val idx = (i + k).coerceIn(0, size - 1)
                        sum += raw[idx] * weights[k + 2]
                    }
                    output[i] = (sum / 35.0).coerceAtLeast(0.0)
                }
            }
        }
        return output
    }

    fun applyFilter(data: DoubleArray, filterType: FilterType): DoubleArray {
        val size = data.size
        val output = DoubleArray(size)

        when (filterType) {
            FilterType.NONE -> {
                for (i in 0 until size) output[i] = data[i]
            }
            FilterType.MOVING_AVERAGE_3 -> {
                for (i in 0 until size) {
                    val prev = if (i > 0) data[i - 1] else data[i]
                    val curr = data[i]
                    val next = if (i < size - 1) data[i + 1] else data[i]
                    output[i] = (prev + curr + next) / 3.0
                }
            }
            FilterType.MOVING_AVERAGE_5 -> {
                for (i in 0 until size) {
                    var sum = 0.0
                    var count = 0
                    for (k in -2..2) {
                        val idx = (i + k).coerceIn(0, size - 1)
                        sum += data[idx]
                        count++
                    }
                    output[i] = sum / count
                }
            }
            FilterType.MEDIAN_3 -> {
                for (i in 0 until size) {
                    val a = if (i > 0) data[i - 1] else data[i]
                    val b = data[i]
                    val c = if (i < size - 1) data[i + 1] else data[i]
                    output[i] = medianOf3Double(a, b, c)
                }
            }
            FilterType.MEDIAN_5 -> {
                for (i in 0 until size) {
                    val window = DoubleArray(5) { k ->
                        data[(i + k - 2).coerceIn(0, size - 1)]
                    }
                    window.sort()
                    output[i] = window[2]
                }
            }
            FilterType.EXPONENTIAL_IIR -> {
                val alpha = 0.35
                var current = data[0]
                for (i in 0 until size) {
                    current = alpha * data[i] + (1.0 - alpha) * current
                    output[i] = current
                }
            }
            FilterType.SAVITZKY_GOLAY -> {
                val weights = intArrayOf(-3, 12, 17, 12, -3)
                for (i in 0 until size) {
                    var sum = 0.0
                    for (k in -2..2) {
                        val idx = (i + k).coerceIn(0, size - 1)
                        sum += data[idx] * weights[k + 2]
                    }
                    output[i] = sum / 35.0
                }
            }
        }
        return output
    }

    private fun medianOf3Double(a: Double, b: Double, c: Double): Double {
        return when {
            (a in b..c) || (a in c..b) -> a
            (b in a..c) || (b in c..a) -> b
            else -> c
        }
    }

    private fun medianOf3(a: Int, b: Int, c: Int): Int {
        return when {
            (a in b..c) || (a in c..b) -> a
            (b in a..c) || (b in c..a) -> b
            else -> c
        }
    }
}
