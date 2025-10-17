package net.enjoy.springboot.registrationlogin.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuestionDto {

    private Long id;

    private Long discussionId;

    @NotEmpty(message = "Question text is required")
    private String questionText;

    private int position;

    private String answer;

    @NotEmpty(message = "Please provide at least one option.")
    @Size(max = 20, message = "A maximum of 20 options are allowed per question.")
    private List<String> options;

    private Integer persistingOptionsCount = 3;

    private List<String> chatSegments;

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDiscussionId() {
        return discussionId;
    }

    public void setDiscussionId(Long discussionId) {
        this.discussionId = discussionId;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    public int getPosition() {
        return position;
    }
    public void setPosition(int position) {
        this.position = position;
    }

    public String getAnswer() {
        return answer;
    }
    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public Integer getPersistingOptionsCount() {
        return persistingOptionsCount;
    }

    public void setPersistingOptionsCount(Integer persistingOptionsCount) {
        this.persistingOptionsCount = persistingOptionsCount;
    }

    public List<String> getChatSegments() {
        return chatSegments;
    }
    public void setChatSegments(List<String> chatSegments) {
        this.chatSegments = chatSegments;
    }

    // Round-wise scores: roundNum -> (optionText -> score)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<Integer, Map<String, Double>> roundWiseScores;

    public Map<Integer, Map<String, Double>> getRoundWiseScores() {
        return roundWiseScores;
    }
    public void setRoundWiseScores(Map<Integer, Map<String, Double>> roundWiseScores) {
        this.roundWiseScores = roundWiseScores;
    }
}
