package com.mathseasy.services

import com.mathseasy.models.Exercise
import com.mathseasy.models.WrongAnswerAnalysisResponse
import com.mathseasy.models.SimilarExerciseDraft
import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Service for integrating with Deepseek AI API
 */
class DeepseekService {
    
    private val dotenv = dotenv { ignoreIfMissing = true }
    private val apiKey = dotenv["DEEPSEEK_API_KEY"] ?: ""
    private val apiUrl = dotenv["DEEPSEEK_API_URL"] ?: "https://api.deepseek.com/v1"
    
    // Simple in-memory cache to reduce API calls
    private val cache = ConcurrentHashMap<String, CachedResponse>()
    private val cacheExpiryMs = 3600000L // 1 hour
    
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60000 // 60 seconds
            connectTimeoutMillis = 10000 // 10 seconds
        }
    }
    
    /**
     * Generate similar exercises based on an existing exercise
     */
    suspend fun generateSimilarExercises(
        exercise: Exercise,
        count: Int = 1,
        difficulty: String? = null
    ): Result<List<Exercise>> {
        val cacheKey = "similar_${exercise.id}_${difficulty}_$count"
        val cached = getFromCache(cacheKey)
        if (cached != null) {
            logger.info { "Returning cached similar exercises" }
            @Suppress("UNCHECKED_CAST")
            return Result.success(cached as List<Exercise>)
        }
        
        return try {
            val exercises = mutableListOf<Exercise>()
            
            // Generate 'count' number of exercises by calling AI multiple times
            repeat(count) { index ->
                logger.info { "Generating exercise ${index + 1} of $count" }
                val prompt = buildSimilarExercisePrompt(exercise, difficulty ?: exercise.difficulty, index + 1)
                val response = callDeepseekAPI(prompt)
                
                val generatedExercises = parseExercisesFromResponse(response, 1)
                if (generatedExercises.isNotEmpty()) {
                    exercises.add(generatedExercises.first())
                } else {
                    logger.warn { "Failed to parse exercise ${index + 1}, skipping" }
                }
            }
            
            if (exercises.isNotEmpty()) {
                putInCache(cacheKey, exercises)
                Result.success(exercises)
            } else {
                Result.failure(Exception("Failed to generate any exercises"))
            }
        } catch (e: Exception) {
            logger.error(e) { "Error generating similar exercises: ${e.message}" }
            Result.failure(e)
        }
    }
    
    /**
     * Get a hint for an exercise
     */
    suspend fun getHint(
        exercise: Exercise,
        hintLevel: Int = 1,
        userProgress: String? = null
    ): Result<String> {
        val cacheKey = "hint_${exercise.id}_$hintLevel"
        val cached = getFromCache(cacheKey)
        if (cached != null) {
            logger.info { "Returning cached hint" }
            return Result.success(cached as String)
        }
        
        return try {
            val prompt = buildHintPrompt(exercise, hintLevel, userProgress)
            val response = callDeepseekAPI(prompt)
            
            val hint = extractHintFromResponse(response)
            putInCache(cacheKey, hint)
            Result.success(hint)
        } catch (e: Exception) {
            logger.error(e) { "Error generating hint: ${e.message}" }
            Result.failure(e)
        }
    }
    
    /**
     * Get detailed explanation for an exercise
     */
    suspend fun getExplanation(
        exercise: Exercise,
        detailLevel: String = "detailed"
    ): Result<String> {
        val cacheKey = "explain_${exercise.id}_$detailLevel"
        val cached = getFromCache(cacheKey)
        if (cached != null) {
            logger.info { "Returning cached explanation" }
            return Result.success(cached as String)
        }
        
        return try {
            val prompt = buildExplanationPrompt(exercise, detailLevel)
            val response = callDeepseekAPI(prompt)
            
            val explanation = extractExplanationFromResponse(response)
            putInCache(cacheKey, explanation)
            Result.success(explanation)
        } catch (e: Exception) {
            logger.error(e) { "Error generating explanation: ${e.message}" }
            Result.failure(e)
        }
    }
    
    /**
     * Get feedback on user's incorrect answer
     */
    suspend fun getFeedback(
        exercise: Exercise,
        userAnswer: String,
        timeSpent: Int
    ): Result<String> {
        return try {
            val prompt = buildFeedbackPrompt(exercise, userAnswer, timeSpent)
            val response = callDeepseekAPI(prompt)
            
            val feedback = extractFeedbackFromResponse(response)
            Result.success(feedback)
        } catch (e: Exception) {
            logger.error(e) { "Error generating feedback: ${e.message}" }
            Result.failure(e)
        }
    }

    
    /**
     * Analyze a wrong answer: explain the mistake, provide relevant theory, and generate a similar exercise.
     */
    suspend fun analyzeWrongAnswer(
        exercise: Exercise,
        userAnswer: String,
        theoryText: String
    ): Result<WrongAnswerAnalysisResponse> {
        return try {
            val prompt = buildWrongAnswerPrompt(exercise, userAnswer, theoryText)
            val response = callDeepseekAPI(prompt)
            
            val parsed = parseWrongAnswerAnalysis(response)
            Result.success(parsed)
        } catch (e: Exception) {
            logger.error(e) { "Error analyzing wrong answer: ${e.message}" }
            Result.failure(e)
        }
    }
    
    // ==================== PRIVATE HELPER METHODS ====================
    
    /**
     * Call Deepseek API with a prompt
     */
    private suspend fun callDeepseekAPI(prompt: String): String {
        if (apiKey.isBlank()) {
            throw Exception("Deepseek API key not configured")
        }
        
        logger.info { "Calling Deepseek API..." }
        
        // Model name for OpenRouter
        // Free models available:
        // - "deepseek/deepseek-chat" (recommended - good quality)
        // - "meta-llama/llama-3.1-8b-instruct:free"
        // - "mistralai/mistral-7b-instruct:free"
        // - "google/gemini-2.0-flash-exp:free"
        val modelName = if (apiUrl.contains("openrouter")) {
            "deepseek/deepseek-chat"  // Using Deepseek via OpenRouter
        } else {
            "deepseek-chat"
        }
        
        val request = DeepseekRequest(
            model = modelName,
            messages = listOf(
                Message(role = "system", content = "You are a helpful math tutor assistant."),
                Message(role = "user", content = prompt)
            ),
            temperature = 0.7,
            max_tokens = 2000
        )
        
        val response: DeepseekResponse = httpClient.post("$apiUrl/chat/completions") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $apiKey")
            // OpenRouter specific headers (optional but recommended)
            header("HTTP-Referer", "https://mathseasy.app")
            header("X-Title", "MathsEasy")
            setBody(request)
        }.body()
        
        // Check for API error
        if (response.error != null) {
            throw Exception("Deepseek API error: ${response.error.message ?: "Unknown error"}")
        }
        
        // Check for choices
        if (response.choices.isNullOrEmpty()) {
            logger.error { "Deepseek API returned no choices. Response: $response" }
            throw Exception("Empty response from Deepseek API")
        }
        
        return response.choices.firstOrNull()?.message?.content 
            ?: throw Exception("No content in response")
    }
    
    /**
     * Build prompt for generating similar exercises
     */
    private fun buildSimilarExercisePrompt(exercise: Exercise, difficulty: String, variantNumber: Int = 1): String {
        return """
Generate a math exercise similar to the following example:

Topic: ${exercise.topic}
${if (exercise.subtopic != null) "Subtopic: ${exercise.subtopic}" else ""}
Difficulty: $difficulty
Question: ${exercise.content}

Options:
${exercise.options.mapIndexed { i, opt -> "${('A' + i)}) $opt" }.joinToString("\n")}

Correct Answer: ${exercise.correctAnswer}
Explanation: ${exercise.explanation}

Requirements:
1. Create a NEW exercise with similar difficulty and topic (Variant #$variantNumber)
2. Make the question UNIQUE and DIFFERENT from the example
3. Use different numbers, scenarios, or problem variations
4. Question must be in English and clear
5. Provide exactly 4 multiple choice options (A, B, C, D)
6. Mark the correct answer (A, B, C, or D)
7. Provide a detailed step-by-step explanation
8. The exercise should be mathematically accurate
9. Make it educational and engaging

Output ONLY a valid JSON object with this exact structure:
{
  "title": "Brief title for the exercise",
  "content": "The question text",
  "options": ["Option A text", "Option B text", "Option C text", "Option D text"],
  "correctAnswer": "A or B or C or D",
  "explanation": "Detailed step-by-step explanation",
  "topic": "${exercise.topic}",
  "difficulty": "$difficulty"
}

Do not include any text before or after the JSON object.
""".trimIndent()
    }
    
    /**
     * Build prompt for generating hints
     */
    private fun buildHintPrompt(exercise: Exercise, level: Int, userProgress: String?): String {
        val levelDescription = when (level) {
            1 -> "very vague hint that doesn't give away the solution"
            2 -> "moderate hint with some direction"
            3 -> "detailed hint that guides toward the solution without fully revealing it"
            else -> "helpful hint"
        }
        
        return """
Exercise: ${exercise.content}

Options:
${exercise.options.mapIndexed { i, opt -> "${('A' + i)}) $opt" }.joinToString("\n")}

${if (userProgress != null) "User's current thinking: $userProgress\n" else ""}

Provide a $levelDescription (Level $level out of 3) for this math problem.

Rules:
- DO NOT reveal the correct answer
- DO NOT show the complete solution
- Guide the student's thinking process
- Be encouraging and educational
- Keep it concise (2-3 sentences maximum)

Output only the hint text, no JSON, no extra formatting.
""".trimIndent()
    }
    
    /**
     * Build prompt for generating explanations
     */
    private fun buildExplanationPrompt(exercise: Exercise, detailLevel: String): String {
        return """
Exercise: ${exercise.content}

Options:
${exercise.options.mapIndexed { i, opt -> "${('A' + i)}) $opt" }.joinToString("\n")}

Correct Answer: ${exercise.correctAnswer}

Original Explanation: ${exercise.explanation}

Provide a detailed step-by-step explanation for why answer ${exercise.correctAnswer} is correct.

You MUST follow this EXACT format strictly. Use **bold** for all headings:

**Step 1: [title]**
[explanation for step 1]

**Step 2: [title]**
[explanation for step 2]

**Step 3: [title]**
[explanation for step 3]

(add more steps if needed)

**Final Answer:** [state the correct answer clearly]

**Why Other Options Are Incorrect:**
- **[Option X]:** [brief reason why it's wrong]
- **[Option Y]:** [brief reason why it's wrong]
- **[Option Z]:** [brief reason why it's wrong]

Rules:
- Use **bold** (**text**) for all step titles, "Final Answer", and "Why Other Options Are Incorrect"
- Use LaTeX for all math expressions (e.g. ${'$'}x^2${'$'}, \[x = 5\])
- Explain each step clearly with proper mathematical terminology
- Be educational and easy to understand
- Do NOT use markdown headers (#, ##, ###). Use **bold** instead.
- Do NOT wrap the output in JSON. Output only the explanation text.
""".trimIndent()
    }
    
    /**
     * Build prompt for generating feedback
     */
    private fun buildFeedbackPrompt(exercise: Exercise, userAnswer: String, timeSpent: Int): String {
        return """
Exercise: ${exercise.content}

Options:
${exercise.options.mapIndexed { i, opt -> "${('A' + i)}) $opt" }.joinToString("\n")}

Correct Answer: ${exercise.correctAnswer}
User's Answer: $userAnswer (incorrect)
Time Spent: $timeSpent seconds

Provide constructive feedback for the student:

Requirements:
1. Acknowledge the attempt positively
2. Identify the likely misconception or error
3. Provide a gentle correction
4. Give an encouraging tip to improve
5. Keep it friendly and motivational (3-4 sentences)

Output only the feedback text, no JSON.
""".trimIndent()
    }

    /**
     * Build prompt for analyzing a wrong answer
     */
    private fun buildWrongAnswerPrompt(exercise: Exercise, userAnswer: String, theoryText: String): String {
        return """
You are a math tutor. A student answered a question incorrectly.

Exercise: ${exercise.content}

Options:
${exercise.options.mapIndexed { i, opt -> "${('A' + i)}) $opt" }.joinToString("\n")}

Correct Answer: ${exercise.correctAnswer}
Student's Answer: $userAnswer (incorrect)

Relevant Theory:
$theoryText

You must do three things:
1. **Analyze** why the student likely got it wrong (2-3 sentences).
2. **Provide theory** — a short, clear explanation of the concept needed (3-5 sentences).
3. **Generate a similar exercise** for practice.

IMPORTANT FORMATTING RULES:
1. You MUST format ALL mathematical expressions, variables, formulas, equations, or numbers using LaTeX notation:
   - Use ${'$'}...${'$'} for inline math (e.g., ${'$'}x = 5${'$'})
   - Use ${'$'}${'$'}...${'$'}${'$'} for block math equations.
   - Do NOT use \( \) or \[ \]. Only use ${'$'} and ${'$'}${'$'}.
2. For the `explanation` field, you must format it strictly step-by-step with newlines separating each step.
   - Example format: "Step 1: [Short description]\n\n${'$'}${'$'}[Equation]${'$'}${'$'}\n\nStep 2: [Short description]"
   - Mathematical expressions in the explanation MUST be placed on a new line using block math (${'$'}${'$'}...${'$'}${'$'}) so they are centered.
   - Keep explanations very concise.

Output ONLY a valid JSON object with this exact structure:
{
  "analysis": "Why the student got it wrong...",
  "theory": "Short theory explanation...",
  "similarExercise": {
    "title": "Brief title",
    "content": "The question text",
    "options": ["Option A", "Option B", "Option C", "Option D"],
    "correctAnswer": "A or B or C or D",
    "explanation": "Step-by-step explanation",
    "topic": "${exercise.topic}",
    "difficulty": "${exercise.difficulty}"
  }
}

Do not include any text before or after the JSON object.
""".trimIndent()
    }

    /**
     * Parse AI response into WrongAnswerAnalysisResponse
     */
    private fun parseWrongAnswerAnalysis(response: String): WrongAnswerAnalysisResponse {
        val json = Json { ignoreUnknownKeys = true }

        val jsonStart = response.indexOf('{')
        val jsonEnd = response.lastIndexOf('}') + 1

        if (jsonStart < 0 || jsonEnd <= jsonStart) {
            throw Exception("No JSON found in AI response")
        }

        var jsonString = response.substring(jsonStart, jsonEnd)

        // Sanitize invalid JSON escape sequences from AI output
        // Valid JSON escapes: \" \\ \/ \b \f \n \r \t \uXXXX
        // AI often produces invalid ones like \( \) \= \' \+ etc.
        jsonString = jsonString.replace(Regex("""\\(?!["\\/bfnrtu])"""), "\\\\")

        return json.decodeFromString<WrongAnswerAnalysisResponse>(jsonString)
    }

    
    /**
     * Parse exercises from AI response
     */
    private fun parseExercisesFromResponse(response: String, count: Int): List<Exercise> {
        return try {
            val json = Json { ignoreUnknownKeys = true }
            
            // Try to extract JSON from response
            val jsonStart = response.indexOf('{')
            val jsonEnd = response.lastIndexOf('}') + 1
            
            if (jsonStart < 0 || jsonEnd <= jsonStart) {
                logger.error { "No JSON found in response: $response" }
                return emptyList()
            }
            
            val jsonString = response.substring(jsonStart, jsonEnd)
            val parsedExercise = json.decodeFromString<GeneratedExercise>(jsonString)
            
            // Convert to Exercise model
            val exercise = Exercise(
                id = null,
                title = parsedExercise.title,
                description = null,
                content = parsedExercise.content,
                options = parsedExercise.options,
                correctAnswer = parsedExercise.correctAnswer,
                explanation = parsedExercise.explanation,
                difficulty = parsedExercise.difficulty,
                topic = parsedExercise.topic,
                subtopic = parsedExercise.subtopic,
                points = when (parsedExercise.difficulty.lowercase()) {
                    "easy" -> 5
                    "medium" -> 10
                    "hard" -> 20
                    else -> 10
                },
                createdAt = System.currentTimeMillis(),
                createdBy = "ai",
                metadata = mapOf("generated_by" to "deepseek", "model" to "deepseek-chat")
            )
            
            listOf(exercise)
        } catch (e: Exception) {
            logger.error(e) { "Error parsing AI response: ${e.message}\nResponse: $response" }
            emptyList()
        }
    }
    
    private fun extractHintFromResponse(response: String): String {
        return response.trim()
    }
    
    private fun extractExplanationFromResponse(response: String): String {
        return response.trim()
    }
    
    private fun extractFeedbackFromResponse(response: String): String {
        return response.trim()
    }

    
    // ==================== CACHE METHODS ====================
    
    private fun getFromCache(key: String): Any? {
        val cached = cache[key]
        if (cached != null && System.currentTimeMillis() - cached.timestamp < cacheExpiryMs) {
            return cached.data
        }
        cache.remove(key)
        return null
    }
    
    private fun putInCache(key: String, data: Any) {
        cache[key] = CachedResponse(data, System.currentTimeMillis())
    }
    
    fun clearCache() {
        cache.clear()
        logger.info { "AI cache cleared" }
    }
    
    // ==================== DATA CLASSES ====================
    
    @Serializable
    private data class DeepseekRequest(
        val model: String,
        val messages: List<Message>,
        val temperature: Double = 0.7,
        val max_tokens: Int = 2000
    )
    
    @Serializable
    private data class Message(
        val role: String,
        val content: String
    )

    
    @Serializable
    private data class DeepseekResponse(
        val id: String? = null,
        val `object`: String? = null,
        val created: Long? = null,
        val model: String? = null,
        val choices: List<Choice>? = null,
        val error: ApiError? = null
    )
    
    @Serializable
    private data class Choice(
        val index: Int? = null,
        val message: Message,
        val finish_reason: String? = null
    )
    
    @Serializable
    private data class ApiError(
        val message: String? = null,
        val type: String? = null,
        val code: String? = null
    )
    
    @Serializable
    private data class GeneratedExercise(
        val title: String,
        val content: String,
        val options: List<String>,
        val correctAnswer: String,
        val explanation: String,
        val topic: String,
        val difficulty: String,
        val subtopic: String? = null
    )
    
    private data class CachedResponse(
        val data: Any,
        val timestamp: Long
    )
}
