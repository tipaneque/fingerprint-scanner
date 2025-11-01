package com.github.bluestring_digital.fingerprint.service;

import com.github.bluestring_digital.fingerprint.service.FingerprintScannerService.FingerSplitResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service to detect which hand (left or right) is being captured.
 */
@Slf4j
@Service
public class HandDetectionService {

    /**
     * Hand detection result
     */
    public static class HandDetectionResult {
        private HandType handType;
        private double confidence;
        private List<FingerPosition> fingerPositions;
        private String reason;

        public enum HandType {
            LEFT("Left"),
            RIGHT("Right"),
            UNKNOWN("Unknown");

            private final String description;
            HandType(String description) { this.description = description; }
            public String getDescription() { return description; }
        }

        public HandDetectionResult(HandType handType, double confidence, String reason) {
            this.handType = handType;
            this.confidence = confidence;
            this.reason = reason;
            this.fingerPositions = new ArrayList<>();
        }

        // Getters e Setters
        public HandType getHandType() { return handType; }
        public void setHandType(HandType handType) { this.handType = handType; }
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        public List<FingerPosition> getFingerPositions() { return fingerPositions; }
        public void setFingerPositions(List<FingerPosition> fingerPositions) {
            this.fingerPositions = fingerPositions;
        }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    /**
     * Position of a specific finger
     */
    public static class FingerPosition {
        private FingerName name;
        private int x;
        private int y;
        private int angle;
        private int quality;

        public enum FingerName {
            THUMB("Thumb"),
            INDEX("Index"),
            MIDDLE("Middle"),
            RING("Ring"),
            LITTLE("Little");

            private final String description;
            FingerName(String description) { this.description = description; }
            public String getDescription() { return description; }
        }

        public FingerPosition(FingerName name, int x, int y, int angle, int quality) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.angle = angle;
            this.quality = quality;
        }

        // Getters
        public FingerName getName() { return name; }
        public int getX() { return x; }
        public int getY() { return y; }
        public int getAngle() { return angle; }
        public int getQuality() { return quality; }
    }

    /**
     * It detects which hand is being captured based on the fingers detected.
     */
    public HandDetectionResult detectHand(List<FingerSplitResult> fingers) {
        if (fingers == null || fingers.isEmpty()) {
            return new HandDetectionResult(
                    HandDetectionResult.HandType.UNKNOWN,
                    0.0,
                    "No finger detected"
            );
        }

        int fingerCount = fingers.size();
        log.debug("Detecting hand with {} fingers", fingerCount);

        HandDetectionResult result;

        // Estratégias de detecção baseadas no número de dedos
        switch (fingerCount) {
            case 1:
                result = detectSingleFinger(fingers.get(0));
                break;
            case 2:
                result = detectTwoThumbs(fingers);
                break;
            case 4:
                result = detectFourFingers(fingers);
                break;
            case 5:
                result = detectFiveFingers(fingers);
                break;
            default:
                result = new HandDetectionResult(
                        HandDetectionResult.HandType.UNKNOWN,
                        0.5,
                        "Non-standard number of fingers: " + fingerCount
                );
        }

        // Atribui nomes aos dedos
        assignFingerNames(fingers, result);

        log.info("Hand detected: {} (confidence: {:.2f}%) - {}",
                result.getHandType().getDescription(),
                result.getConfidence() * 100,
                result.getReason());

        return result;
    }

    /**
     * Detects a hand with only one finger (difficult to determine).
     */
    private HandDetectionResult detectSingleFinger(FingerSplitResult finger) {
        // With a single finger, it's difficult to determine the hand.
        // We use position and angle as clues.

        int angle = finger.getAngle();
        int x = finger.getX();

        // Normalizes angle to 0-360
        angle = ((angle % 360) + 360) % 360;

        // The thumb usually has a more pronounced angle.
        if (angle > 45 && angle < 135) {
            // Leaning to the right - probably left thumb
            return new HandDetectionResult(
                    HandDetectionResult.HandType.LEFT,
                    0.6,
                    "Single finger tilted to the right (angle: " + angle + " degrees)"
            );
        } else if (angle > 225 && angle < 315) {
            // Leaning to the left - probably right thumb
            return new HandDetectionResult(
                    HandDetectionResult.HandType.RIGHT,
                    0.6,
                    "Single finger tilted to the left (angle: " + angle + "°)"
            );
        }

        return new HandDetectionResult(
                HandDetectionResult.HandType.UNKNOWN,
                0.4,
                "Single finger without characteristic inclination"
        );
    }

