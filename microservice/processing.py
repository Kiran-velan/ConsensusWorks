# processing.py
"""
Human Swarm Consensus Scoring Microservice - PRODUCTION VERSION
Optimized for practical consensus detection with realistic human swarm behavior

MODIFIED: uses Conviction, Coherence, Polarization, Stability, Score Threshold,
Lead Margin, Override Score/Lead intelligently (not just direct checks).
"""

import numpy as np
import math
from collections import deque
from dataclasses import dataclass, field
from typing import List, Dict, Optional
import time
import logging
from logger_utils import get_discussion_logger

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@dataclass
class SwarmState:
    """State maintained across batches within a single round"""
    social_influence: np.ndarray = field(default_factory=lambda: np.zeros(6))
    submission_history: deque = field(default_factory=lambda: deque(maxlen=6))
    score_history: deque = field(default_factory=lambda: deque(maxlen=6))
    last_update: float = field(default_factory=time.time)
    round_start: float = field(default_factory=time.time)
    batch_count: int = 0
    mode: str = "survey"


class HumanSwarmConsensusMicroservice:
    """
    Microservice for human swarm-based consensus scoring
    """

    def __init__(self, config: Dict = None):
        self.config = config or {}
        self.active_loggers = {}

        # Core algorithm params
        self.influence_decay = self.config.get('influence_decay', 0.82)
        self.influence_weight = self.config.get('influence_weight', 0.60)
        self.bt_weight = self.config.get('bt_weight', 0.40)
        self.base_temperature = self.config.get('base_temperature', 1.1)

        # Mode-specific thresholds
        self.decision_making_coherence = self.config.get('decision_making_coherence', 0.60)
        self.survey_coherence = self.config.get('survey_coherence', 0.50)

        # Convergence criteria
        self.min_batches = self.config.get('min_batches', 4)
        self.score_threshold = self.config.get('score_threshold', 75.0)
        self.lead_margin = self.config.get('lead_margin', 12.0)
        self.max_polarization = self.config.get('max_polarization', 0.70)

        # Override bypass for obvious winners
        self.override_score = self.config.get('override_score', 80.0)
        self.override_lead = self.config.get('override_lead', 25.0)

        # Stability & conviction parameters
        self.stability_requirement = self.config.get('stability_requirement', 0.80)
        self.conviction_influence = self.config.get('conviction_influence', 0.5)  # how strongly conviction reduces temp/decay

        self.discussion_states: Dict[int, SwarmState] = {}

        logger.info(f"🐝 Human Swarm Consensus Microservice initialized")
        logger.info(
            f"   Convergence tuned: score≥{self.score_threshold}, lead≥{self.lead_margin}, "
            f"coherence≥{self.decision_making_coherence}/{self.survey_coherence}"
        )

    # -------------------------
    # Public API
    # -------------------------
    def process_batch(
        self,
        discussion_id: int,
        submissions: List[List[float]],
        mode: str = "survey",
        current_time: Optional[float] = None
    ) -> Dict:
        if current_time is None:
            current_time = time.time()

        if mode not in ["decision-making", "survey"]:
            raise ValueError(f"Invalid mode '{mode}'. Must be 'decision-making' or 'survey'")

        discussion_logger = self.get_logger_for_discussion(discussion_id)

        if discussion_id not in self.discussion_states:
            self._initialize_discussion(discussion_id, mode, current_time)
            logger.info(f"🐝 New swarm initialized: {discussion_id} (mode: {mode})")
            discussion_logger.debug(f"🐝 New swarm initialized: {discussion_id} (mode: {mode})")
            congig = self.get_config()
            discussion_logger.debug(f"⚙️ Configuration: {congig}")

        state = self.discussion_states[discussion_id]
        if state.mode != mode:
            discussion_logger.debug(f"🔄 Mode changed for {discussion_id}: {state.mode} → {mode}")
            state.mode = mode

        state.batch_count += 1

        submissions_array = np.array(submissions, dtype=np.float64)
        if submissions_array.ndim != 2 or submissions_array.shape[1] != 6:
            raise ValueError(f"Invalid submission format. Expected (n, 6), got {submissions_array.shape}")

        submissions_array = np.clip(submissions_array, 0, 100)
        discussion_logger.debug(f"📊 Batch {state.batch_count} for {discussion_id}: {len(submissions)} readings.")

        # Step 1: Apply decay (adaptive, modified by conviction later)
        self._apply_swarm_decay(state, current_time)

        # Step 2: Accumulate social influence
        self._accumulate_social_influence(state, submissions_array)
        state.submission_history.append(submissions_array)

        # Step 3: BT strengths
        bt_strengths = self._calculate_weighted_bt(state)

        # Step 4: Combine with adaptive temp (conviction influences temp here)
        final_scores = self._combine_with_swarm_dynamics(state.social_influence, bt_strengths, state)
        state.score_history.append(final_scores.copy())

        # Step 5: Compute metrics (coherence, polarization, conviction, stability, momentum)
        swarm_metrics = self._calculate_swarm_coherence(state, submissions_array, final_scores)

        # Step 6: Convergence check (intelligent use of metrics)
        convergence_result = self._check_swarm_convergence(state, final_scores, swarm_metrics, discussion_id)

        # Step 7: Build response
        response = self._build_response(discussion_id, state, final_scores, convergence_result, swarm_metrics)

        state.last_update = current_time

        if convergence_result['converged']:
            logger.info(
                f"✅ CONVERGED {discussion_id} on option {convergence_result['converged_index']}: "
                f"Score={response['fitnessScores'][convergence_result['converged_index']]:.1f}"
            )
            discussion_logger.debug(
                f"✅ CONVERGED {discussion_id} on option {convergence_result['converged_index']}: "
                f"Score={response['fitnessScores'][convergence_result['converged_index']]:.1f}"
            )

        return response

    def reset_discussion(self, discussion_id: int) -> Dict:
        discussion_logger = self.get_logger_for_discussion(discussion_id)
        if discussion_id in self.discussion_states:
            del self.discussion_states[discussion_id]
            logger.info(f"🔄 Reset swarm: {discussion_id} at time: "
                        f"{time.strftime('%Y-%m-%d %H:%M:%S', time.localtime())}")
            discussion_logger.debug(f"🗑️ Discussion {discussion_id} state cleared at time: "
                                    f"{time.strftime('%Y-%m-%d %H:%M:%S', time.localtime())}")
            return {'status': 'reset', 'message': f'Discussion {discussion_id} reset successfully'}
        else:
            logger.warning(f"⚠️ Attempted to reset non-existent swarm: {discussion_id}")
            return {'status': 'reset', 'message': f'Discussion {discussion_id} did not exist'}

    def get_discussion_state(self, discussion_id: int) -> Optional[Dict]:
        if discussion_id not in self.discussion_states:
            return None
        state = self.discussion_states[discussion_id]
        return {
            'discussion_id': discussion_id,
            'mode': state.mode,
            'batch_count': state.batch_count,
            'time_elapsed': time.time() - state.round_start,
            'social_influence': state.social_influence.tolist(),
            'history_batches': len(state.submission_history),
        }

    def get_logger_for_discussion(self, discussion_id: int):
        if discussion_id not in self.active_loggers:
            self.active_loggers[discussion_id] = get_discussion_logger(discussion_id)
        return self.active_loggers[discussion_id]

    def list_active_discussions(self) -> List[int]:
        return list(self.discussion_states.keys())

    def get_config(self) -> Dict:
        return {
            'influence_decay': self.influence_decay,
            'influence_weight': self.influence_weight,
            'bt_weight': self.bt_weight,
            'base_temperature': self.base_temperature,
            'decision_making_coherence': self.decision_making_coherence,
            'survey_coherence': self.survey_coherence,
            'min_batches': self.min_batches,
            'score_threshold': self.score_threshold,
            'lead_margin': self.lead_margin,
            'max_polarization': self.max_polarization,
            'override_score': self.override_score,
            'override_lead': self.override_lead,
            'stability_requirement': self.stability_requirement,
            'conviction_influence': self.conviction_influence,
        }

    # =========================================================================
    # Core algorithms & helpers
    # =========================================================================
    def _initialize_discussion(self, discussion_id: int, mode: str, current_time: float):
        self.discussion_states[discussion_id] = SwarmState(
            social_influence=np.zeros(6),
            submission_history=deque(maxlen=6),
            score_history=deque(maxlen=6),
            last_update=current_time,
            round_start=current_time,
            batch_count=0,
            mode=mode
        )

    def _apply_swarm_decay(self, state: SwarmState, current_time: float):
        """
        Decay social influence but allow conviction to slow decay:
        - When conviction is high (swarm focused), decay should be slower to stabilize.
        """
        time_elapsed = current_time - state.last_update
        num_periods = time_elapsed / 5.0

        # base adaptive_decay
        if len(state.score_history) >= 3:
            recent_scores = np.array(list(state.score_history)[-3:])
            variance = np.mean(np.var(recent_scores, axis=0))
            variance_factor = np.clip(variance / 40.0, 0.6, 1.8)
            adaptive_decay = self.influence_decay ** variance_factor
        else:
            adaptive_decay = self.influence_decay

        # If conviction known (from last computation), reduce decay when conviction high
        # Use last computed conviction if available:
        conviction = 0.0
        if hasattr(state, "last_conviction"):
            conviction = getattr(state, "last_conviction", 0.0)

        # conviction reduces decay exponentially up to conviction_influence factor
        conviction_effect = 1.0 - (conviction * self.conviction_influence)
        conviction_effect = np.clip(conviction_effect, 0.5, 1.0)
        adaptive_decay = adaptive_decay ** conviction_effect

        decay_factor = adaptive_decay ** num_periods
        state.social_influence *= decay_factor

    def _accumulate_social_influence(self, state: SwarmState, submissions: np.ndarray):
        # slightly stronger early reinforcement without blowing up late dynamics
        total_allocations = np.sum(submissions, axis=0)
        # more sensitive to allocation differences: denominator smaller, multiplier larger
        influence_delta = np.log1p(total_allocations / 6.0) * 14.0
        state.social_influence += influence_delta


    def _calculate_weighted_bt(self, state: SwarmState) -> np.ndarray:
        if len(state.submission_history) == 0:
            return np.ones(6)
        all_submissions = np.vstack(list(state.submission_history))
        wins = np.zeros((6, 6))
        for submission in all_submissions:
            for i in range(6):
                for j in range(6):
                    if i != j:
                        total = submission[i] + submission[j]
                        if total > 0:
                            intensity = total / 100.0
                            win_prob = submission[i] / total
                            wins[i][j] += win_prob * intensity
        strengths = self._solve_bradley_terry(wins)
        return strengths

    def _solve_bradley_terry(self, wins: np.ndarray, max_iter: int = 50, tol: float = 1e-4) -> np.ndarray:
        n = 6
        strengths = np.ones(n)
        for iteration in range(max_iter):
            old_strengths = strengths.copy()
            for i in range(n):
                total_wins = np.sum(wins[i, :])
                denominator = 0.0
                for j in range(n):
                    if i != j:
                        total_comparisons = wins[i][j] + wins[j][i]
                        if total_comparisons > 0:
                            denominator += total_comparisons / (strengths[i] + strengths[j])
                if denominator > 0:
                    strengths[i] = total_wins / denominator
            strengths = strengths / np.mean(strengths)
            if np.max(np.abs(strengths - old_strengths)) < tol:
                break
        return strengths

    def _combine_with_swarm_dynamics(self, influence: np.ndarray, bt_strengths: np.ndarray,
                                     state: SwarmState) -> np.ndarray:
        influence_safe = np.maximum(influence, 1e-10)
        bt_safe = np.maximum(bt_strengths, 1e-10)

        # combine with weights
        combined = (influence_safe ** self.influence_weight) * (bt_safe ** self.bt_weight)

        # moderate contrast amplification — stronger than before to allow tipping
        combined = np.power(combined, 1.6)

        log_scores = np.log(combined + 1e-12)

        # Adaptive temperature: incorporate last known conviction to sharpen when conviction high
        adaptive_temp = self.base_temperature
        if len(state.score_history) >= 3:
            recent_scores = np.array(list(state.score_history)[-3:])
            avg_std = np.mean(np.std(recent_scores, axis=1))
            avg_mean = np.mean(recent_scores)

            # coherence estimate
            coherence_est = 1.0 - np.clip(avg_std / (avg_mean + 1e-10), 0, 1)
            # base temp scaled by coherence
            adaptive_temp = self.base_temperature * (0.35 + 0.65 * (1 - coherence_est))

        # reduce temperature if conviction exists (so high conviction -> lower temp -> amplify winner)
        conviction = getattr(state, "last_conviction", 0.0)
        temp_reduction = 1.0 - (conviction * self.conviction_influence)
        temp_reduction = np.clip(temp_reduction, 0.55, 1.0)
        adaptive_temp = adaptive_temp * temp_reduction

        # softmax
        scaled_scores = log_scores / (adaptive_temp + 1e-12)
        scaled_scores = scaled_scores - np.max(scaled_scores)
        exp_scores = np.exp(scaled_scores)
        softmax_probs = exp_scores / np.sum(exp_scores)
        final_scores = softmax_probs * 100.0
        return final_scores

    def _calculate_swarm_coherence(self, state: SwarmState, current_batch: np.ndarray,
                                   scores: np.ndarray) -> Dict:
        """
        Returns:
            coherence: alignment (0-1)
            polarization: (0-1)
            momentum: (reporting only)
            conviction: (0-1) - inverse entropy (how intense is preference)
            stability: (0-1) - how static are scores across recent batches
        """
        # normalized distribution
        normalized_scores = scores / (np.sum(scores) + 1e-10)

        # entropy-based alignment (0..1)
        entropy = -np.sum(normalized_scores * np.log(normalized_scores + 1e-12))
        max_entropy = np.log(6.0)
        alignment = 1.0 - (entropy / (max_entropy + 1e-12))

        # peak_ratio (dominance of top option) (0..1)
        top_score = float(np.max(scores))
        total_score = float(np.sum(scores)) + 1e-12
        peak_ratio = top_score / total_score

        # conviction: blend of peak_ratio and alignment (peak primary)
        conviction = 0.8 * peak_ratio + 0.2 * alignment
        conviction = float(np.clip(conviction, 0.0, 1.0))

        # polarization: smoother mapping of top-two ratio, less binary
        sorted_scores = np.sort(scores)[::-1]
        top_two_ratio = sorted_scores[1] / (sorted_scores[0] + 1e-12)
        # map into 0..1 but with gentler slope
        polarization = float(np.clip(top_two_ratio * 1.2 - 0.15, 0.0, 1.0))

        # stability: recent change magnitude (0..1)
        stability = 0.0
        if len(state.score_history) >= 3:
            recent_scores = np.array(list(state.score_history)[-3:])
            score_changes = np.mean(np.abs(np.diff(recent_scores, axis=0)))
            stability = 1.0 - np.clip(score_changes / 100.0, 0.0, 1.0)
        else:
            stability = 0.0

        # momentum for reporting only (keep as before)
        momentum = 0.0
        if len(state.score_history) >= 3:
            recent_scores = np.array(list(state.score_history)[-3:])
            winner_idx = np.argmax(scores)
            winner_trajectory = recent_scores[:, winner_idx]
            if len(winner_trajectory) >= 2:
                velocity = np.mean(np.diff(winner_trajectory))
                momentum = np.clip(velocity / 10.0, -1.0, 1.0)

        # coherence: blend alignment and peak dominance, penalized by polarization
        coherence = 0.4 * alignment + 0.6 * (peak_ratio ** 1.1)
        # reduce coherence slightly when polarization is high
        coherence = coherence * (1.0 - 0.3 * polarization)
        coherence = float(np.clip(coherence, 0.0, 1.0))

        # persist conviction for other uses
        state.last_conviction = conviction

        return {
            'coherence': coherence,
            'polarization': float(polarization),
            'momentum': float(momentum),
            'conviction': float(conviction),
            'stability': float(stability)
        }

    def _check_swarm_convergence(self, state: SwarmState, scores: np.ndarray,
                                 swarm_metrics: Dict, discussion_id) -> Dict:
        """
        Intelligent convergence logic combining metrics:
        - Hard override for obvious winners (override_score + override_lead)
        - Otherwise require: min_batches, required_coherence, low_polarization,
          visual_score >= score_threshold, and adaptive lead. Also require
          stability or very high conviction (either/or allows convergence).
        - Additionally, a consensus_confidence score is computed for diagnostics
          and can trigger an early-accept path if extremely confident.
        """

        discussion_logger = self.get_logger_for_discussion(discussion_id)

        # Minimum batch gate
        if state.batch_count < self.min_batches:
            return {'converged': False, 'converged_index': None}

        # Compute ranked info
        max_realistic = 60.0 if state.mode == "decision-making" else 50.0
        sorted_indices = np.argsort(scores)[::-1]
        top_idx = int(sorted_indices[0])
        second_idx = int(sorted_indices[1])
        top_score = float(scores[top_idx])
        second_score = float(scores[second_idx])
        lead_margin = top_score - second_score
        visual_score = min(top_score / max_realistic * 100.0, 100.0)

        # 1) Hard override (obvious winner)
        if visual_score >= self.override_score and lead_margin >= self.override_lead:
            discussion_logger.debug(
                f"⚡ OVERRIDE CONVERGENCE: visual_score={visual_score:.1f} lead={lead_margin:.1f}"
            )
            return {'converged': True, 'converged_index': top_idx}

        # Mode-specific coherence requirement
        required_coherence = self.decision_making_coherence if state.mode == "decision-making" else self.survey_coherence

        # Basic checks
        has_coherence = swarm_metrics['coherence'] >= required_coherence
        low_polarization = swarm_metrics['polarization'] < self.max_polarization
        reaches_threshold = visual_score >= self.score_threshold

        # Adaptive lead: tighter when conviction & stability high, more strict otherwise
        conviction = swarm_metrics.get('conviction', 0.0)
        stability = swarm_metrics.get('stability', 0.0)

        # lead adjustment: range [-30%, +30%] relative to base lead
        # high conviction & stability -> reduce required lead by up to 30%
        lead_modifier = 1.0 - 0.3 * (0.5 * (conviction + stability))  # between 0.85..1.0 roughly
        lead_modifier = np.clip(lead_modifier, 0.7, 1.2)
        adaptive_lead_requirement = self.lead_margin * lead_modifier

        has_lead = lead_margin >= adaptive_lead_requirement

        # Consensus confidence: weighted combination for diagnostics and early accept possibility
        # weights chosen to emphasize coherence & conviction
        consensus_confidence = (
            0.40 * swarm_metrics['coherence'] +
            0.30 * swarm_metrics['conviction'] +
            0.15 * swarm_metrics['stability'] +
            0.15 * (visual_score / 100.0)
        )

        discussion_logger.debug(
            f"🔍 Convergence check [{state.mode}]: coherence={swarm_metrics['coherence']:.3f} "
            f"conviction={conviction:.3f} stability={stability:.3f} "
            f"polarization={swarm_metrics['polarization']:.3f} visual={visual_score:.1f} "
            f"lead={lead_margin:.2f} adaptive_lead_req={adaptive_lead_requirement:.2f} "
            f"conf_conf={consensus_confidence:.3f} momentum={swarm_metrics['momentum']:.3f}"
        )

        # Primary convergence path: conservative (all gates)
        primary_converged = all([has_coherence, low_polarization, reaches_threshold, has_lead])

        # Secondary early-accept: if consensus_confidence extremely high and low polarization
        secondary_converged = (consensus_confidence >= 0.88) and (swarm_metrics['polarization'] < 0.55) and (visual_score >= (self.score_threshold - 5.0))

        # Final decision: require either primary path OR secondary high-confidence path
        converged = primary_converged or secondary_converged

        if converged:
            return {'converged': True, 'converged_index': top_idx}
        else:
            return {'converged': False, 'converged_index': None}

    def _build_response(self, discussion_id: int, state: SwarmState, scores: np.ndarray,
                        convergence_result: Dict, swarm_metrics: Dict) -> Dict:
        discussion_logger = self.get_logger_for_discussion(discussion_id)

        max_realistic = 60.0 if state.mode == "decision-making" else 50.0
        visual_scores = [round(min(score / max_realistic * 100.0, 100.0), 2) for score in scores]

        # include metrics in the response for UI/reporting
        payload = {
            'discussionId': discussion_id,
            'converged': bool(convergence_result['converged']),
            'convergedIndex': convergence_result['converged_index'],
            'fitnessScores': visual_scores,
            'swarmMetrics': swarm_metrics
        }

        discussion_logger.debug(f"🎯 Visual scores: {[float(v) for v in visual_scores]}")
        return payload
