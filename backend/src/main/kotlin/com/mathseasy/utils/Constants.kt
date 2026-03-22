package com.mathseasy.utils

object Constants {
    // Points Configuration
    const val POINTS_EASY = 5
    const val POINTS_MEDIUM = 10
    const val POINTS_HARD = 20
    const val POINTS_BONUS_SPEED = 5
    
    // Difficulty Levels
    const val DIFFICULTY_EASY = "easy"
    const val DIFFICULTY_MEDIUM = "medium"
    const val DIFFICULTY_HARD = "hard"
    
    // Badge Types
    const val BADGE_TYPE_MILESTONE = "milestone"
    const val BADGE_TYPE_STREAK = "streak"
    const val BADGE_TYPE_POINTS = "points"
    const val BADGE_TYPE_TOPIC_MASTERY = "topic_mastery"
    
    // Badge Rarity
    const val RARITY_COMMON = "common"
    const val RARITY_RARE = "rare"
    const val RARITY_EPIC = "epic"
    const val RARITY_LEGENDARY = "legendary"
    
    // Notification Types
    const val NOTIFICATION_TYPE_REMINDER = "reminder"
    const val NOTIFICATION_TYPE_BADGE = "badge"
    const val NOTIFICATION_TYPE_STREAK = "streak"
    const val NOTIFICATION_TYPE_ACHIEVEMENT = "achievement"
    
    // Time Configuration
    const val STREAK_GRACE_HOURS = 24
    const val NOTIFICATION_DEFAULT_MINUTES_BEFORE = 10
    
    // Firestore Collections
    const val COLLECTION_USERS = "users"
    const val COLLECTION_EXERCISES = "exercises"
    const val COLLECTION_EXERCISE_SETS = "exerciseSets"
    const val COLLECTION_LEARNING_SCHEDULES = "learningSchedules"
    const val COLLECTION_LEARNING_HISTORY = "learningHistory"
    const val COLLECTION_BADGES = "badges"
    const val COLLECTION_USER_BADGES = "userBadges"
    const val COLLECTION_NOTIFICATIONS = "notifications"
    const val COLLECTION_ORDERS = "orders"
    const val COLLECTION_REVIEW_QUEUE = "reviewQueue"
    
    // Main Topics
    const val TOPIC_FOUNDATIONS = "foundations"
    const val TOPIC_LINEAR_ALGEBRA = "linear_algebra"
    const val TOPIC_QUADRATIC = "quadratic"
    const val TOPIC_FUNCTIONS = "functions"
    const val TOPIC_EXPONENTS_LOGARITHMS = "exponents_logarithms"
    const val TOPIC_SEQUENCES = "sequences"
    
    // Subtopics — Foundations
    const val SUBTOPIC_NUMBER_OPERATIONS = "number_operations"
    const val SUBTOPIC_ALGEBRAIC_MANIPULATION = "algebraic_manipulation"
    const val SUBTOPIC_LINEAR_EXPRESSIONS = "linear_expressions"
    
    // Subtopics — Linear Algebra
    const val SUBTOPIC_LINEAR_EQUATIONS = "linear_equations"
    const val SUBTOPIC_SIMULTANEOUS_EQUATIONS = "simultaneous_equations"
    const val SUBTOPIC_LINEAR_INEQUALITIES = "linear_inequalities"
    const val SUBTOPIC_WORD_PROBLEMS_LINEAR = "word_problems_linear"
    
    // Subtopics — Quadratic
    const val SUBTOPIC_COMPLETING_THE_SQUARE = "completing_the_square"
    const val SUBTOPIC_QUADRATIC_FORMULA = "quadratic_formula"
    
    // Subtopics — Functions
    const val SUBTOPIC_DOMAIN_RANGE = "domain_range"
    const val SUBTOPIC_COMPOSITE_FUNCTION = "composite_function"
    const val SUBTOPIC_INVERSE_FUNCTION = "inverse_function"
    
    // Subtopics — Exponents & Logarithms
    const val SUBTOPIC_LAWS_OF_EXPONENTS = "laws_of_exponents"
    const val SUBTOPIC_EXPONENTIAL_EQUATIONS = "exponential_equations"
    const val SUBTOPIC_LOG_LAWS = "log_laws"
    const val SUBTOPIC_LOG_EQUATIONS = "log_equations"
    
    // Subtopics — Sequences
    const val SUBTOPIC_ARITHMETIC_SEQUENCE = "arithmetic_sequence"
    const val SUBTOPIC_GEOMETRIC_SEQUENCE = "geometric_sequence"
    const val SUBTOPIC_WORD_MODELING = "word_modeling"
    
    // Error Codes
    const val ERROR_UNAUTHORIZED = "UNAUTHORIZED"
    const val ERROR_INVALID_INPUT = "INVALID_INPUT"
    const val ERROR_NOT_FOUND = "NOT_FOUND"
    const val ERROR_ALREADY_EXISTS = "ALREADY_EXISTS"
    const val ERROR_INTERNAL_SERVER = "INTERNAL_SERVER_ERROR"
    const val ERROR_RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED"
    const val ERROR_FIREBASE_ERROR = "FIREBASE_ERROR"
    const val ERROR_AI_SERVICE_ERROR = "AI_SERVICE_ERROR"
    
    // Rate Limiting
    const val RATE_LIMIT_AI_REQUESTS = 10
    const val RATE_LIMIT_AI_WINDOW_MINUTES = 60
    const val RATE_LIMIT_GENERAL_REQUESTS = 100
    const val RATE_LIMIT_GENERAL_WINDOW_MINUTES = 15
    
    // Payment / SePay Configuration
    const val ORDER_STATUS_PENDING = "pending"
    const val ORDER_STATUS_PAID = "paid"
    const val ORDER_STATUS_FAILED = "failed"
    
    const val SEPAY_BANK = "MB"
    const val SEPAY_ACCOUNT = "0378313750"
    val SEPAY_API_KEY: String = System.getenv("SEPAY_API_KEY") ?: ""
    
    const val PAYMENT_AMOUNT_FOUNDATIONS = 50000L
}

