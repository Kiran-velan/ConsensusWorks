package net.enjoy.springboot.registrationlogin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PuckCoordinatesDto {
    private Long discussionId;
    private boolean converged; // indicates if convergence happened
    private Integer convergedIndex;
    private List<Double> fitnessScores; // strength/score of the 6 anchor points

    // Getters and setters
    public Long getDiscussionId() { return discussionId; }
    public List<Double> getFitnessScores() { return fitnessScores; }
    public boolean isConverged() { return converged; }
    public Integer getConvergedIndex() { return convergedIndex; }

    public void setDiscussionId(Long discussionId) { this.discussionId = discussionId; }
    public void setConverged(boolean converged) { this.converged = converged; }
    public void setConvergedIndex(Integer convergedIndex) { this.convergedIndex = convergedIndex; }
    public void setFitnessScores(List<Double> fitnessScores) { this.fitnessScores = fitnessScores; }

}