    /**
     * Detects two thumbs
     */
    private HandDetectionResult detectTwoThumbs(List<FingerSplitResult> fingers) {
        // Ordena por posição X
        List<FingerSplitResult> sorted = fingers.stream()
                .sorted(Comparator.comparingInt(FingerSplitResult::getX))
                .collect(Collectors.toList());

        FingerSplitResult left = sorted.get(0);
        FingerSplitResult right = sorted.get(1);

        int leftAngle = left.getAngle();
        int rightAngle = right.getAngle();

        // Thumbs should be pointing towards each other
        // Left thumb: angled to the right (positive angle)
        // Right thumb: angled to the left (negative angle or > 180 degrees)

        boolean leftThumbCharacteristic = leftAngle > 10 && leftAngle < 80;
        boolean rightThumbCharacteristic = (rightAngle > 280 && rightAngle < 350) ||
                (rightAngle > -80 && rightAngle < -10);

        if (leftThumbCharacteristic && rightThumbCharacteristic) {
            return new HandDetectionResult(
                    HandDetectionResult.HandType.LEFT, // Ambas as mãos
                    0.85,
                    "Two thumbs detected (angles: " + leftAngle + "°, " + rightAngle + "°)"
            );
        }

        return new HandDetectionResult(
                HandDetectionResult.HandType.UNKNOWN,
                0.6,
                "Two fingers with no clear thumb pattern"
        );
    }

    /**
     * Detects 4 fingers (without thumb)
     */
    private HandDetectionResult detectFourFingers(List<FingerSplitResult> fingers) {
        // Sort by position X (left to right)
        List<FingerSplitResult> sorted = fingers.stream()
                .sorted(Comparator.comparingInt(FingerSplitResult::getX))
                .collect(Collectors.toList());

        // Calculates finger spacing
        List<Integer> gaps = new ArrayList<>();
        for (int i = 0; i < sorted.size() - 1; i++) {
            gaps.add(sorted.get(i + 1).getX() - sorted.get(i).getX());
        }

        // The larger gap indicates where the thumb should be.
        int maxGapIndex = 0;
        int maxGap = gaps.get(0);
        for (int i = 1; i < gaps.size(); i++) {
            if (gaps.get(i) > maxGap) {
                maxGap = gaps.get(i);
                maxGapIndex = i;
            }
        }

        // Average of the other gaps
        int finalMaxGap = maxGap;
        double avgGap = gaps.stream()
                .filter(gap -> gap != finalMaxGap)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);

        // If the larger gap is significantly larger (>1.5x the average)
        if (maxGap > avgGap * 1.5) {
            // Left gap = right hand (thumb is on the left, outside the capture)
            // Right gap = left hand (thumb is on the right, outside the capture)

            if (maxGapIndex == 0) {
                // Gap between first and second finger = RIGHT hand
                return new HandDetectionResult(
                        HandDetectionResult.HandType.RIGHT,
                        0.85,
                        "4 fingers with a larger gap on the left (right thumb outside)"
                );
            } else if (maxGapIndex == gaps.size() - 1) {
                // Gap between the last and second-to-last finger = LEFT hand
                return new HandDetectionResult(
                        HandDetectionResult.HandType.LEFT,
                        0.85,
                        "4 fingers with a larger gap on the right (left thumb outside)"
                );
            }
        }

        // Angle analysis as fallback
        double avgAngle = sorted.stream()
                .mapToInt(FingerSplitResult::getAngle)
                .average()
                .orElse(0);

        // Left-hand fingers tend to tilt slightly to the right
        // Right-hand fingers tend to tilt slightly to the left
        if (avgAngle > 5 && avgAngle < 15) {
            return new HandDetectionResult(
                    HandDetectionResult.HandType.LEFT,
                    0.7,
                    "4 fingers angled to the right (medium angle): " +
                            String.format("%.1f", avgAngle) + " degrees)"
            );
        } else if (avgAngle < -5 && avgAngle > -15) {
            return new HandDetectionResult(
                    HandDetectionResult.HandType.RIGHT,
                    0.7,
                    "4 fingers angled to the left (medium angle): " +
                            String.format("%.1f", avgAngle) + "°)"
            );
        }

