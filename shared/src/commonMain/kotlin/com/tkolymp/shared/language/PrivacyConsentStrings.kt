package com.tkolymp.shared.language

data class PrivacyConsentStrings(
    val title: String = "Your privacy",
    val body: String = "Before you continue, please read our Privacy Policy. It explains what data we collect about club members (such as name, contact details and payment status) and how it is used.",
    val readPolicyLinkLabel: String = "Read the Privacy Policy",
    val readTermsLinkLabel: String = "Read the Terms of Use",
    val checkboxLabel: String = "I have read the Privacy Policy and agree to the Terms of Use",
    val continueButton: String = "Continue",
    val errorReportingLabel: String = "Send error reports",
    val errorReportingDescription: String = "Technical error and crash reports (device model, OS and app version — no name or e-mail) are sent to the app developer so bugs can be fixed. You can turn this off at any time in Settings.",
    /** Same as [errorReportingDescription] without the "turn off in Settings" hint, for use inside Settings itself. */
    val errorReportingSettingsDescription: String = "Technical error and crash reports (device model, OS and app version — no name or e-mail) are sent to the app developer so bugs can be fixed.",
    /** Settings section holding the error-report switch and the privacy policy link (withdrawal must be as easy as opting in, GDPR art. 7(3)/21). */
    val settingsSection: String = "Privacy",
    val privacyPolicyRow: String = "Privacy Policy",
    val termsRow: String = "Terms of Use",
)
