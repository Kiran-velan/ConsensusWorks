package net.enjoy.springboot.registrationlogin.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonBackReference;
import lombok.AllArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@AllArgsConstructor
@Table(name = "questions")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Question text cannot be null")
    @NotBlank(message = "Question text cannot be empty")
    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(nullable = false)
    private int position; // Field to track the order of questions

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonBackReference
    @JoinColumn(name = "discussion_id", nullable = false)
    private Discussion discussion;

    // ✅ One-to-Many Relationship with QuestionOption
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<QuestionOption> options = new ArrayList<>();

    // A field to store final converged answer
    @Column(name = "answer", columnDefinition = "TEXT", nullable = false)
    private String answer = "None";

    @Column(name = "persisting_options_count")
    private Integer persistingOptionsCount = 3;

    // Constructors
    public Question() {}

    public Question(String questionText, Discussion discussion, int position) {
        this.questionText = questionText;
        this.discussion = discussion;
        this.position = position;
    }

    @Override
    public String toString() {
        return "Question{" +
                "id=" + id +
                ", questionText='" + questionText + '\'' +
                ", answer='" + answer + '\'' +
                ", discussionId=" + (discussion != null ? discussion.getDiscussionId() : "N/A") +
                ", position=" + position +
                ", persistingOptionsCount=" + persistingOptionsCount +
                '}';
    }

    // Getter for questionText (already exists)
    public String getQuestionText() {
        return this.questionText;
    }
    // Setter for questionText
    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
    // Getter for question ID
    public Long getId() {
        return this.id;
    }

    // Setter for discussion
    public void setDiscussion(Discussion discussion) {
        this.discussion = discussion;
    }

    // Getter for discussion
    public Discussion getDiscussion() {
        return this.discussion;
    }

    public List<QuestionOption> getOptions() {
        return options;
    }

    public void setOptions(List<QuestionOption> options) {
        this.options = options;
    }

    // Helper method to add an option
    public void addOption(QuestionOption option) {
        options.add(option);
        option.setQuestion(this);
    }

    // Helper method to remove an option
    public void removeOption(QuestionOption option) {
        options.remove(option);
        option.setQuestion(null);
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
}