package net.enjoy.springboot.registrationlogin.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonBackReference;
import lombok.AllArgsConstructor;

@Entity
@Getter
@Setter
@AllArgsConstructor
@Table(name = "question_options")
public class QuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "option_id")
    private Long optionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false, updatable = false) // Add updatable=false
    @JsonBackReference
    private Question question;

    @NotNull(message = "Option value cannot be null")
    @NotBlank(message = "Option value cannot be empty")
    @Size(max = 200, message = "Option cannot exceed 200 characters")
    @Column(name = "option_value", nullable = false)
    private String optionValue;

    // Constructor for easy creation
    public QuestionOption() {}

    public QuestionOption(Question question, String optionValue) {
        this.question = question;
        this.optionValue = optionValue;
    }

    @Override
    public String toString() {
        return "QuestionOption{" +
                "optionId=" + optionId +
                ", optionValue='" + optionValue + '\'' +
                ", questionId=" + (question != null ? question.getId() : "N/A") +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof QuestionOption)) return false;
        QuestionOption other = (QuestionOption) obj;
        return optionValue != null && optionValue.equals(other.getOptionValue());
    }

    @Override
    public int hashCode() {
        return optionValue != null ? optionValue.hashCode() : 0;
    }

    // ✅ Getters and Setters
    public Long getOptionId() {
        return optionId;
    }

    public void setOptionId(Long optionId) {
        this.optionId = optionId;
    }

    public Question getQuestion() {
        return question;
    }

    public void setQuestion(Question question) {
        this.question = question;
    }

    public String getOptionValue() {
        return optionValue;
    }

    public void setOptionValue(String optionValue) {
        this.optionValue = optionValue;
    }

}