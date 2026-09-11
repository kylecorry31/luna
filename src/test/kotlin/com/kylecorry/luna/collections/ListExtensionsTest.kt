package com.kylecorry.luna.collections

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ListExtensionsTest {

    @Test
    fun combinationsOfSizeZeroIsTheEmptyCombination() {
        assertEquals(listOf(emptyList<Int>()), listOf(1, 2, 3).combinations(0))
        assertEquals(listOf(emptyList<Int>()), emptyList<Int>().combinations(0))
    }

    @Test
    fun combinationsPreservesOriginalOrder() {
        val actual = listOf("a", "b", "c", "d").combinations(2)

        assertEquals(
            listOf(
                listOf("a", "b"),
                listOf("a", "c"),
                listOf("a", "d"),
                listOf("b", "c"),
                listOf("b", "d"),
                listOf("c", "d")
            ),
            actual
        )
    }

    @Test
    fun combinationsOfSizeOneIsEachElement() {
        assertEquals(listOf(listOf(1), listOf(2), listOf(3)), listOf(1, 2, 3).combinations(1))
    }

    @Test
    fun combinationsOfFullSizeIsTheWholeList() {
        assertEquals(listOf(listOf(1, 2, 3)), listOf(1, 2, 3).combinations(3))
    }

    @Test
    fun combinationsLargerThanTheListIsEmpty() {
        assertEquals(emptyList<List<Int>>(), listOf(1, 2).combinations(3))
        assertEquals(emptyList<List<Int>>(), emptyList<Int>().combinations(1))
    }

    @Test
    fun combinationsOfNegativeSizeIsEmpty() {
        assertEquals(emptyList<List<Int>>(), listOf(1, 2).combinations(-1))
        assertEquals(emptyList<List<Int>>(), emptyList<Int>().combinations(-1))
    }

    @Test
    fun combinationsTreatsDuplicatesByPosition() {
        assertEquals(listOf(listOf(1), listOf(1)), listOf(1, 1).combinations(1))
        assertEquals(listOf(listOf(1, 1)), listOf(1, 1).combinations(2))
    }

    @Test
    fun combinationsCountMatchesBinomialCoefficient() {
        val list = (1..6).toList()

        for (size in 0..list.size) {
            assertEquals(binomial(list.size, size), list.combinations(size).size)
        }
    }

    @Test
    fun combinationsAreDistinctSubsequences() {
        val list = listOf(1, 2, 3, 4, 5)

        val actual = list.combinations(3)

        assertEquals(actual.size, actual.distinct().size)
        actual.forEach { combination ->
            assertEquals(3, combination.size)
            assertEquals(combination, list.filter { it in combination })
        }
    }

    private fun binomial(n: Int, k: Int): Int {
        return (1..k).fold(1) { acc, i -> acc * (n - k + i) / i }
    }
}
