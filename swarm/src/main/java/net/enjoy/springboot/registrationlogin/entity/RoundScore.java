package net.enjoy.springboot.registrationlogin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "round_scores",
        uniqueConstraints = @UniqueConstraint(columnNames = {"question_id", "round_number", "option_text"})
)
public class RoundScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "round_number", nullable = false)
    private int roundNumber;

    @Column(name = "option_text", nullable = false)
    private String optionText;

    @Column(name = "score", nullable = false)
    private double score;

    public RoundScore() {}

    public RoundScore(Long questionId, int roundNumber, String optionText, double score) {
        this.questionId = questionId;
        this.roundNumber = roundNumber;
        this.optionText = optionText;
        this.score = score;
    }

    // getters and setters
    public Long getId() { return id; }

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public int getRoundNumber() { return roundNumber; }
    public void setRoundNumber(int roundNumber) { this.roundNumber = roundNumber; }

    public String getOptionText() { return optionText; }
    public void setOptionText(String optionText) { this.optionText = optionText; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }
}