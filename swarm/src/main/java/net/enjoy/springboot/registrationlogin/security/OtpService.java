package net.enjoy.springboot.registrationlogin.security;

import net.enjoy.springboot.registrationlogin.entity.OtpToken;
import net.enjoy.springboot.registrationlogin.repository.OtpTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class OtpService {

    @Autowired
    private OtpTokenRepository otpTokenRepo;

    @Autowired
    private BrevoMailService brevoMailService;

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
        String subject = "ConsensusWorks - One Time Password (OTP)";

        String textContent = "Hello,\n\nYour OTP is: " + otp +
                "\n\nThis code will expire in 10 minutes.\n\nThanks,\nConsensusWorks Team";

        String htmlContent = "<div style='font-family: Arial, sans-serif; line-height: 1.6;'>" +
                "<h2>ConsensusWorks OTP Verification</h2>" +
                "<p>Hello,</p>" +
                "<p>Your one-time password (OTP) is:</p>" +
                "<h3 style='color: #2E7D32;'>" + otp + "</h3>" +
                "<p>This code will expire in <b>10 minutes</b>.</p>" +
                "<br><p>Thanks,<br>ConsensusWorks Team</p>" +
                "</div>";

        brevoMailService.sendEmail(toEmail, subject, htmlContent, textContent);
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
