package com.tkolymp.shared.language

data class FeedbackStrings(
    val sectionTitle: String = "Feedback",
    val reportBugLabel: String = "Report a bug",
    val suggestFeatureLabel: String = "Suggest a feature",
    val nameLabel: String = "Name",
    val emailLabel: String = "Email",
    val messageLabel: String = "Message",
    val bugMessageHint: String = "Describe what went wrong...",
    val featureMessageHint: String = "Describe your idea...",
    val submit: String = "Send",
    val sending: String = "Sending...",
    val successMessage: String = "Thanks! Your feedback was sent.",
    val errorMessage: String = "Couldn't send feedback. Please try again.",
    val validationError: String = "Please fill in all fields.",
)
