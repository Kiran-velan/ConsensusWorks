package net.enjoy.springboot.registrationlogin.dto;

import java.util.List;


public class QuestionOptionDto {

    private Long id;
    private List<QuestionDto> questions;
    private String optionValue;

    public String getOptionValue() {
        return optionValue;
    }
    public void setOptionValue(String optionValue) {
        this.optionValue = optionValue;
    }

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public List<QuestionDto> getQuestions() {
        return questions;
    }
    public void setQuestions(List<QuestionDto> questions) {
        this.questions = questions;
    }
    public QuestionOptionDto() {
    }
    public QuestionOptionDto(Long id, String optionValue, List<QuestionDto> questions) {
        this.id = id;
        this.optionValue = optionValue;
        this.questions = questions;
    }


}