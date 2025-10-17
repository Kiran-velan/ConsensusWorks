package net.enjoy.springboot.registrationlogin.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class OtpToken {

    @Id
    @Column(nullable = false, unique = true)
    private String email;   // Acts as primary key

    @Column(nullable = false)
    private String otp;

    @Column(nullable = false)
    private LocalDateTime expiry;

    @Column(nullable = false)
    private boolean verified = false;

    // --- Getters & Setters ---
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getOtp() { return otp; }
    public void setOtp(String otp) { this.otp = otp; }

    public LocalDateTime getExpiry() { return expiry; }
    public void setExpiry(LocalDateTime expiry) { this.expiry = expiry; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }

    // --- Helper ---
    public boolean isExpired() {
        return expiry.isBefore(LocalDateTime.now());
    }
}
