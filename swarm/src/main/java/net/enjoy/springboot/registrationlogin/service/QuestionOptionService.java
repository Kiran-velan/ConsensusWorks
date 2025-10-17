package net.enjoy.springboot.registrationlogin.service;

import net.enjoy.springboot.registrationlogin.entity.QuestionOption;
import java.util.List;

public interface QuestionOptionService {

    // ✅ Save a single option
    QuestionOption saveOption(QuestionOption option);

    // ✅ Save multiple options in bulk
    void saveAllOptions(List<QuestionOption> options);

    // ✅ Retrieve options for a specific question
    List<QuestionOption> getOptionsByQuestionId(Long questionId);

    // ✅ Delete options for a specific question
    void deleteByQuestionId(Long questionId);

    List<QuestionOption> findByQuestionId(Long questionId);

    void deleteOption(QuestionOption option);
}