        return new HandDetectionResult(
                HandDetectionResult.HandType.UNKNOWN,
                0.6,
                "4 fingers with no clear orientation pattern."
        );
    }

    /**
     * Detects five fingers (full hand)
     */
    private HandDetectionResult detectFiveFingers(List<FingerSplitResult> fingers) {
        // Sort by position X
        List<FingerSplitResult> sorted = fingers.stream()
                .sorted(Comparator.comparingInt(FingerSplitResult::getX))
                .collect(Collectors.toList());

        //The thumb is usually further away from the other fingers and has a different angle

        // Analyzes the gaps between consecutive fingers.
        List<Integer> gaps = new ArrayList<>();
        for (int i = 0; i < sorted.size() - 1; i++) {
            gaps.add(sorted.get(i + 1).getX() - sorted.get(i).getX());
        }

        int maxGapIndex = 0;
        int maxGap = gaps.get(0);
        for (int i = 1; i < gaps.size(); i++) {
            if (gaps.get(i) > maxGap) {
                maxGap = gaps.get(i);
                maxGapIndex = i;
            }
        }

        // The larger gap between the fingers indicates the position of the thumb.
        if (maxGapIndex == 0) {
            // Thumb is more to the left = LEFT HAND
            return new HandDetectionResult(
                    HandDetectionResult.HandType.LEFT,
                    0.9,
                    "5 fingers with thumb on the left"
            );
        } else if (maxGapIndex == gaps.size() - 1) {
            // Thumb is further to the right = RIGHT HAND
            return new HandDetectionResult(
                    HandDetectionResult.HandType.RIGHT,
                    0.9,
                    "5 fingers with thumb on the right"
            );
        }

        return new HandDetectionResult(
                HandDetectionResult.HandType.UNKNOWN,
                0.6,
                "5 fingers but thumb not clearly identified"
        );
    }

    /**
     * Assigns names to fingers based on the detected hand and positions.
     */
    private void assignFingerNames(List<FingerSplitResult> fingers,
                                   HandDetectionResult result) {
        if (fingers.isEmpty()) return;

        // Sort by position X
        List<FingerSplitResult> sorted = fingers.stream()
                .sorted(Comparator.comparingInt(FingerSplitResult::getX))
                .collect(Collectors.toList());

        List<FingerPosition> positions = new ArrayList<>();

        switch (fingers.size()) {
            case 2: // 2 thumbs
                positions.add(new FingerPosition(
                        FingerPosition.FingerName.THUMB,
                        sorted.get(0).getX(),
                        sorted.get(0).getY(),
                        sorted.get(0).getAngle(),
                        sorted.get(0).getQuality()
                ));
                positions.add(new FingerPosition(
                        FingerPosition.FingerName.THUMB,
                        sorted.get(1).getX(),
                        sorted.get(1).getY(),
                        sorted.get(1).getAngle(),
                        sorted.get(1).getQuality()
                ));
                break;

            case 4: // 4 fingers (without thumb)
                FingerPosition.FingerName[] fourFingerNames;

                if (result.getHandType() == HandDetectionResult.HandType.LEFT) {
                    // Left hand without thumb: Index, Middle, Ring, Little finger
                    fourFingerNames = new FingerPosition.FingerName[]{
                            FingerPosition.FingerName.INDEX,
                            FingerPosition.FingerName.MIDDLE,
                            FingerPosition.FingerName.RING,
                            FingerPosition.FingerName.LITTLE
                    };
                } else {
                    // Right hand without thumb: Index, Middle, Ring, Little finger
                    fourFingerNames = new FingerPosition.FingerName[]{
                            FingerPosition.FingerName.INDEX,
                            FingerPosition.FingerName.MIDDLE,
                            FingerPosition.FingerName.RING,
                            FingerPosition.FingerName.LITTLE
                    };
                }

                for (int i = 0; i < sorted.size(); i++) {
                    FingerSplitResult finger = sorted.get(i);
                    positions.add(new FingerPosition(
                            fourFingerNames[i],
                            finger.getX(),
                            finger.getY(),
                            finger.getAngle(),
                            finger.getQuality()
                    ));
                }
                break;

            case 5: // 5 fingers
                FingerPosition.FingerName[] fiveFingerNames;

                if (result.getHandType() == HandDetectionResult.HandType.LEFT) {
                    // Left hand: Thumb, Index finger, Middle finger, Ring finger, Little finger
                    fiveFingerNames = new FingerPosition.FingerName[]{
                            FingerPosition.FingerName.THUMB,
                            FingerPosition.FingerName.INDEX,
                            FingerPosition.FingerName.MIDDLE,
                            FingerPosition.FingerName.RING,
                            FingerPosition.FingerName.LITTLE
                    };
                } else {
                    // Right hand: Thumb, Index finger, Middle finger, Ring finger, Little finger
                    fiveFingerNames = new FingerPosition.FingerName[]{
                            FingerPosition.FingerName.THUMB,
                            FingerPosition.FingerName.INDEX,
                            FingerPosition.FingerName.MIDDLE,
                            FingerPosition.FingerName.RING,
                            FingerPosition.FingerName.LITTLE
                    };
                }

                for (int i = 0; i < sorted.size(); i++) {
                    FingerSplitResult finger = sorted.get(i);
                    positions.add(new FingerPosition(
                            fiveFingerNames[i],
                            finger.getX(),
                            finger.getY(),
                            finger.getAngle(),
                            finger.getQuality()
                    ));
                }
                break;
        }

        result.setFingerPositions(positions);
    }

    /**
     * Converts the result to a user-friendly JSON format.
     */
    public Map<String, Object> toMap(HandDetectionResult result) {
        Map<String, Object> map = new HashMap<>();
        map.put("hand", result.getHandType().name().toLowerCase());
        map.put("handDescription", result.getHandType().getDescription());
        map.put("confidence", Math.round(result.getConfidence() * 100));
        map.put("reason", result.getReason());

        List<Map<String, Object>> fingersList = new ArrayList<>();
        for (FingerPosition fp : result.getFingerPositions()) {
            Map<String, Object> fingerMap = new HashMap<>();
            fingerMap.put("name", fp.getName().name().toLowerCase());
            fingerMap.put("nameDescription", fp.getName().getDescription());
            fingerMap.put("x", fp.getX());
            fingerMap.put("y", fp.getY());
            fingerMap.put("angle", fp.getAngle());
            fingerMap.put("quality", fp.getQuality());
            fingersList.add(fingerMap);
        }
        map.put("fingers", fingersList);

        return map;
    }
}
