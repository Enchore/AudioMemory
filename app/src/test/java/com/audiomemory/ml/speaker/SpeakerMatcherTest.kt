package com.audiomemory.ml.speaker

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SpeakerMatcherTest {

    private lateinit var matcher: SpeakerMatcher

    @Before
    fun setup() {
        matcher = SpeakerMatcher()
    }

    @Test
    fun `cosine similarity of identical vectors is 1`() {
        val v = floatArrayOf(1f, 0f, 0f, 1f)
        assertEquals(1.0f, matcher.cosineSimilarity(v, v), 0.001f)
    }

    @Test
    fun `cosine similarity of orthogonal vectors is 0`() {
        val a = floatArrayOf(1f, 0f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f, 0f)
        assertEquals(0.0f, matcher.cosineSimilarity(a, b), 0.001f)
    }

    @Test
    fun `cosine similarity of opposite vectors is -1`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(-1f, 0f)
        assertEquals(-1.0f, matcher.cosineSimilarity(a, b), 0.001f)
    }

    @Test
    fun `cosine similarity of similar vectors is high`() {
        val a = floatArrayOf(1f, 0.9f, 0.1f, 0.05f)
        val b = floatArrayOf(1f, 0.85f, 0.12f, 0.06f)
        val sim = matcher.cosineSimilarity(a, b)
        assertTrue("Expected high similarity, got $sim", sim > 0.99f)
    }

    @Test
    fun `findBestMatch returns null when no speakers above threshold`() {
        val embedding = floatArrayOf(1f, 0f, 0f, 0f)
        val candidates = listOf(
            SpeakerCandidate(1L, "A", false, floatArrayOf(0f, 1f, 0f, 0f))
        )
        assertNull(matcher.findBestMatch(embedding, candidates))
    }

    @Test
    fun `findBestMatch returns matching speaker above threshold`() {
        val embedding = floatArrayOf(0.9f, 0.1f, 0.05f, 0.02f)
        val candidates = listOf(
            SpeakerCandidate(1L, "Owner", true, floatArrayOf(0.91f, 0.09f, 0.06f, 0.01f)),
            SpeakerCandidate(2L, "Other", false, floatArrayOf(0f, 1f, 0f, 0f)),
        )
        val result = matcher.findBestMatch(embedding, candidates)
        assertNotNull(result)
        assertEquals(1L, result!!.speakerId)
        assertTrue(result.isOwner)
        assertTrue(result.similarity >= SpeakerMatcher.MATCH_THRESHOLD)
    }

    @Test
    fun `findBestMatch prefers highest similarity`() {
        val embedding = floatArrayOf(1f, 0f, 0f, 0f)
        val candidates = listOf(
            SpeakerCandidate(1L, "A", false, floatArrayOf(0.95f, 0.05f, 0f, 0f)),
            SpeakerCandidate(2L, "B", false, floatArrayOf(0.99f, 0.01f, 0f, 0f)),
        )
        val result = matcher.findBestMatch(embedding, candidates)
        assertNotNull(result)
        assertEquals(2L, result!!.speakerId)
    }

    @Test
    fun `averageEmbeddings produces normalized result`() {
        val embs = listOf(
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f),
        )
        val avg = matcher.averageEmbeddings(embs)
        assertEquals(3, avg.size)
        // Result should be L2-normalized
        val norm = kotlin.math.sqrt(avg.sumOf { (it * it).toDouble() }).toFloat()
        assertEquals(1.0f, norm, 0.01f)
    }

    @Test
    fun `averageEmbeddings with single input returns normalized copy`() {
        val embs = listOf(floatArrayOf(3f, 4f))
        val avg = matcher.averageEmbeddings(embs)
        val norm = kotlin.math.sqrt(avg.sumOf { (it * it).toDouble() }).toFloat()
        assertEquals(1.0f, norm, 0.01f)
    }
}
