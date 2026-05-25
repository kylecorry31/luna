package com.kylecorry.luna.specifications

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoreSpecificationsTest {

    @Test
    fun andSpecificationIsSatisfiedWhenBothSpecsAreSatisfied() {
        val greaterThanFive = object : Specification<Int>() {
            override fun isSatisfiedBy(value: Int): Boolean {
                return value > 5
            }
        }
        val even = object : Specification<Int>() {
            override fun isSatisfiedBy(value: Int): Boolean {
                return value % 2 == 0
            }
        }

        val spec = AndSpecification(greaterThanFive, even)

        assertTrue(spec.isSatisfiedBy(8))
        assertFalse(spec.isSatisfiedBy(7))
        assertFalse(spec.isSatisfiedBy(4))
    }

    @Test
    fun orSpecificationIsSatisfiedWhenEitherSpecIsSatisfied() {
        val greaterThanFive = object : Specification<Int>() {
            override fun isSatisfiedBy(value: Int): Boolean {
                return value > 5
            }
        }
        val even = object : Specification<Int>() {
            override fun isSatisfiedBy(value: Int): Boolean {
                return value % 2 == 0
            }
        }

        val spec = OrSpecification(greaterThanFive, even)

        assertTrue(spec.isSatisfiedBy(8))
        assertTrue(spec.isSatisfiedBy(7))
        assertTrue(spec.isSatisfiedBy(4))
        assertFalse(spec.isSatisfiedBy(3))
    }

    @Test
    fun notSpecificationInvertsTheWrappedSpecResult() {
        val greaterThanFive = object : Specification<Int>() {
            override fun isSatisfiedBy(value: Int): Boolean {
                return value > 5
            }
        }

        val spec = NotSpecification(greaterThanFive)

        assertTrue(spec.isSatisfiedBy(3))
        assertFalse(spec.isSatisfiedBy(6))
    }

    @Test
    fun booleanSpecificationAlwaysReturnsProvidedValue() {
        val alwaysTrue = BooleanSpecification<Int>(true)
        val alwaysFalse = BooleanSpecification<Int>(false)

        assertTrue(alwaysTrue.isSatisfiedBy(1))
        assertTrue(alwaysTrue.isSatisfiedBy(100))

        assertFalse(alwaysFalse.isSatisfiedBy(1))
        assertFalse(alwaysFalse.isSatisfiedBy(100))
    }

    @Test
    fun conditionalSpecificationUsesTrueBranchWhenConditionIsSatisfied() {
        val condition = object : Specification<Int>() {
            override fun isSatisfiedBy(value: Int): Boolean {
                return value > 0
            }
        }
        val ifTrue = object : Specification<Int>() {
            override fun isSatisfiedBy(value: Int): Boolean {
                return value % 2 == 0
            }
        }
        val ifFalse = object : Specification<Int>() {
            override fun isSatisfiedBy(value: Int): Boolean {
                return value < -10
            }
        }

        val spec = ConditionalSpecification(condition, ifTrue, ifFalse)

        assertTrue(spec.isSatisfiedBy(4))
        assertFalse(spec.isSatisfiedBy(3))
    }

    @Test
    fun conditionalSpecificationUsesFalseBranchWhenConditionIsNotSatisfied() {
        val condition = object : Specification<Int>() {
            override fun isSatisfiedBy(value: Int): Boolean {
                return value > 0
            }
        }
        val ifTrue = object : Specification<Int>() {
            override fun isSatisfiedBy(value: Int): Boolean {
                return value % 2 == 0
            }
        }
        val ifFalse = object : Specification<Int>() {
            override fun isSatisfiedBy(value: Int): Boolean {
                return value < -10
            }
        }

        val spec = ConditionalSpecification(condition, ifTrue, ifFalse)

        assertTrue(spec.isSatisfiedBy(-20))
        assertFalse(spec.isSatisfiedBy(-5))
    }

    @Test
    fun specificationHelpersCreateEquivalentComposedSpecifications() {
        val greaterThanFive = object : Specification<Int>() {
            override fun isSatisfiedBy(value: Int): Boolean {
                return value > 5
            }
        }
        val even = object : Specification<Int>() {
            override fun isSatisfiedBy(value: Int): Boolean {
                return value % 2 == 0
            }
        }

        val andSpec = greaterThanFive.and(even)
        val orSpec = greaterThanFive.or(even)
        val notSpec = greaterThanFive.not()

        assertTrue(andSpec.isSatisfiedBy(8))
        assertFalse(andSpec.isSatisfiedBy(7))

        assertTrue(orSpec.isSatisfiedBy(7))
        assertFalse(orSpec.isSatisfiedBy(3))

        assertTrue(notSpec.isSatisfiedBy(2))
        assertFalse(notSpec.isSatisfiedBy(6))
    }
}
