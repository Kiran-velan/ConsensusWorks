"""
Consensus Scoring Microservice - PRODUCTION VERSION
Designed for incremental batch processing with state persistence
"""

import numpy as np
import math
from collections import deque
from dataclasses import dataclass, field
from typing import List, Dict, Optional
import time
import logging

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@dataclass
class ConvictionState:
    """State maintained across batches within a single round"""
    conviction: np.ndarray = field(default_factory=lambda: np.zeros(6))
    submission_history: deque = field(default_factory=lambda: deque(maxlen=12))
    score_history: deque = field(default_factory=lambda: deque(maxlen=12))
    last_update: float = field(default_factory=time.time)
    round_start: float = field(default_factory=time.time)
    batch_count: int = 0
    mode: str = "survey"  # Track mode per discussion


class ConsensusScoreMicroservice:
    """
    Microservice for real-time consensus scoring
    
    KEY DESIGN PRINCIPLES:
    1. Processes incremental batches (new submissions only, not cumulative)
    2. Maintains internal state per discussion_id (conviction + history)
    3. Applies time decay to old state before adding new submissions
    4. Returns scores (0-100) that map to pizza sector gradient depth
    5. State persists within a round, reset between rounds via reset_discussion()
    6. Supports two modes: "decision-making" (sum=100) and "survey" (no constraint)
    
    ALGORITHM:
    - Conviction Voting: Accumulates support with sqrt diminishing returns
    - Bradley-Terry: Resolves preference cycles via pairwise comparisons
    - Softmax Normalization: Ensures 0-100 range with good separation
    - Time Decay: Recent submissions weighted higher (configurable)
    - Visual Scaling: Scales internal scores to full 0-100 visual range
    
    MODE-SPECIFIC CONVERGENCE CRITERIA:
    - Decision-making: winner_score_threshold=80, winner_lead_margin=20
    - Survey: winner_score_threshold=80, winner_lead_margin=15
    """
    
    def __init__(self, config: Dict = None):
        """
        Initialize microservice
        
        Config parameters:
            conviction_decay: Decay factor per batch (default: 0.85)
            conviction_weight: Weight for conviction in final score (default: 0.7)
            bt_weight: Weight for Bradley-Terry (default: 0.3)
            softmax_temperature: Controls score spread (default: 2.0)
            convergence_threshold: Stability needed (default: 0.70)
            
            Mode-specific (can override defaults):
            decision_making_score_threshold: Score for decision-making (default: 80.0)
            decision_making_lead_margin: Lead for decision-making (default: 20.0)
            survey_score_threshold: Score for survey (default: 80.0)
            survey_lead_margin: Lead for survey (default: 15.0)
        """
        self.config = config or {}
        
        # Algorithm parameters
        self.conviction_decay = self.config.get('conviction_decay', 0.85)
        self.conviction_weight = self.config.get('conviction_weight', 0.7)
        self.bt_weight = self.config.get('bt_weight', 0.3)
        self.softmax_temperature = self.config.get('softmax_temperature', 2.0)
        
        # Shared convergence parameter
        self.convergence_threshold = self.config.get('convergence_threshold', 0.70)
        
        # Mode-specific convergence parameters
        self.decision_making_score_threshold = self.config.get('decision_making_score_threshold', 80.0)
        self.decision_making_lead_margin = self.config.get('decision_making_lead_margin', 20.0)
        self.survey_score_threshold = self.config.get('survey_score_threshold', 80.0)
        self.survey_lead_margin = self.config.get('survey_lead_margin', 15.0)
        
        # State storage (persists across batches within same round)
        self.discussion_states: Dict[int, ConvictionState] = {}
        
        logger.info(f"Microservice initialized with config: {self.config}")
    
    
    def process_batch(self, 
                     discussion_id: int, 
                     submissions: List[List[float]],
                     mode: str = "survey",
                     current_time: Optional[float] = None) -> Dict:
        """
        Process an incremental batch of new submissions
        
        IMPORTANT: 
        - `submissions` contains ONLY new submissions since last batch
        - NOT cumulative (main app deletes most of the processed submissions)
        
        Args:
            discussion_id: Unique identifier for this discussion/round
            submissions: NEW submissions only [[v1,v2,v3,v4,v5,v6], ...] Each v_i ∈ [0, 100]
            mode: "decision-making" (sum=100 constraint) or "survey" (no constraint)
            current_time: Optional timestamp (defaults to time.time())
        
        Returns:
            {
                'discussionId': int,
                'converged': bool,
                'convergedIndex': int|None (0-5),
                'fitnessScores': [float],  # 0-100, scaled for visual display
            }
        """
        if current_time is None:
            current_time = time.time()
        
        # Validate mode
        if mode not in ["decision-making", "survey"]:
            raise ValueError(f"Invalid mode '{mode}'. Must be 'decision-making' or 'survey'")
        
        # Initialize or get existing state
        if discussion_id not in self.discussion_states:
            self._initialize_discussion(discussion_id, mode, current_time)
            logger.info(f"Initialized new discussion: {discussion_id} (mode: {mode})")
        
        state = self.discussion_states[discussion_id]
        
        # Update mode if it changed (allow mode switching mid-discussion)
        if state.mode != mode:
            logger.info(f"Mode changed for {discussion_id}: {state.mode} → {mode}")
            state.mode = mode
        
        state.batch_count += 1
        
        # Validate submissions
        submissions_array = np.array(submissions, dtype=np.float64)
        
        if submissions_array.ndim != 2 or submissions_array.shape[1] != 6:
            raise ValueError(
                f"Invalid submission format. Expected shape (n, 6), got {submissions_array.shape}"
            )
        
        # Clip to valid range
        submissions_array = np.clip(submissions_array, 0, 100)
        
        logger.debug(
            f"Batch {state.batch_count} for {discussion_id} ({mode}): "
            f"{len(submissions)} new submissions"
        )
        
        # STEP 1: Apply time decay to existing state
        self._apply_decay(state, current_time)
        
        # STEP 2: Add new submissions to conviction
        self._accumulate_conviction(state, submissions_array)
        state.submission_history.append(submissions_array)
        
        # STEP 3: Calculate Bradley-Terry strengths from history
        bt_strengths = self._calculate_bt_strengths(state)
        
        # STEP 4: Combine conviction + BT and normalize
        final_scores = self._combine_and_normalize(state.conviction, bt_strengths, state)
        
        # STEP 5: Store score history
        state.score_history.append(final_scores.copy())
        
        # STEP 6: Check convergence (mode-aware, uses internal scores)
        convergence_result = self._check_convergence(state, final_scores)
        
        # STEP 7: Build response (with visual scaling)
        response = self._build_response(discussion_id, state, final_scores, convergence_result)
        
        # Update timestamp
        state.last_update = current_time
        
        logger.info(
            f"Processed batch for {discussion_id} ({mode}): "
            f"Internal scores={[f'{s:.1f}' for s in final_scores]}, "
            f"Visual scores={[f'{s:.1f}' for s in response['fitnessScores']]}, "
            f"Converged={convergence_result['converged']}, "
            f"ConvergedIndex={convergence_result['converged_index']}"
        )
        
        return response
    
    
    def reset_discussion(self, discussion_id: int) -> Dict:
        """Reset discussion state"""
        if discussion_id in self.discussion_states:
            del self.discussion_states[discussion_id]
            logger.info(f"Reset discussion: {discussion_id}")
            return {
                'status': 'reset',
                'message': f'Discussion {discussion_id} reset successfully'
            }
        else:
            logger.warning(f"Attempted to reset non-existent discussion: {discussion_id}")
            return {
                'status': 'reset',
                'message': f'Discussion {discussion_id} did not exist (no action needed)'
            }
    

    # =========================================================================
    # EXTRA FUNCTIONS FOR MANAGEMENT/DEBUGGING
    # =========================================================================
    
    def get_discussion_state(self, discussion_id: int) -> Optional[Dict]:
        """
        Get current state for monitoring/debugging
        
        Returns:
            State info or None if discussion doesn't exist
        """
        if discussion_id not in self.discussion_states:
            return None
        
        state = self.discussion_states[discussion_id]
        
        return {
            'discussion_id': discussion_id,
            'mode': state.mode,
            'batch_count': state.batch_count,
            'time_elapsed': time.time() - state.round_start,
            'conviction': state.conviction.tolist(),
            'history_batches': len(state.submission_history),
            'last_update': state.last_update,
            'round_start': state.round_start
        }
    
    
    def list_active_discussions(self) -> List[int]:
        """
        Get list of all active discussion IDs
        
        Returns:
            List of discussion_id integers
        """
        return list(self.discussion_states.keys())
    
    
    def get_config(self) -> Dict:
        """Get current configuration"""
        return {
            'conviction_decay': self.conviction_decay,
            'conviction_weight': self.conviction_weight,
            'bt_weight': self.bt_weight,
            'softmax_temperature': self.softmax_temperature,
            'convergence_threshold': self.convergence_threshold,
            'decision_making_score_threshold': self.decision_making_score_threshold,
            'decision_making_lead_margin': self.decision_making_lead_margin,
            'survey_score_threshold': self.survey_score_threshold,
            'survey_lead_margin': self.survey_lead_margin
        }
    

    # =========================================================================
    # INTERNAL METHODS
    # =========================================================================
    
    def _initialize_discussion(self, discussion_id: int, mode: str, current_time: float):
        """Initialize state for a new discussion"""
        self.discussion_states[discussion_id] = ConvictionState(
            conviction=np.zeros(6),
            submission_history=deque(maxlen=12),
            score_history=deque(maxlen=12),
            last_update=current_time,
            round_start=current_time,
            batch_count=0,
            mode=mode
        )
    
    
    def _apply_decay(self, state: ConvictionState, current_time: float):
        """Apply exponential time decay to conviction"""
        time_elapsed = current_time - state.last_update
        num_periods = time_elapsed / 5.0

        if len(state.score_history) >= 3:
            recent_scores = np.array(list(state.score_history)[-3:])
            variance = np.mean(np.var(recent_scores, axis=0))
            # High variance (disagreement) → faster decay (be more responsive)
            # Low variance (consensus) → slower decay (be more stable)
            variance_factor = np.clip(variance / 50.0, 0.5, 2.0)
            adaptive_decay = self.conviction_decay ** variance_factor
        else:
            adaptive_decay = self.conviction_decay

        decay_factor = adaptive_decay ** num_periods
        state.conviction *= decay_factor
    
    
    def _accumulate_conviction(self, state: ConvictionState, submissions: np.ndarray):
        """Accumulate conviction from new submissions with diminishing returns"""
        total_allocations = np.sum(submissions, axis=0)
        conviction_delta = np.sqrt(total_allocations + 1e-10)
        state.conviction += conviction_delta
    
    
    def _calculate_bt_strengths(self, state: ConvictionState) -> np.ndarray:
        """Calculate Bradley-Terry strengths from submission history"""
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
                            wins[i][j] += submission[i] / total
        
        strengths = self._solve_bradley_terry(wins)
        return strengths
    
    
    def _solve_bradley_terry(self, wins: np.ndarray, max_iter: int = 50, tol: float = 1e-4) -> np.ndarray:
        """Solve Bradley-Terry using MM algorithm"""
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
    
    
    def _combine_and_normalize(self, conviction: np.ndarray, bt_strengths: np.ndarray, state: ConvictionState) -> np.ndarray:
        """Combine conviction + BT with softmax normalization to 0-100 range"""
        conviction_safe = np.maximum(conviction, 1e-10)
        bt_safe = np.maximum(bt_strengths, 1e-10)
        
        combined = (conviction_safe ** self.conviction_weight) * (bt_safe ** self.bt_weight)
        
        log_scores = np.log(combined + 1e-10)

        # Determine temperature based on recent score history
        base_temp = self.softmax_temperature
        if len(state.score_history) >= 3:
            recent_scores = np.array(list(state.score_history)[-3:])
            avg_std = np.mean(np.std(recent_scores, axis=1))
            avg_mean = np.mean(recent_scores)
            consensus_strength = 1.0 - np.clip(avg_std / (avg_mean + 1e-10), 0, 1)
            adaptive_temp = base_temp * (0.3 + 0.7 * (1 - consensus_strength))
        else:
            adaptive_temp = base_temp

        scaled_scores = log_scores / adaptive_temp
        scaled_scores = scaled_scores - np.max(scaled_scores)
        exp_scores = np.exp(scaled_scores)
        softmax_probs = exp_scores / np.sum(exp_scores)
        
        final_scores = softmax_probs * 100.0
        
        return final_scores
    
    
    def _check_convergence(self, state: ConvictionState, scores: np.ndarray) -> Dict:
        """Check if convergence criteria met (mode-aware)"""
        if state.batch_count < 6:  # at least 30 seconds elapsed (6 × 5s)
            return {
                'converged': False,
                'converged_index': None,
            }
        
        # Get mode-specific thresholds
        if state.mode == "decision-making":
            winner_score_threshold = self.decision_making_score_threshold
            winner_lead_margin = self.decision_making_lead_margin
        else:  # survey
            winner_score_threshold = self.survey_score_threshold
            winner_lead_margin = self.survey_lead_margin
        
        # Calculate stability
        recent_scores = np.array(list(state.score_history)[-6:])
        score_variances = np.var(recent_scores, axis=0)
        avg_score_variance = np.mean(score_variances)
        mean_score = np.mean(scores)
        
        if mean_score > 0:
            score_stability = 1.0 / (1.0 + avg_score_variance / mean_score * 5.0)
        else:
            score_stability = 0.0
        
        sorted_indices = np.argsort(scores)[::-1]
        top_score = scores[sorted_indices[0]]
        second_score = scores[sorted_indices[1]]
        lead_margin = top_score - second_score

        top_visual_score = min(top_score / (60 if state.mode == "decision-making" else 45) * 100.0, 100.0)
        print(f"Top score: {top_visual_score:.2f}, Lead margin: {lead_margin:.2f}, Score stability: {score_stability:.2f}")
        
        sector_reached_center = top_visual_score >= winner_score_threshold
        has_significant_lead = lead_margin >= winner_lead_margin
        is_stable = score_stability >= self.convergence_threshold
        
        print(f"sector_reached_center: {sector_reached_center}, has_significant_lead: {has_significant_lead}, is_stable: {is_stable}")
        converged = sector_reached_center and has_significant_lead and is_stable        
        
        return {
            'converged': converged,
            'converged_index': int(sorted_indices[0]) if converged else None,
        }
    

    def _build_response(self, discussion_id: int, state: ConvictionState, scores: np.ndarray, 
                       convergence_result: Dict) -> Dict:
        """Build final response with visual scaling for frontend"""
        
        # Determine max realistic score based on mode
        if state.mode == "decision-making":
            max_realistic_score = 60.0
        else:  # survey
            max_realistic_score = 45.0

        # This ensures frontend can use full gradient depth
        visual_scores = [min(score / max_realistic_score * 100.0, 100.0) for score in scores]
        
        return {
            'discussionId': discussion_id,
            'converged': bool(convergence_result['converged']),
            'convergedIndex': convergence_result['converged_index'],
            'fitnessScores': visual_scores,
        }