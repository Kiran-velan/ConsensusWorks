package net.enjoy.springboot.registrationlogin.controller;

import net.enjoy.springboot.registrationlogin.security.BrevoMailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
public class FeedbackController {

    @Value("${feedback.recipient.email}")
    private String recipientEmail;

    @Autowired
    private BrevoMailService brevoMailService;

    @PostMapping("/send-feedback")
    @ResponseBody
    public ResponseEntity<Map<String, String>> sendFeedback(@RequestBody FeedbackRequest feedbackRequest) {
        try {
            String subject = "New Feedback from ConsensusWorks User";
            String htmlContent = "<div><p><b>Email:</b> " + feedbackRequest.getEmail() + "</p>" +
                    "<p><b>Feedback:</b><br>" + feedbackRequest.getMessage() + "</p></div>";
            String textContent = "Email: " + feedbackRequest.getEmail() + "\n\nFeedback: " + feedbackRequest.getMessage();

            brevoMailService.sendEmail(recipientEmail, subject, htmlContent, textContent);

            Map<String, String> response = new HashMap<>();
            response.put("status", "success");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    public static class FeedbackRequest {
        private String email;
        private String message;

        // Getters and setters
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}