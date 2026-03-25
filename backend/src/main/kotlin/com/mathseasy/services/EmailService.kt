package com.mathseasy.services

import io.github.cdimascio.dotenv.dotenv
import jakarta.mail.*
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.Properties

/**
 * Service for sending email notifications
 */
class EmailService {
    private val logger = LoggerFactory.getLogger(EmailService::class.java)
    private val dotenv = dotenv { ignoreIfMissing = true }
    
    private val smtpHost = dotenv["SMTP_HOST"] ?: "smtp.gmail.com"
    private val smtpPort = dotenv["SMTP_PORT"]?.toIntOrNull() ?: 587
    private val smtpUser = dotenv["SMTP_USER"] ?: ""
    private val smtpPassword = dotenv["SMTP_PASSWORD"] ?: ""
    private val smtpFrom = dotenv["SMTP_FROM"] ?: smtpUser
    private val smtpFromName = dotenv["SMTP_FROM_NAME"] ?: "MathsEasy"
    
    private val isConfigured: Boolean = smtpUser.isNotBlank() && smtpPassword.isNotBlank()
    
    init {
        if (!isConfigured) {
            logger.warn("Email service is not configured. Set SMTP_USER and SMTP_PASSWORD in .env to enable email notifications.")
        } else {
            logger.info("Email service configured with SMTP host: $smtpHost:$smtpPort")
        }
    }
    
    /**
     * Send email notification
     */
    suspend fun sendEmail(
        to: String,
        subject: String,
        htmlBody: String,
        textBody: String? = null
    ): Boolean {
        return withContext(Dispatchers.IO) {
            if (!isConfigured) {
                logger.warn("Email service not configured. Skipping email to $to")
                return@withContext false
            }
            
            try {
                val props = Properties().apply {
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.starttls.enable", "true")
                    put("mail.smtp.host", smtpHost)
                    put("mail.smtp.port", smtpPort.toString())
                    put("mail.smtp.ssl.trust", smtpHost)
                    put("mail.smtp.ssl.protocols", "TLSv1.2")
                }
                
                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(smtpUser, smtpPassword)
                    }
                })
                
                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(smtpFrom, smtpFromName))
                    addRecipient(Message.RecipientType.TO, InternetAddress(to))
                    this.subject = subject
                    
                    // Set both HTML and plain text
                    if (textBody != null) {
                        setContent(createMultipartContent(htmlBody, textBody), "multipart/alternative")
                    } else {
                        setContent(htmlBody, "text/html; charset=utf-8")
                    }
                }
                
                Transport.send(message)
                logger.info("Email sent successfully to $to: $subject")
                true
            } catch (e: Exception) {
                logger.error("Failed to send email to $to: ${e.message}", e)
                false
            }
        }
    }
    
    /**
     * Send learning reminder email
     */
    suspend fun sendLearningReminder(
        toEmail: String,
        userName: String,
        scheduledTime: String,
        minutesBefore: Int
    ): Boolean {
        val subject = "🔔 Reminder: Your learning session starts soon!"
        
        val htmlBody = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px; border-radius: 10px 10px 0 0; text-align: center; }
                    .content { background: #f8f9fa; padding: 30px; border-radius: 0 0 10px 10px; }
                    .button { display: inline-block; padding: 12px 30px; background: #667eea; color: white; text-decoration: none; border-radius: 5px; margin-top: 20px; }
                    .footer { text-align: center; color: #6c757d; font-size: 12px; margin-top: 20px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🚀 MathsEasy</h1>
                        <p style="font-size: 18px; margin: 0;">Time to learn!</p>
                    </div>
                    <div class="content">
                        <p>Hi <strong>$userName</strong>,</p>
                        <p>Your learning session is starting in <strong>$minutesBefore minutes</strong> at <strong>$scheduledTime</strong>!</p>
                        <p>📚 Ready to improve your math skills? Let's make today count!</p>
                        <p style="text-align: center;">
                            <a href="http://localhost:3000/exercises" class="button">Start Learning</a>
                        </p>
                        <p style="margin-top: 30px; font-size: 14px; color: #6c757d;">
                            💡 <strong>Tip:</strong> Consistency is key to mastering mathematics. Even 15 minutes daily makes a difference!
                        </p>
                    </div>
                    <div class="footer">
                        <p>© 2025 MathsEasy. All rights reserved.</p>
                        <p>You're receiving this because you have email notifications enabled in your account settings.</p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
        
        val textBody = """
            Hi $userName,
            
            Your learning session is starting in $minutesBefore minutes at $scheduledTime!
            
            Ready to improve your math skills? Visit http://localhost:3000/exercises to start learning.
            
            Tip: Consistency is key to mastering mathematics. Even 15 minutes daily makes a difference!
            
            © 2025 MathsEasy
        """.trimIndent()
        
        return sendEmail(toEmail, subject, htmlBody, textBody)
    }
    
    /**
     * Send achievement notification email
     */
    suspend fun sendAchievementEmail(
        toEmail: String,
        userName: String,
        achievementTitle: String,
        achievementDescription: String
    ): Boolean {
        val subject = "🏆 Congratulations! You've earned a new achievement!"
        
        val htmlBody = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%); color: white; padding: 30px; border-radius: 10px 10px 0 0; text-align: center; }
                    .achievement { background: white; padding: 30px; border-radius: 10px; margin: 20px 0; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }
                    .trophy { font-size: 60px; text-align: center; }
                    .footer { text-align: center; color: #6c757d; font-size: 12px; margin-top: 20px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🎉 Amazing Achievement!</h1>
                    </div>
                    <div class="achievement">
                        <div class="trophy">🏆</div>
                        <p style="text-align: center; font-size: 20px; margin: 20px 0;"><strong>$achievementTitle</strong></p>
                        <p style="text-align: center; color: #6c757d;">$achievementDescription</p>
                        <p style="text-align: center; margin-top: 30px;">
                            Great job, <strong>$userName</strong>! Keep up the excellent work!
                        </p>
                    </div>
                    <div class="footer">
                        <p>© 2025 MathsEasy. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
        
        return sendEmail(toEmail, subject, htmlBody)
    }
    
    /**
     * Create multipart content with HTML and plain text alternatives
     */
    private fun createMultipartContent(htmlBody: String, textBody: String): jakarta.mail.Multipart {
        val multipart = jakarta.mail.internet.MimeMultipart("alternative")
        
        // Add plain text part
        val textPart = jakarta.mail.internet.MimeBodyPart().apply {
            setText(textBody, "utf-8")
        }
        multipart.addBodyPart(textPart)
        
        // Add HTML part
        val htmlPart = jakarta.mail.internet.MimeBodyPart().apply {
            setContent(htmlBody, "text/html; charset=utf-8")
        }
        multipart.addBodyPart(htmlPart)
        
        return multipart
    }
}


