package com.arulm.loaner

object VersionComparator {
    fun isNewer(candidate: String, current: String): Boolean {
        val candidateParts = candidate.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split('.', '-', '_').map { it.toIntOrNull() ?: 0 }
        val count = maxOf(candidateParts.size, currentParts.size)
        for (index in 0 until count) {
            val left = candidateParts.getOrElse(index) { 0 }
            val right = currentParts.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return false
    }
}
