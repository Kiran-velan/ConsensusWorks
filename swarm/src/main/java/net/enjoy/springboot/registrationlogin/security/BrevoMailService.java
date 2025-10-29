package net.enjoy.springboot.registrationlogin.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sendinblue.ApiClient;
import sendinblue.Configuration;
import sendinblue.auth.ApiKeyAuth;
import sibApi.TransactionalEmailsApi;
import sibModel.CreateSmtpEmail;
import sibModel.SendSmtpEmail;
import sibModel.SendSmtpEmailSender;
import sibModel.SendSmtpEmailTo;

import java.util.*;

@Service
public class BrevoMailService {

    @Value("${brevo.api.key}")
    private String apiKey;

    @Value("${brevo.sender.email}")
    private String senderEmail;

    @Value("${brevo.sender.name}")
    private String senderName;

    public void sendEmail(String toEmail, String subject, String htmlContent, String textContent) {
        try {
            // Initialize Brevo client
            ApiClient defaultClient = Configuration.getDefaultApiClient();
            ApiKeyAuth apiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("api-key");
            apiKeyAuth.setApiKey(apiKey);

            TransactionalEmailsApi api = new TransactionalEmailsApi();

            // Sender details
            SendSmtpEmailSender sender = new SendSmtpEmailSender()
                    .email(senderEmail)
                    .name(senderName);

            // Recipient(s)
            List<SendSmtpEmailTo> recipients = Collections.singletonList(
                    new SendSmtpEmailTo().email(toEmail)
            );

            // Create email
            SendSmtpEmail email = new SendSmtpEmail()
                    .sender(sender)
                    .to(recipients)
                    .subject(subject)
                    .htmlContent(htmlContent)
                    .textContent(textContent);

            // Send
            CreateSmtpEmail response = api.sendTransacEmail(email);
            System.out.println("✅ Email sent successfully to " + toEmail + " | Message ID: " + response.getMessageId());

        } catch (Exception e) {
            System.err.println("❌ Failed to send email via Brevo: " + e.getMessage());
        }
    }
}