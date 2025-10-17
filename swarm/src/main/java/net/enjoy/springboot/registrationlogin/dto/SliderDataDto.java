package net.enjoy.springboot.registrationlogin.dto;

import lombok.Data;
import java.util.List;

@Data
public class SliderDataDto {
    private Long discussionId;
    private List<Double> sliders;  // 6 slider values
    private List<String> optionLabels;
    private Long questionId;
    private int roundNum;

    // Getters and setters
    public Long getDiscussionId() { return discussionId; }
    public List<Double> getSliders() { return sliders; }
    public List<String> getOptionLabels() { return optionLabels; }
    public Long getQuestionId() { return questionId; }
    public int getRoundNum() { return roundNum; }

}
