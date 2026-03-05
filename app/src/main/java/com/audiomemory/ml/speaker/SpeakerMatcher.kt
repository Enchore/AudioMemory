package com.audiomemory.ml.speaker

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Matches speaker embeddings against known speakers using cosine similarity.
 * Core intelligence for speaker identification and owner recognition.
 */
@Singleton
class SpeakerMatcher @Inject constructor() {

    companion object {
        const val MATCH_THRESHOLD = 0.85f
        const val EMBEDDING_DIM = 192 // d-vector dimension from sherpa-onnx
    }

    /**
     * Compute cosine similarity between two embedding vectors.
     * Returns value in [-1, 1], where 1 = identical.
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Vectors must have same dimension" }
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0f) dotProduct / denominator else 0f
    }

    /**
     * Find the best matching speaker for a given embedding.
     * Returns (speakerId, similarity) or null if no match above threshold.
     */
    fun findBestMatch(
        embedding: FloatArray,
        knownSpeakers: List<SpeakerCandidate>,
    ): MatchResult? {
        var bestMatch: MatchResult? = null

        for (speaker in knownSpeakers) {
            val sim = cosineSimilarity(embedding, speaker.embedding)
            if (sim >= MATCH_THRESHOLD && (bestMatch == null || sim > bestMatch.similarity)) {
                bestMatch = MatchResult(speaker.id, speaker.name, speaker.isOwner, sim)
            }
        }

        return bestMatch
    }

    /**
     * Average multiple embeddings to create a more robust speaker profile.
     * Used during owner enrollment (multiple samples → single embedding).
     */
    fun averageEmbeddings(embeddings: List<FloatArray>): FloatArray {
        require(embeddings.isNotEmpty()) { "Need at least one embedding" }
        val dim = embeddings.first().size
        val avg = FloatArray(dim)
        for (emb in embeddings) {
            for (i in avg.indices) {
                avg[i] += emb[i]
            }
        }
        for (i in avg.indices) {
            avg[i] /= embeddings.size
        }
        // L2 normalize
        val norm = sqrt(avg.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0f) {
            for (i in avg.indices) avg[i] /= norm
        }
        return avg
    }
}

data class SpeakerCandidate(
    val id: Long,
    val name: String,
    val isOwner: Boolean,
    val embedding: FloatArray,
) {
    override fun equals(other: Any?) = (other as? SpeakerCandidate)?.id == id
    override fun hashCode() = id.hashCode()
}

data class MatchResult(
    val speakerId: Long,
    val speakerName: String,
    val isOwner: Boolean,
    val similarity: Float,
)
