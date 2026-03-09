package com.coreline.cbot.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StockQueryClassifierTest {
    private val classifier = StockQueryClassifier()

    @Test
    fun `classifies market summary question as stock query`() {
        assertTrue(classifier.shouldUseStockProxy("금일 한국주식시장 정리 해줘"))
    }

    @Test
    fun `classifies domestic symbol question as stock query`() {
        assertTrue(classifier.shouldUseStockProxy("005930 최근 흐름 요약해줘"))
    }

    @Test
    fun `classifies stock keyword question as stock query`() {
        assertTrue(classifier.shouldUseStockProxy("삼성전자 최근 뉴스 요약해줘"))
    }

    @Test
    fun `classifies company trend prompt with typo as stock query`() {
        assertTrue(classifier.shouldUseStockProxy("삼성전자 금일 통향 정리 해줘"))
    }

    @Test
    fun `classifies short whitelisted stock prompt as stock query`() {
        assertTrue(classifier.shouldUseStockProxy("삼성전자 어때?"))
    }

    @Test
    fun `classifies alias stock prompt as stock query`() {
        assertTrue(classifier.shouldUseStockProxy("하닉 오늘 어때"))
    }

    @Test
    fun `does not classify vague prompt as stock query`() {
        assertFalse(classifier.shouldUseStockProxy("오늘 어때"))
    }

    @Test
    fun `does not classify generic prompt as stock query`() {
        assertFalse(classifier.shouldUseStockProxy("동작하고 있어?"))
    }

    @Test
    fun `does not classify greeting as stock query`() {
        assertFalse(classifier.shouldUseStockProxy("하이"))
    }
}
