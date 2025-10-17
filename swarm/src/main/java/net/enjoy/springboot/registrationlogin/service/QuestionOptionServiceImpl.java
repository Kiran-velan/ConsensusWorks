package net.enjoy.springboot.registrationlogin.service;

import net.enjoy.springboot.registrationlogin.entity.QuestionOption;
import net.enjoy.springboot.registrationlogin.repository.QuestionOptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QuestionOptionServiceImpl implements QuestionOptionService {

    private final QuestionOptionRepository questionOptionRepository;

    @Autowired
    public QuestionOptionServiceImpl(QuestionOptionRepository questionOptionRepository) {
        this.questionOptionRepository = questionOptionRepository;
    }

    @Override
    public QuestionOption saveOption(QuestionOption option) {
        return questionOptionRepository.save(option);
    }

    @Override
    public void saveAllOptions(List<QuestionOption> options) {
        questionOptionRepository.saveAll(options);
    }

    @Override
    public List<QuestionOption> getOptionsByQuestionId(Long questionId) {
        return questionOptionRepository.findByQuestionId(questionId);
    }

    @Override
    public void deleteByQuestionId(Long questionId) {
        questionOptionRepository.deleteByQuestionId(questionId);
    }

    public List<QuestionOption> findByQuestionId(Long questionId) {
        return questionOptionRepository.findByQuestionId(questionId);
    }

    @Transactional
    public void deleteOption(QuestionOption option) {
        questionOptionRepository.delete(option);
    }

}