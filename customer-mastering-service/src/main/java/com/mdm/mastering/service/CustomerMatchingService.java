package com.mdm.mastering.service;

import com.mdm.mastering.entity.CustomerGoldenEntity;
import java.util.Optional;

/**
 * Service interface for survivable customer matching.
 * 
 * <p>Implements multiple fuzzy matching rules to detect duplicates:
 * <ul>
 *   <li>Email similarity (typos, Gmail dots)</li>
 *   <li>Name similarity (Levenshtein distance)</li>
 *   <li>Phonetic matching (Soundex)</li>
 *   <li>Nickname mapping (50+ variants)</li>
 *   <li>Phone number normalization</li>
 * </ul>
 * 
 * <p>Matching thresholds:
 * <ul>
 *   <li>≥ 80% → DUPLICATE (merge records)</li>
 *   <li>50-79% → POSSIBLE_DUPLICATE (flag for review)</li>
 *   <li>&lt; 50% → NO_MATCH (create new record)</li>
 * </ul>
 */
public interface CustomerMatchingService {
    
    /**
     * Find matching customer using survivable rules.
     * 
     * @param email Customer email
     * @param firstName Customer first name
     * @param lastName Customer last name
     * @param phone Customer phone number
     * @return Match result with score and matched customer (if any)
     */
    MatchResult findMatch(String email, String firstName, String lastName, String phone);
    
    /**
     * Result of a customer matching attempt.
     */
    class MatchResult {
        private final MatchType type;
        private final CustomerGoldenEntity matchedCustomer;
        private final double matchScore;
        private final java.util.List<String> matchedRules;
        
        private MatchResult(MatchType type, CustomerGoldenEntity matchedCustomer, 
                           double matchScore, java.util.List<String> matchedRules) {
            this.type = type;
            this.matchedCustomer = matchedCustomer;
            this.matchScore = matchScore;
            this.matchedRules = matchedRules;
        }
        
        public static MatchResult duplicate(CustomerGoldenEntity customer, double score, 
                                           java.util.List<String> rules) {
            return new MatchResult(MatchType.DUPLICATE, customer, score, rules);
        }
        
        public static MatchResult possibleDuplicate(CustomerGoldenEntity customer, double score, 
                                                   java.util.List<String> rules) {
            return new MatchResult(MatchType.POSSIBLE_DUPLICATE, customer, score, rules);
        }
        
        public static MatchResult noMatch() {
            return new MatchResult(MatchType.NO_MATCH, null, 0.0, java.util.List.of());
        }
        
        public boolean isDuplicate() {
            return type == MatchType.DUPLICATE;
        }
        
        public boolean isPossibleDuplicate() {
            return type == MatchType.POSSIBLE_DUPLICATE;
        }
        
        public boolean hasMatch() {
            return matchedCustomer != null;
        }
        
        // Getters
        public MatchType getType() { return type; }
        public CustomerGoldenEntity getMatchedCustomer() { return matchedCustomer; }
        public double getMatchScore() { return matchScore; }
        public java.util.List<String> getMatchedRules() { return matchedRules; }
    }
    
    /**
     * Type of match result.
     */
    enum MatchType {
        DUPLICATE,           // Score >= 80%
        POSSIBLE_DUPLICATE,  // Score 50-79%
        NO_MATCH            // Score < 50%
    }
}
