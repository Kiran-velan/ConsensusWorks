package net.enjoy.springboot.registrationlogin.repository;

import net.enjoy.springboot.registrationlogin.entity.RoundScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface RoundScoreRepository extends JpaRepository<RoundScore, Long> {

    Optional<RoundScore> findByQuestionIdAndRoundNumberAndOptionText(Long questionId, int roundNumber, String optionText);

    List<RoundScore> findByQuestionIdAndRoundNumber(Long questionId, int roundNumber);

    List<RoundScore> findByQuestionId(Long questionId);

    List<RoundScore> findByQuestionIdAndRoundNumberOrderByScoreDesc(Long questionId, int roundNumber);
}
