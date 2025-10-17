package net.enjoy.springboot.registrationlogin.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DiscussionDto {

    private Long discussionId;  // Required for updating existing discussions
    private String uniqueCode;  // Required for identification

    @NotEmpty(message = "Discussion name is required")
    private String discussionName;

    @NotNull(message = "Number of participants is required")
    private Integer numberOfParticipants;

    @NotNull(message = "Number of questions is required")
    private Integer numberOfQuestions;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description; // Optional but size-limited

    @NotEmpty(message = "Scheduled time is required")
    private String scheduledTime;

    private Integer duration; // Needs to be present for update purposes

    @NotNull(message = "Questions are required")
    private List<QuestionDto> questions;

    private String discussionType;

    // Explicit getters and setters
    public Long getDiscussionId() { return discussionId; }
    public void setDiscussionId(Long discussionId) { this.discussionId = discussionId; }

    public String getUniqueCode() { return uniqueCode; }
    public void setUniqueCode(String uniqueCode) { this.uniqueCode = uniqueCode; }

    public String getDiscussionName() { return discussionName; }
    public void setDiscussionName(String discussionName) { this.discussionName = discussionName; }

    public Integer getNumberOfParticipants() { return numberOfParticipants; }
    public void setNumberOfParticipants(Integer numberOfParticipants) { this.numberOfParticipants = numberOfParticipants; }

    public Integer getNumberOfQuestions() { return numberOfQuestions; }
    public void setNumberOfQuestions(Integer numberOfQuestions) { this.numberOfQuestions = numberOfQuestions; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(String scheduledTime) { this.scheduledTime = scheduledTime; }

    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }

    public List<QuestionDto> getQuestions() { return questions; }
    public void setQuestions(List<QuestionDto> questions) { this.questions = questions; }

    public String getDiscussionType() {return discussionType;}
    public void setDiscussionType(String discussionType) {this.discussionType = discussionType;}
}
