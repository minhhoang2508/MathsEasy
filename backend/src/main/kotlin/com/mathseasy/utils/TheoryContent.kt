package com.mathseasy.utils

/**
 * Short theory summaries for each topic, used as AI context when analyzing wrong answers.
 */
object TheoryContent {

    private val topicTheory = mapOf(
        Constants.TOPIC_FOUNDATIONS to """
            Foundations covers basic number operations (addition, subtraction, multiplication, division),
            order of operations (PEMDAS/BODMAS), algebraic manipulation (simplifying expressions, 
            expanding brackets, factoring), and working with linear expressions. Key concepts include:
            - Distributive property: a(b + c) = ab + ac
            - Combining like terms
            - Working with fractions and decimals
            - Integer properties and divisibility rules
        """.trimIndent(),

        Constants.TOPIC_LINEAR_ALGEBRA to """
            Linear Algebra focuses on solving linear equations and systems of equations. Key topics:
            - Solving single-variable equations: ax + b = c
            - Systems of simultaneous equations (substitution, elimination methods)
            - Linear inequalities and graphing on number lines
            - Word problems involving linear relationships
            - Slope-intercept form: y = mx + b
        """.trimIndent(),

        Constants.TOPIC_QUADRATIC to """
            Quadratic & Polynomial covers second-degree equations and polynomials. Key concepts:
            - Standard form: ax² + bx + c = 0
            - Factoring quadratics
            - Completing the square: (x + p)² = q
            - Quadratic formula: x = (-b ± √(b²-4ac)) / 2a
            - Discriminant: Δ = b² - 4ac (determines number of real roots)
            - Vertex form and graphing parabolas
        """.trimIndent(),

        Constants.TOPIC_FUNCTIONS to """
            Functions covers the fundamental concepts of mathematical functions. Key topics:
            - Domain and range of functions
            - Function notation: f(x)
            - Composite functions: (f ∘ g)(x) = f(g(x))
            - Inverse functions: f⁻¹(x), where f(f⁻¹(x)) = x
            - Vertical line test for functions
            - Transformations of functions (shifts, reflections, stretches)
        """.trimIndent(),

        Constants.TOPIC_EXPONENTS_LOGARITHMS to """
            Exponents & Logarithms covers exponential and logarithmic expressions. Key concepts:
            - Laws of exponents: aᵐ × aⁿ = aᵐ⁺ⁿ, (aᵐ)ⁿ = aᵐⁿ, a⁰ = 1
            - Negative and fractional exponents
            - Exponential equations: aˣ = b
            - Logarithm definition: log_a(b) = c means aᶜ = b
            - Log laws: log(ab) = log a + log b, log(a/b) = log a - log b, log(aⁿ) = n·log a
            - Change of base formula
        """.trimIndent(),

        Constants.TOPIC_SEQUENCES to """
            Sequences covers patterns in ordered lists of numbers. Key concepts:
            - Arithmetic sequences: aₙ = a₁ + (n-1)d, sum Sₙ = n/2(2a₁ + (n-1)d)
            - Geometric sequences: aₙ = a₁ × rⁿ⁻¹, sum Sₙ = a₁(1 - rⁿ)/(1 - r)
            - Finding common difference (d) and common ratio (r)
            - nth term formulas
            - Word problems involving sequences (modeling real-world patterns)
        """.trimIndent()
    )

    /**
     * Get theory text for a topic. Returns a generic fallback if topic is unknown.
     */
    fun getTheory(topic: String): String {
        return topicTheory[topic.lowercase()]
            ?: "General mathematics concepts including algebra, equations, and problem-solving techniques."
    }
}
