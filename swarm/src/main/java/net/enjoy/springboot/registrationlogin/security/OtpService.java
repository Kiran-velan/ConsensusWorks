package net.enjoy.springboot.registrationlogin.security;

import jakarta.mail.internet.MimeMessage;
import net.enjoy.springboot.registrationlogin.entity.OtpToken;
import net.enjoy.springboot.registrationlogin.repository.OtpTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.messaging.MessagingException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class OtpService {

    @Autowired
    private OtpTokenRepository otpTokenRepo;

    @Autowired
    private JavaMailSender mailSender;

    public String generateOtp(String email) {
        String otp = String.valueOf(new SecureRandom().nextInt(900000) + 100000); // 6-digit OTP

        OtpToken token = new OtpToken();
        token.setEmail(email);
        token.setOtp(otp);
        token.setExpiry(LocalDateTime.now().plusMinutes(10));
        token.setVerified(false);

        otpTokenRepo.save(token);
        return otp;
    }

    public void sendOtpEmail(String toEmail, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();

            // true = multipart message
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // the actual SMTP authentication credentials (username/password) that JavaMail uses to send the email come from application.properties
            helper.setFrom("yourgmail@gmail.com", "ConsensusWorks Support"); // only sets the From: header in the email.
            helper.setTo(toEmail);
            helper.setSubject("ConsensusWorks - One Time Password (OTP)");

            // ✅ Plain text version (fallback)
            String textContent = "Hello,\n\n" +
                    "Your one-time password (OTP) for ConsensusWorks is: " + otp + "\n\n" +
                    "This code will expire in 10 minutes.\n\n" +
                    "If you didn’t request this, please ignore this email.\n\n" +
                    "Thanks,\nThe ConsensusWorks Team";

            // ✅ HTML version (inbox friendly)
            String htmlContent = "<div style='font-family: Arial, sans-serif; line-height: 1.6;'>" +
                    "<h2>Swarm AI OTP Verification</h2>" +
                    "<p>Hello,</p>" +
                    "<p>Your one-time password (OTP) is:</p>" +
                    "<h3 style='color: #2E7D32;'>" + otp + "</h3>" +
                    "<p>This code will expire in <b>10 minutes</b>.</p>" +
                    "<p>If you did not request this, please ignore this email.</p>" +
                    "<br><p>Thanks,<br>Swarm AI Team</p>" +
                    "</div>";

            // Add both plain text and HTML
            helper.setText(textContent, htmlContent);

            mailSender.send(message);

            System.out.println("✅ OTP email sent to: " + toEmail);

        } catch (MessagingException e) {
            System.err.println("❌ Failed to send OTP email: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("❌ Unexpected error: " + e.getMessage());
        }
    }

    public boolean verifyOtp(String email, String otp) {
        OtpToken otpToken = otpTokenRepo.findByEmail(email);
        if (otpToken == null) return false;
        if (otpToken.getExpiry().isBefore(LocalDateTime.now())) return false;
        if (!otpToken.getOtp().equals(otp)) return false;

        otpToken.setVerified(true);  // mark verified
        otpTokenRepo.save(otpToken);

        return true;
    }

    public boolean isEmailVerified(String email) {
        OtpToken token = otpTokenRepo.findByEmail(email);
        return token != null && token.isVerified();
    }

    public void cleanup(String email) {
        OtpToken token = otpTokenRepo.findByEmail(email);
        if (token != null) {
            otpTokenRepo.delete(token);
        }
    }

}
