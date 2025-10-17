package net.enjoy.springboot.registrationlogin.dto;

import java.util.List;

public class SliderEntry {
    private List<Double> sliders;
    private List<String> optionLabels;
    private Long questionId;
    private int roundNum;

    public SliderEntry(List<Double> sliders, List<String> optionLabels, Long questionId, int roundNum) {
        this.sliders = sliders;
        this.optionLabels = optionLabels;
        this.questionId = questionId;
        this.roundNum = roundNum;
    }

    public List<Double> getSliders() {
        return sliders;
    }

    public void setSliders(List<Double> sliders) {
        this.sliders = sliders;
    }

    public List<String> getOptionLabels() {
        return optionLabels;
    }

    public void setOptionLabels(List<String> optionLabels) {
        this.optionLabels = optionLabels;
    }
    public Long getQuestionId() {
        return questionId;
    }
    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }
    public int getRoundNum() {
        return roundNum;
    }
    public void setRoundNum(int roundNum) {
        this.roundNum = roundNum;
    }

    @Override
    public String toString() {
        return "SliderEntry{" +
                "sliders=" + sliders +
                ", optionLabels=" + optionLabels +
                ", questionId=" + questionId +
                ", roundNum=" + roundNum +
                '}';
    }
}
