# processing_unconstrained.py
"""
Human Swarm Consensus Scoring Microservice - UNCONSTRAINED RATINGS VERSION
Handles independent ratings where participants can assign any value (0-100) to each option
without sum constraints.

KEY DIFFERENCE: Treats ratings as independent intensity scores, not allocations.
"""

import numpy as np
import math
from collections import deque
from dataclasses import dataclass, field
from typing import List, Dict, Optional
import time
import logging
from logger_utils import get_discussion_logger

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@dataclass
class SwarmState:
    """State maintained across batches within a single round"""
    social_influence: np.ndarray = field(default_factory=lambda: np.zeros(6))
    submission_history: deque = field(default_factory=lambda: deque(maxlen=12))
    score_history: deque = field(default_factory=lambda: deque(maxlen=12))
    last_update: float = field(default_factory=time.time)
    round_start: float = field(default_factory=time.time)
    batch_count: int = 0


class HumanSwarmConsensusMicroservice:
    """
    Microservice for human swarm-based consensus scoring with UNCONSTRAINED ratings
    Participants can rate each option independently (0-100) without sum restrictions
    """

    def __init__(self, config: Dict = None):
        self.config = config or {}
        self.active_loggers = {}

        # Core algorithm params
        self.influence_decay = self.config.get('influence_decay', 0.85)
        self.influence_weight = self.config.get('influence_weight', 0.60)
        self.bt_weight = self.config.get('bt_weight', 0.40)
        self.base_temperature = self.config.get('base_temperature', 1.1)

        # Unified coherence threshold (0..1)
        self.coherence_threshold = self.config.get('coherence_threshold', 0.60)

        # Convergence criteria (probability space)
        self.min_batches = self.config.get('min_batches', 4)
        self.score_threshold = self.config.get('score_threshold', 0.75)
        self.lead_margin = self.config.get('lead_margin', 0.15)
        self.max_polarization = self.config.get('max_polarization', 0.55)

        # Override bypass for obvious winners
        self.override_score = self.config.get('override_score', 0.85)
        self.override_lead = self.config.get('override_lead', 0.30)

        # Stability & conviction parameters
        self.stability_requirement = self.config.get('stability_requirement', 0.75)
        self.conviction_influence = self.config.get('conviction_influence', 0.5)

        self.discussion_states: Dict[int, SwarmState] = {}

        logger.info(f"🐝 Human Swarm Consensus Microservice initialized (UNCONSTRAINED ratings)")
        logger.info(
            f"Convergence tuned: score>={self.score_threshold}, lead>={self.lead_margin}, coherence>={self.coherence_threshold}"
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

        discussion_logger = self.get_logger_for_discussion(discussion_id)

        if discussion_id not in self.discussion_states:
            self._initialize_discussion(discussion_id, current_time)
            logger.info(f"New swarm initialized: {discussion_id}")
            discussion_logger.debug(f"New swarm initialized: {discussion_id} at time: "
                                    f"{time.strftime('%Y-%m-%d %H:%M:%S', time.localtime())}")
            config = self.get_config()
            discussion_logger.debug(f"⚙️ Configuration: {config}")

        state = self.discussion_states[discussion_id]
        state.batch_count += 1

        submissions_array = np.array(submissions, dtype=np.float64)
        if submissions_array.ndim != 2 or submissions_array.shape[1] != 6:
            raise ValueError(f"Invalid submission format. Expected (n, 6), got {submissions_array.shape}")

        submissions_array = np.clip(submissions_array, 0, 100)
        discussion_logger.debug(f"Batch {state.batch_count} for {discussion_id}: {len(submissions)} readings.")

        # Step 1: Normalize submissions to comparable scale (per-participant)
        normalized_submissions = self._normalize_submissions(submissions_array)

        # Step 2: Apply decay
        self._apply_swarm_decay(state, current_time)

        # Step 3: Accumulate social influence (using normalized data)
        self._accumulate_social_influence(state, normalized_submissions)
        state.submission_history.append(normalized_submissions)

        # Step 4: BT strengths (using normalized pairwise comparisons)
        bt_strengths = self._calculate_weighted_bt(state)

        # Step 5: Combine with adaptive temp
        final_scores = self._combine_with_swarm_dynamics(state.social_influence, bt_strengths, state)
        state.score_history.append(final_scores.copy())

        # Step 6: Compute metrics
        swarm_metrics = self._calculate_swarm_coherence(state, normalized_submissions, final_scores)

        # Step 7: Convergence check
        convergence_result = self._check_swarm_convergence(state, final_scores, swarm_metrics, discussion_id)

        # Step 8: Build response
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
            'coherence_threshold': self.coherence_threshold,
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
    def _normalize_submissions(self, submissions: np.ndarray) -> np.ndarray:
        """
        Normalize unconstrained ratings preserving relative preference AND intensity.
        Uses hybrid intensity metric balancing max, mean, and spread.
        """
        normalized = np.zeros_like(submissions)
        
        for i, rating in enumerate(submissions):
            # Handle all-zero case
            if np.sum(rating) == 0:
                normalized[i] = np.ones(6) / 6.0  # Uniform if no preference
            else:
                # Step 1: Compute HYBRID intensity metric
                # Combines: max (commitment ceiling), mean (engagement), spread (decisiveness)
                
                max_rating = np.max(rating)
                mean_rating = np.mean(rating)
                rating_std = np.std(rating)
                
                # Component 1: Max intensity (0-1)
                max_intensity = max_rating / 100.0
                
                # Component 2: Mean intensity (0-1)  
                mean_intensity = mean_rating / 100.0
                
                # Component 3: Decisiveness from spread (0-1)
                # High std = decisive (clear preference), low std = indecisive
                # Normalize by typical max std (~40 for divergent ratings)
                decisiveness = np.clip(rating_std / 40.0, 0, 1)
                
                # HYBRID INTENSITY: weighted combination
                # 40% max (commitment ceiling)
                # 30% mean (overall engagement)
                # 30% decisiveness (clarity of preference)
                intensity = (
                    0.40 * max_intensity + 
                    0.30 * mean_intensity + 
                    0.30 * decisiveness
                )
                intensity = np.clip(intensity, 0, 1)
                
                # Step 2: Softmax for relative preference
                temp = 18.0  # Balanced temperature
                exp_ratings = np.exp(rating / temp)
                relative_pref = exp_ratings / np.sum(exp_ratings)
                
                # Step 3: Moderate intensity weighting
                # Power=0.4 for gentle curve
                intensity_weight = np.power(intensity, 0.4)
                
                uniform = np.ones(6) / 6.0
                intensity_weighted = intensity_weight * relative_pref + (1 - intensity_weight) * uniform
                
                # Renormalize
                normalized[i] = intensity_weighted / np.sum(intensity_weighted)
        
        return normalized

    def _initialize_discussion(self, discussion_id: int, current_time: float):
        state = SwarmState(
            social_influence=np.zeros(6),
            submission_history=deque(maxlen=6),
            score_history=deque(maxlen=6),
            last_update=current_time,
            round_start=current_time,
            batch_count=0
        )
        state.last_conviction = 0.0  # Initialize explicitly
        self.discussion_states[discussion_id] = state

    def _apply_swarm_decay(self, state: SwarmState, current_time: float):
        """
        Decay social influence with conviction modulation
        """
        time_elapsed = current_time - state.last_update
        num_periods = time_elapsed / 5.0

        # Adaptive decay based on recent variance
        if len(state.score_history) >= 3:
            recent_scores = np.array(list(state.score_history)[-3:])
            variance = np.mean(np.var(recent_scores, axis=0))
            variance_factor = np.clip(variance / 40.0, 0.6, 1.8)
            adaptive_decay = self.influence_decay ** variance_factor
        else:
            adaptive_decay = self.influence_decay

        # Conviction reduces decay
        conviction = getattr(state, "last_conviction", 0.0)
        conviction_effect = 1.0 - (conviction * self.conviction_influence)
        conviction_effect = np.clip(conviction_effect, 0.5, 1.0)
        adaptive_decay = adaptive_decay ** conviction_effect

        decay_factor = adaptive_decay ** num_periods
        state.social_influence *= decay_factor

    def _accumulate_social_influence(self, state: SwarmState, submissions: np.ndarray):
        """
        Accumulate social influence from normalized submissions with rate limiting.
        """
        # Average probability across all participants (0-1 scale)
        avg_probabilities = np.mean(submissions, axis=0)
        
        # Convert to influence delta with appropriate scaling
        # Use log transform for diminishing returns (prevents explosion)
        raw_influence_delta = np.log1p(avg_probabilities * 10.0) * 8.0
        
        # RATE LIMITING: Cap maximum influence gain per batch
        # This prevents sudden jumps from large batches
        max_influence_per_batch = 15.0  # Reasonable accumulation rate
        influence_delta = np.clip(raw_influence_delta, 0, max_influence_per_batch)
        
        state.social_influence += influence_delta

    def _calculate_weighted_bt(self, state: SwarmState) -> np.ndarray:
        """
        Bradley-Terry using normalized submissions (now probability distributions)
        """
        if len(state.submission_history) == 0:
            return np.ones(6)
        
        all_submissions = np.vstack(list(state.submission_history))
        wins = np.zeros((6, 6))
        
        for submission in all_submissions:
            for i in range(6):
                for j in range(6):
                    if i != j:
                        # submission values are now probabilities (0-1)
                        total = submission[i] + submission[j]
                        if total > 0:
                            # Intensity based on total probability mass
                            intensity = total
                            win_prob = submission[i] / total
                            wins[i][j] += win_prob * intensity
        
        strengths = self._solve_bradley_terry(wins)
        return strengths

    def _solve_bradley_terry(self, wins: np.ndarray, max_iter: int = 70, tol: float = 1e-4) -> np.ndarray:
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

        # Combine with weights
        combined = (influence_safe ** self.influence_weight) * (bt_safe ** self.bt_weight)

        # GENTLER contrast amplification to reduce volatility
        combined = np.power(combined, 1.35)  # Reduced from 1.5

        log_scores = np.log(combined + 1e-12)

        # Adaptive temperature with BOUNDS to prevent wild swings
        adaptive_temp = self.base_temperature
        if len(state.score_history) >= 3:
            recent_scores = np.array(list(state.score_history)[-3:])
            avg_std = np.mean(np.std(recent_scores, axis=1))
            avg_mean = np.mean(recent_scores)

            coherence_est = 1.0 - np.clip(avg_std / (avg_mean + 1e-10), 0, 1)
            # Add bounds: temp can't swing too dramatically
            temp_adjustment = (0.35 + 0.65 * (1 - coherence_est))
            temp_adjustment = np.clip(temp_adjustment, 0.7, 1.3)  # ±30% max
            adaptive_temp = self.base_temperature * temp_adjustment

        # Conviction reduces temperature (but bounded)
        conviction = getattr(state, "last_conviction", 0.0)
        temp_reduction = 1.0 - (conviction * self.conviction_influence)
        temp_reduction = np.clip(temp_reduction, 0.65, 1.0)  # Max 35% reduction
        adaptive_temp = adaptive_temp * temp_reduction

        # Softmax
        scaled_scores = log_scores / (adaptive_temp + 1e-12)
        scaled_scores = scaled_scores - np.max(scaled_scores)
        exp_scores = np.exp(scaled_scores)
        raw_probs = exp_scores / np.sum(exp_scores)
        
        # TEMPORAL SMOOTHING: blend with previous scores to reduce jumps
        if len(state.score_history) > 0:
            prev_scores = state.score_history[-1]
            # Exponential moving average: 70% new, 30% old
            # Early batches: more responsive (higher alpha)
            # Later batches: more stable (lower alpha)
            alpha = 0.7 if state.batch_count <= 3 else 0.6
            smoothed_probs = alpha * raw_probs + (1 - alpha) * prev_scores
            # Renormalize after blending
            final_scores = smoothed_probs / np.sum(smoothed_probs)
        else:
            final_scores = raw_probs
        
        return final_scores

    def _calculate_swarm_coherence(self, state: SwarmState, current_batch: np.ndarray,
                                   scores: np.ndarray) -> Dict:
        """
        Calculate swarm metrics for unconstrained ratings.
        """
        normalized_scores = scores / (np.sum(scores) + 1e-12)

        # Entropy-based alignment
        entropy = -np.sum(normalized_scores * np.log(normalized_scores + 1e-12))
        max_entropy = np.log(6.0)
        alignment = 1.0 - (entropy / (max_entropy + 1e-12))

        # Peak ratio (dominance)
        top_score = float(np.max(scores))
        total_score = float(np.sum(scores)) + 1e-12
        peak_ratio = top_score / total_score

        # Conviction: blend of peak_ratio and alignment
        conviction = 0.8 * peak_ratio + 0.2 * alignment
        conviction = float(np.clip(conviction, 0.0, 1.0))

        # Polarization: top-two ratio
        sorted_scores = np.sort(scores)[::-1]
        top_two_ratio = sorted_scores[1] / (sorted_scores[0] + 1e-12)
        polarization = float(np.clip(top_two_ratio * 1.2 - 0.15, 0.0, 1.0))

        # Stability: recent score changes
        stability = 0.0
        if len(state.score_history) >= 3:
            recent_scores = np.array(list(state.score_history)[-3:])
            score_changes = np.mean(np.abs(np.diff(recent_scores, axis=0)))
            stability = 1.0 - np.clip(score_changes / 1.0, 0.0, 1.0)

        # Momentum
        momentum = 0.0
        if len(state.score_history) >= 3:
            recent_scores = np.array(list(state.score_history)[-3:])
            winner_idx = np.argmax(scores)
            winner_trajectory = recent_scores[:, winner_idx]
            if len(winner_trajectory) >= 2:
                velocity = np.mean(np.diff(winner_trajectory))
                momentum = np.clip(velocity / 0.1, -1.0, 1.0)

        # Coherence: blend alignment and dominance, penalized by polarization
        coherence = 0.4 * alignment + 0.6 * (peak_ratio ** 1.1)
        coherence = coherence * (1.0 - 0.3 * polarization)
        coherence = float(np.clip(coherence, 0.0, 1.0))

        # Persist conviction
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
        Convergence logic for unconstrained ratings
        """
        discussion_logger = self.get_logger_for_discussion(discussion_id)

        # Minimum batch gate
        if state.batch_count < self.min_batches:
            return {'converged': False, 'converged_index': None}

        # Compute ranked info
        sorted_indices = np.argsort(scores)[::-1]
        top_idx = int(sorted_indices[0])
        second_idx = int(sorted_indices[1])
        top_score = float(scores[top_idx])
        second_score = float(scores[second_idx])
        lead_margin = top_score - second_score

        # 1) Hard override (obvious winner)
        if top_score >= self.override_score and lead_margin >= self.override_lead:
            discussion_logger.debug(
                f"⚡ OVERRIDE CONVERGENCE: top_score={top_score:.3f} lead={lead_margin:.3f}"
            )
            return {'converged': True, 'converged_index': top_idx}

        # Basic checks
        has_coherence = swarm_metrics['coherence'] >= self.coherence_threshold
        low_polarization = swarm_metrics['polarization'] < self.max_polarization
        reaches_threshold = top_score >= self.score_threshold

        # Adaptive lead requirement
        conviction = swarm_metrics.get('conviction', 0.0)
        stability = swarm_metrics.get('stability', 0.0)

        lead_modifier = 1.0 - 0.25 * (0.6 * conviction + 0.4 * stability)
        lead_modifier = np.clip(lead_modifier, 0.7, 1.2)
        adaptive_lead_requirement = self.lead_margin * lead_modifier

        has_lead = lead_margin >= adaptive_lead_requirement

        # Consensus confidence
        consensus_confidence = (
            0.40 * swarm_metrics['coherence'] +
            0.30 * swarm_metrics['conviction'] +
            0.15 * swarm_metrics['stability'] +
            0.15 * top_score
        )

        discussion_logger.debug(
            f"Convergence check: coherence={swarm_metrics['coherence']:.3f} "
            f"conviction={conviction:.3f} stability={stability:.3f} "
            f"polarization={swarm_metrics['polarization']:.3f} top={top_score:.3f} "
            f"lead={lead_margin:.3f} adaptive_lead_req={adaptive_lead_requirement:.3f} "
            f"conf_conf={consensus_confidence:.3f}"
        )

        # Primary convergence path
        primary_converged = all([has_coherence, low_polarization, reaches_threshold, has_lead])

        # Secondary early-accept
        secondary_converged = (
            consensus_confidence >= 0.85 and 
            swarm_metrics['polarization'] < 0.50 and 
            top_score >= (self.score_threshold - 0.08)
        )
        
        if secondary_converged:
            discussion_logger.debug(
                f"⚡ EARLY-ACCEPT CONVERGENCE: consensus_confidence={consensus_confidence:.3f}"
            )

        converged = primary_converged or secondary_converged

        if converged:
            return {'converged': True, 'converged_index': top_idx}
        else:
            return {'converged': False, 'converged_index': None}

    def _build_response(self, discussion_id: int, state: SwarmState, scores: np.ndarray,
                        convergence_result: Dict, swarm_metrics: Dict) -> Dict:
        discussion_logger = self.get_logger_for_discussion(discussion_id)

        # Convert probabilities to percentages
        fitness_scores = [round(float(s) * 100.0, 2) for s in scores]

        payload = {
            'discussionId': discussion_id,
            'converged': bool(convergence_result['converged']),
            'convergedIndex': convergence_result['converged_index'],
            'fitnessScores': fitness_scores,
            'swarmMetrics': swarm_metrics
        }

        discussion_logger.debug(f"Scores: {[float(v) for v in fitness_scores]}")
        return payload