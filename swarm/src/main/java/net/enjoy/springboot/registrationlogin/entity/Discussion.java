package net.enjoy.springboot.registrationlogin.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import net.enjoy.springboot.registrationlogin.service.QuestionOptionService;

@Entity
@Table(name = "discussions")
@Getter
@Setter
public class Discussion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long discussionId;

    @Column(unique = true, nullable = false, length = 6)
    private String uniqueCode;

    @Column(nullable = false, length = 100)
    @Size(max = 100, message = "Discussion name cannot exceed 100 characters")
    private String discussionName;

    @Column(nullable = false)
    @Min(value = 1, message = "Number of participants must be at least 1")
    private int numberOfParticipants;

    @Column(nullable = false)
    private int numberOfQuestions;

    @Column(nullable = false, length = 500)
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    @Future(message = "Scheduled time must be in the future")
    @NotNull(message = "Scheduled time cannot be null")
    @Column(nullable = false, columnDefinition = "TIMESTAMP")
    private OffsetDateTime scheduledTime;

    @Column(nullable = false)
    private int duration;

    @OneToMany(mappedBy = "discussion", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @JsonManagedReference
    @JsonIgnore
    private List<Question> questions = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore  // ignore during Discussion caching
    private User user;

    @Column(name = "discussion_type", nullable = false)
    private String discussionType; // "decision-making" or "survey"

    public void setQuestions(List<Question> questions) {
        if (questions != null) {
            this.questions = questions;
            for (Question question : questions) {
                question.setDiscussion(this);  // Ensure proper foreign key linkage
            }
        }
    }

    @PrePersist
    private void prePersist() {
        if (uniqueCode == null || uniqueCode.isBlank()) {
            uniqueCode = generateRandomCode(6);
        }

        validateScheduledTime();
    }

    private void validateScheduledTime() {
        if (scheduledTime == null) {
            throw new IllegalArgumentException("Scheduled time is required.");
        }
        if (scheduledTime.isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new IllegalArgumentException("Scheduled time must be in the future.");
        }
    }

    private String generateRandomCode(int length) {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int index = ThreadLocalRandom.current().nextInt(characters.length());
            code.append(characters.charAt(index));
        }
        return code.toString();
    }

    public void calculateDuration(QuestionOptionService questionOptionService) {
        int totalDuration = 0;

        if (this.questions != null && !this.questions.isEmpty()) {
            for (Question question : this.questions) {
                List<QuestionOption> options = questionOptionService.getOptionsByQuestionId(question.getId());
                int optionCount = (options != null) ? options.size() : 0;

                totalDuration += Math.max(3, (int) Math.ceil((optionCount - 6) / 3.0) * 3 + 3);
            }
        }

        this.duration = totalDuration;
    }

    @Override
    public String toString() {
        return "Discussion{" +
                "discussionId=" + discussionId +
                ", uniqueCode='" + uniqueCode + '\'' +
                ", discussionName='" + discussionName + '\'' +
                ", numberOfParticipants=" + numberOfParticipants +
                ", numberOfQuestions=" + numberOfQuestions +
                ", description='" + description + '\'' +
                ", scheduledTime=" + scheduledTime +
                ", duration=" + duration +
                ", discussionType='" + discussionType + '\'' +
                ", questionsCount=" + (questions != null ? questions.size() : 0) +
                '}';
    }

    // Explicit getter to resolve getDiscussionId() error
    public Long getDiscussionId() {
        return discussionId;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public void setDiscussionName(String discussionName) {
        this.discussionName = discussionName;
    }

    public String getDiscussionName() {
        return discussionName; // Assuming you have a field called discussionName
    }

    public void setNumberOfParticipants(int numberOfParticipants) {
        this.numberOfParticipants = numberOfParticipants;
    }

    public int getNumberOfParticipants() {
        return numberOfParticipants;
    }

    public void setNumberOfQuestions(int numberOfQuestions) {
        this.numberOfQuestions = numberOfQuestions;
    }

    public int getNumberOfQuestions() {
        return numberOfQuestions;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setScheduledTime(OffsetDateTime scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public OffsetDateTime getScheduledTime() {
        return scheduledTime;
    }

    public List<Question> getQuestions() {
        return this.questions;
    }

    public String getUniqueCode() {
        return uniqueCode;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    public String getDescription() {
        return this.description;
    }

    public String getDiscussionType() {
        return this.discussionType;
    }

    public void setDiscussionType(String discussionType) {
        this.discussionType = discussionType;
    }

}