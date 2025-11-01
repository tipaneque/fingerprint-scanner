package com.github.bluestring_digital.fingerprint.controller;

import com.github.bluestring_digital.fingerprint.service.FingerprintScannerService;
import com.github.bluestring_digital.fingerprint.service.FingerprintScannerService.FingerSplitResult;

import com.github.bluestring_digital.fingerprint.service.HandDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/fingerprint")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FingerprintController {

    private final FingerprintScannerService scannerService;
    private final HandDetectionService handDetectionService;
    private final SimpMessagingTemplate messagingTemplate;

    private CompletableFuture<Void> captureTask;
    private volatile boolean isCapturing = false;
    private boolean isInitialized = false;

    /**
     * Opens the device
     */
    @PostMapping("/device/open")
    public ResponseEntity<Map<String, Object>> openDevice() {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean success = scannerService.openDevice();
            isInitialized = success;
            response.put("success", success);
            response.put("message", success ?
                    "Device connected successfully" :
                    "Failed to connect device.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error opening device", e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Closes the device
     */
    @PostMapping("/device/close")
    public ResponseEntity<Map<String, Object>> closeDevice() {
        Map<String, Object> response = new HashMap<>();
        try {
            stopCapture();
            boolean success = scannerService.closeDevice();
            isInitialized = false;
            response.put("success", success);
            response.put("message", success ?
                    "Device successfully disconnected" :
                    "Failed to disconnect device");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error closing device", e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Checks the device status
     */
    @GetMapping("/device/status")
    public ResponseEntity<Map<String, Object>> getDeviceStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("isOpen", scannerService.isDeviceOpen());
        status.put("isCapturing", isCapturing);
        return ResponseEntity.ok(status);
    }

    /**
     * Configures the type of finger (dry/normal/wet)
     */
    @PostMapping("/device/finger-type")
    public ResponseEntity<Map<String, Object>> setFingerType(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String type = request.get("type");
            boolean success = scannerService.setFingerType(type);
            response.put("success", success);
            response.put("message", success ? "Finger type configured": "Failed to configure");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error setting finger type", e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Captures single finger
     */
    @PostMapping("/capture/single")
    public ResponseEntity<Map<String, Object>> captureSingleImage() {
        Map<String, Object> response = new HashMap<>();
        try {
            byte [] rawData = scannerService.captureRawImage(300, 400);
            int quality = scannerService.safeFingerQuality(rawData);

            byte [] bmpData = scannerService.rawToBmp(rawData, 300, 400);
            String base64Image = scannerService.bmpToBase64(bmpData);

            response.put("success", true);
            response.put("image", base64Image);
            response.put("quality", quality);
            response.put("width", 300);
            response.put("height", 400);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error capturing image", e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Starts the continuous capture with preview
     */
    @PostMapping("/capture/start")
    public ResponseEntity<Map<String, Object>> startContinuousCapture() {
        Map<String, Object> response = new HashMap<>();

        if (isCapturing) {
            response.put("success", false);
            response.put("message", "Capture is already underway");
            return ResponseEntity.ok(response);
        }

        isCapturing = true;
        captureTask = CompletableFuture.runAsync(() -> {
            try {
                int width = 1600;
                int height = 1500;
                log.info("Starting continuous capture {}x{}", width, height);

                while (isCapturing) {
                    byte[] rawData = scannerService.captureRawImage(width, height);
                    int quality = scannerService.safeFingerQuality(rawData);

                    byte[] bmpData = scannerService.rawToBmp(rawData, width, height);
                    String base64Image = scannerService.bmpToBase64(bmpData);

                    Map<String, Object> frameData = new HashMap<>();
                    frameData.put("image", base64Image);
                    frameData.put("quality", quality);
                    frameData.put("width", width);
                    frameData.put("height", height);
                    frameData.put("timestamp", System.currentTimeMillis());

                    // Sends frame even with low quality (for debugging)
                    messagingTemplate.convertAndSend("/topic/fingerprint", frameData);

                    log.debug("Sent frame (quality={})", quality);

                    Thread.sleep(200); // 5 FPS
                }
            } catch (Exception e) {
                log.error("Continuous capture error", e);
                isCapturing = false;
            }
        });

        response.put("success", true);
        response.put("message", "Continuous capture started");
        return ResponseEntity.ok(response);
    }

    /**
     * Stops the continuous capture
     */
    @PostMapping("/capture/stop")
    public ResponseEntity<Map<String, Object>> stopCapture() {
        Map<String, Object> response = new HashMap<>();
        isCapturing = false;
        if (captureTask != null) {
            captureTask.cancel(true);
        }
        response.put("success", true);
        response.put("message", "Capture interrupted");
        return ResponseEntity.ok(response);
    }

    /**
     * Captures and splits multiple fingers
     */
    @PostMapping("/capture/multiple")
    public ResponseEntity<Map<String, Object>> captureMultipleFingers(
            @RequestParam(defaultValue = "4") int expectedFingers) {

        Map<String, Object> response = new HashMap<>();
        try {
            byte[] rawData = scannerService.captureRawImage(1600, 1500);
            int quality = scannerService.safeFingerQuality(rawData);

            if (quality < 30) {
                response.put("success", false);
                response.put("message", "Insufficient image quality: " + quality);
                return ResponseEntity.ok(response);
            }

            List<FingerSplitResult> fingers = scannerService.splitFingers(rawData, 1600, 1500);

            List<Map<String, Object>> fingersList = new ArrayList<>();
            for (FingerSplitResult finger : fingers) {
                byte[] bmpData = scannerService.rawToBmp(
                        finger.getImageData(),
                        finger.getWidth(),
                        finger.getHeight()
                );
                String base64Image = scannerService.bmpToBase64(bmpData);

                Map<String, Object> fingerData = new HashMap<>();
                fingerData.put("image", base64Image);
                fingerData.put("quality", finger.getQuality());
                fingerData.put("angle", finger.getAngle());
                fingerData.put("x", finger.getX());
                fingerData.put("y", finger.getY());

                fingersList.add(fingerData);
            }

            // Success beep
            scannerService.beep(1);

            response.put("success", true);
            response.put("fingers", fingersList);
            response.put("count", fingers.size());
            response.put("quality", quality);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error capturing multiple fingers", e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Creates biometric template
     */
    @PostMapping("/template/create")
    public ResponseEntity<Map<String, Object>> createTemplate() {
        Map<String, Object> response = new HashMap<>();
        try {
            byte[] rawData = scannerService.captureRawImage(1600, 1500);
            List<FingerSplitResult> fingers = scannerService.splitFingers(rawData, 1600, 1500);

            if (fingers.isEmpty()) {
                response.put("success", false);
                response.put("message", "No fingers detected");
                return ResponseEntity.ok(response);
            }

            List<String> templates = new ArrayList<>();
            for (FingerSplitResult finger : fingers) {
                if (finger.getQuality() >= 20) {
                    byte[] template = scannerService.createISOTemplate(finger.getImageData());
                    templates.add(Base64.getEncoder().encodeToString(template));
                }
            }

            response.put("success", true);
            response.put("templates", templates);
            response.put("count", templates.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error creating template", e);
            response.put("success", false);
            response.put("message", "Erro: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Compares two templates
     */
    @PostMapping("/template/compare")
    public ResponseEntity<Map<String, Object>> compareTemplates(
            @RequestBody Map<String, String> request) {

        Map<String, Object> response = new HashMap<>();
        try {
            String template1Base64 = request.get("template1");
            String template2Base64 = request.get("template2");

            byte[] template1 = Base64.getDecoder().decode(template1Base64);
            byte[] template2 = Base64.getDecoder().decode(template2Base64);

            int score = scannerService.compareTemplates(template1, template2);
            boolean match = score >= 45; // Threshold de 45

            response.put("success", true);
            response.put("score", score);
            response.put("match", match);
            response.put("message", match ?
                    "Fingerprints match (score: " + score + ")" :
                    "Fingerprints do not match (score: " + score + ")");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error comparing templates ", e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Detects false fingerprint
     */
    @PostMapping("/liveness/check")
    public ResponseEntity<Map<String, Object>> checkLiveness() {
        Map<String, Object> response = new HashMap<>();
        try {
            byte [] rawData = scannerService.captureRawImage(300, 400);
            int fakeScore = scannerService.detectFake(rawData, 300, 400);

            boolean isLive = fakeScore > 120; // Threshold of 120

            response.put("success", true);
            response.put("score", fakeScore);
            response.put("isLive", isLive);
            response.put("message", isLive ?
                    "Real finger detected" :
                    "Possible fake finger detected.");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error checking authenticity", e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }


    /**
     * Captures two thumbs
     */
    @PostMapping("/capture/thumbs")
    public ResponseEntity<Map<String, Object>> captureTwoThumbs() {
        Map<String, Object> response = new HashMap<>();
        try {
            int width = 1600;
            int height = 1500;
            int expectedFingers = 2;

            log.info("Capturing two thumbs {}x{}", width, height);

            byte[] rawData = scannerService.captureRawImage(width, height);
            int quality = scannerService.safeFingerQuality(rawData);

            log.info("Quality of image: {}", quality);

            List<FingerSplitResult> fingers = scannerService.splitFingers(rawData, width, height);

            if (fingers.isEmpty()) {
                response.put("success", false);
                response.put("message", "No fingers detected. Place both thumbs on the scanner");
                return ResponseEntity.ok(response);
            }

            if (fingers.size() != expectedFingers) {
                response.put("success", false);
                response.put("message", String.format(
                        "Expected %d thumbs, but detected %d finger(s). Position only the 2 thumbs.",
                        expectedFingers, fingers.size()
                ));
                response.put("detected", fingers.size());
                response.put("quality", quality);
                return ResponseEntity.ok(response);
            }

            // Detects which hand
            HandDetectionService.HandDetectionResult handDetection =
                    handDetectionService.detectHand(fingers);

            log.info("Thumbs detect - Hands: {} (confidence level: {}%)",
                    handDetection.getHandType().getDescription(),
                    Math.round(handDetection.getConfidence() * 100));

            List<Map<String, Object>> thumbsList = new ArrayList<>();

            // Sorts by X position (left to right)
            List<FingerSplitResult> sortedFingers = fingers.stream()
                    .sorted(Comparator.comparingInt(FingerSplitResult::getX))
                    .toList();

            for (int i = 0; i < sortedFingers.size(); i++) {
                FingerSplitResult finger = sortedFingers.get(i);

                byte[] bmpData = scannerService.rawToBmp(
                        finger.getImageData(),
                        finger.getWidth(),
                        finger.getHeight()
                );
                String base64Image = scannerService.bmpToBase64(bmpData);

                Map<String, Object> thumbData = new HashMap<>();
                thumbData.put("image", base64Image);
                thumbData.put("quality", finger.getQuality());
                thumbData.put("angle", finger.getAngle());
                thumbData.put("x", finger.getX());
                thumbData.put("y", finger.getY());
                thumbData.put("position", i == 0 ? "left" : "right"); // Relative position
                thumbData.put("index", i);

                thumbsList.add(thumbData);

                log.debug("Thumb {}: quality={}, angle={}, pos=({},{})",
                        i, finger.getQuality(), finger.getAngle(), finger.getX(), finger.getY());
            }

            scannerService.beep(2); // 2 beeps for 2 thumbs

            response.put("success", true);
            response.put("thumbs", thumbsList);
            response.put("count", thumbsList.size());
            response.put("quality", quality);
            response.put("handDetection", handDetectionService.toMap(handDetection));

            log.info("Thumb capture completed: {} thumbs detected", thumbsList.size());

            return ResponseEntity.ok(response);

        } catch (IllegalStateException e) {
            log.error("Invalid state", e);
            response.put("success", false);
            response.put("message", "Device is not connected");
            return ResponseEntity.status(409).body(response);

        } catch (RuntimeException e) {
            log.error("Runtime error when capturing thumbs", e);
            response.put("success", false);
            response.put("message", "Capture error: " + e.getMessage());
            return ResponseEntity.status(500).body(response);

        } catch (Exception e) {
            log.error("Unexpected error capturing thumbs", e);
            response.put("success", false);
            response.put("message", "Internal error: " + e.getMessage());
            response.put("type", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(response);
        }
    }

    // =================== HAND DETECTION ===================

    /**
     * Specific endpoint to detect which hand is on the scanner
     */
    @PostMapping("/detect/hand")
    public ResponseEntity<Map<String, Object>> detectHand(
            @RequestParam(defaultValue = "1600") int width,
            @RequestParam(defaultValue = "1500") int height) {

        Map<String, Object> response = new HashMap<>();
        try {
            log.info("Detecting hand {}x{}", width, height);

            byte[] rawData = scannerService.captureRawImage(width, height);
            int quality = scannerService.safeFingerQuality(rawData);

            if (quality < 20) {
                response.put("success", false);
                response.put("message", "Insufficient quality: " + quality);
                return ResponseEntity.ok(response);
            }

            List<FingerSplitResult> fingers = scannerService.splitFingers(rawData, width, height);

            if (fingers.isEmpty()) {
                response.put("success", false);
                response.put("message", "No finger detected");
                return ResponseEntity.ok(response);
            }

            HandDetectionService.HandDetectionResult handDetection =
                    handDetectionService.detectHand(fingers);

            response.put("success", true);
            response.put("fingerCount", fingers.size());
            response.put("quality", quality);
            response.putAll(handDetectionService.toMap(handDetection));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error detecting hand", e);
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }


    /**
     * Emits beep
     */
    @PostMapping("/device/beep")
    public ResponseEntity<Map<String, Object>> beep(
            @RequestParam(defaultValue = "1") int times) {

        Map<String, Object> response = new HashMap<>();
        try {
            scannerService.beep(times);
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}