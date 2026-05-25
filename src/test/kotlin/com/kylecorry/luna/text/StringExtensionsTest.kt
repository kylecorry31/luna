package com.kylecorry.luna.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class StringExtensionsTest {

    @ParameterizedTest
    @CsvSource(
        "'', [, ], true",
        "'[]', [, ], true",
        "'[[]]', [, ], true",
        "'[][[]]', [, ], true",
        "'[', [, ], false",
        "']', [, ], false",
        "'[]]', [, ], false",
        "'[[]', [, ], false",
        "'][', [, ], false",
        "'abc', [, ], true",
        "'a[b]c', [, ], true",
        "'a[b[c]d]e', [, ], true",
        "'a[b[c]d]e]', [, ], false",
        "'(())', (, ), true",
        "'(()', (, ), false",
        "')(', (, ), false",
    )
    fun testAreBracketsBalanced(
        input: String,
        openBracket: Char,
        closeBracket: Char,
        expected: Boolean,
    ) {
        assertEquals(expected, input.areBracketsBalanced(openBracket, closeBracket))
    }

}
