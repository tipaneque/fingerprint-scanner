package com.github.bluestring_digital.fingerprint.service;

import com.github.bluestring_digital.fingerprint.lib.FingerprintDeviceInterface.*;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class FingerprintScannerService {

    private final LiveScan liveScan = LiveScan.INSTANCE;
    private final Mosaic mosaic = Mosaic.INSTANCE;
    private final FpSplit fpSplit = FpSplit.INSTANCE;
    private final FpStdLib fpStdLib = FpStdLib.INSTANCE;
    private final Fione fione = Fione.INSTANCE;

    private int fpDevice = 0;
    private final AtomicBoolean isDeviceOpen = new AtomicBoolean(false);
    private final AtomicBoolean isCapturing = new AtomicBoolean(false);

    // Standard dimensions
    private static final int DEFAULT_WIDTH = 1600;
    private static final int DEFAULT_HEIGHT = 1500;
    private static final int SINGLE_WIDTH = 300;
    private static final int SINGLE_HEIGHT = 400;


    public int safeFingerQuality(byte[] imageData) {
        int width = 800;
        int height = 750;

        try {
            int quality = mosaic.MOSAIC_FingerQuality(imageData, width, height);
            log.info("Quality detected with resolution {}x{} = {}", width, height, quality);
            return quality;
        } catch (Error e) {
            log.warn("Failure to assess quality in {}x{} -> {}", width, height, e.getMessage());
            return -1;
        }
    }
    
    /**
     * Opens the connection to the device.
     */
    public boolean openDevice() {
        try {
            int result = liveScan.LIVESCAN_Init();
            if (result == 1) {
                mosaic.MOSAIC_Init();
                isDeviceOpen.set(true);
                log.info("Device opened successfully");
                return true;
            }
            log.error("Failed to open device: code {}", result);
            return false;
        } catch (Exception e) {
            log.error("Error opening device", e);
            return false;
        }
    }

    /**
     * Close the connection with the device.
     */
    public boolean closeDevice() {
        try {
            stopCapture();
            int result = liveScan.LIVESCAN_Close();
            if (result == 1) {
                mosaic.MOSAIC_Close();
                isDeviceOpen.set(false);
                if (fpDevice != 0) {
                    fpStdLib.ZAZ_FpStdLib_CloseDevice(fpDevice);
                    fpDevice = 0;
                }
                log.info("Device closed successfully");
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Error closing device", e);
            return false;
        }
    }

    /**
     * Configure finger type (dry/normal/wet)
     */
    public boolean setFingerType(String type) {
        if (!isDeviceOpen.get()) return false;

        int level = switch (type.toLowerCase()) {
            case "dry" -> 5;
            case "wet" -> 3;
            default -> 4; // normal
        };

        return liveScan.LIVESCAN_SetFingerDryWet(level) == 1;
    }

    /**
     * Captures a raw image of the device.
     */
    public byte[] captureRawImage(int width, int height) {
        if (!isDeviceOpen.get()) {
            throw new IllegalStateException("Device is not open");
        }

        // Define a janela de captura (área do sensor)
        liveScan.LIVESCAN_SetCaptWindow(0, 0, 0, width, height);

        byte[] rawData = new byte[width * height];
        int result = liveScan.LIVESCAN_GetFPRawData(0, rawData);

        if (result != 1) {
            log.error("Failed to capture image. Code: {}", result);
            throw new RuntimeException("Failed to capture image: code " + result);
        }

        log.debug("Image captured successfully: {}x{}", width, height);
        return rawData;
    }

    /**
     * Check if there is a finger in the image
     */
    public boolean isFinger(byte[] imageData, int width, int height) {
        return mosaic.MOSAIC_IsFinger(imageData, width, height) > 0;
    }

    /**
     * Separate multiple fingers in an image.
     */
    public List<FingerSplitResult> splitFingers(byte[] imageData, int width, int height) {
        fpSplit.FPSPLIT_Init(width, height, 1);

        IntByReference fingerNum = new IntByReference(0);

        // Calculate the size of the structure.
        FpSplit.FPSPLIT_INFO template = new FpSplit.FPSPLIT_INFO();
        int structSize = template.size();

        // Allocates memory for an array of 10 structures.
        Pointer infoArrayPtr = new Memory(structSize * 10);

        // Allocates individual buffers for each finger and configures the pointers
        Pointer[] fingerBuffers = new Pointer[10];
        for (int i = 0; i < 10; i++) {
            fingerBuffers[i] = new Memory(SINGLE_WIDTH * SINGLE_HEIGHT);
            // Writes the buffer pointer to the correct offset (24 bytes after the start of each structure)
            infoArrayPtr.setPointer(i * structSize + 24, fingerBuffers[i]);
        }

        // Perform separation
        int result = fpSplit.FPSPLIT_DoSplit(
                imageData, width, height, 1,
                SINGLE_WIDTH, SINGLE_HEIGHT,
                fingerNum, infoArrayPtr
        );

        List<FingerSplitResult> fingers = new ArrayList<>();
        int count = fingerNum.getValue();

        log.debug("Finger separation: result={}, quantity={}", result, count);

        // Lê as informações de cada dedo detectado
        for (int i = 0; i < count && i < 10; i++) {
            // Creates a structure pointing to the correct offset.
            FpSplit.FPSPLIT_INFO info = new FpSplit.FPSPLIT_INFO(
                    infoArrayPtr.share(i * structSize)
            );
            info.read();

            // Read the image data.
            byte[] fingerData = info.getImageData(SINGLE_WIDTH, SINGLE_HEIGHT);

            FingerSplitResult finger = new FingerSplitResult();
            finger.setImageData(fingerData);
            finger.setWidth(SINGLE_WIDTH);
            finger.setHeight(SINGLE_HEIGHT);
            finger.setX(info.x);
            finger.setY(info.y);
            finger.setTop(info.top);
            finger.setLeft(info.left);
            finger.setAngle(info.angle);
            finger.setQuality(info.quality);

            fingers.add(finger);

            log.debug("Dedo {}: x={}, y={}, angle={}, quality={}", i, info.x, info.y, info.angle, info.quality);
        }

        // Frees up memory
        for (Pointer buffer : fingerBuffers) {
            if (buffer instanceof Memory) {
                // Memory is garbage collected, but we can explicitly free it.
                ((Memory) buffer).clear();
            }
        }

        fpSplit.FPSPLIT_Uninit();
        return fingers;
    }

    /**
     * Converts raw image to BMP with header.
     */
    public byte[] rawToBmp(byte[] rawData, int width, int height) {
        byte[] bmpData = new byte[1078 + width * height];

        // BMP header
        byte[] header = createBmpHeader(width, height);
        System.arraycopy(header, 0, bmpData, 0, 1078);

        // Mirror vertically (BMP is bottom-up)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                bmpData[1078 + y * width + x] =
                        rawData[(height - y - 1) * width + x];
            }
        }

        return bmpData;
    }

    /**
     * Converts BMP to Base64 for sending to the frontend.
     */
    public String bmpToBase64(byte[] bmpData) {
        return "data:image/bmp;base64," + Base64.getEncoder().encodeToString(bmpData);
    }

    /**
     * Creates an ISO template for digital printing.
     */
    public byte[] createISOTemplate(byte[] imageData) {
        if (fpDevice == 0) {
            fpDevice = fpStdLib.ZAZ_FpStdLib_OpenDevice();
            if (fpDevice == 0) {
                throw new RuntimeException("Failed to initialize biometric algorithm.");
            }
        }

        // Mirror the image horizontally (for compatibility).
        horizontalMirror(imageData, SINGLE_WIDTH, SINGLE_HEIGHT);

        byte[] template = new byte[1024];
        int result = fpStdLib.ZAZ_FpStdLib_CreateISOTemplate(fpDevice, imageData, template);

        if (result == 0) {
            throw new RuntimeException("Failed to create ISO template.");
        }

        return template;
    }

    /**
     * Compare two biometric templates.
     */
    public int compareTemplates(byte[] template1, byte[] template2) {
        if (fpDevice == 0) {
            throw new IllegalStateException("Biometric device not initialized");
        }

        return fpStdLib.ZAZ_FpStdLib_CompareTemplates(fpDevice, template1, template2);
    }

    /**
     * Detects fake fingerprints.
     */
    public int detectFake(byte[] imageData, int width, int height) {
        return fione.GetFingerFake(imageData, width, height);
    }

    /**
     * The device beeps.
     */
    public void beep(int times) {
        if (isDeviceOpen.get()) {
            liveScan.LIVESCAN_Beep(times);
        }
    }

    /**
     * For continuous capture
     */
    public void stopCapture() {
        isCapturing.set(false);
    }


    private byte[] createBmpHeader(int width, int height) {
        byte[] header = new byte[1078];

        // Basic BMP headers
        byte[] bmpHeader = {
                0x42, 0x4d, // BM
                0x0, 0x0, 0x0, 0x00, // file size
                0x00, 0x00, 0x00, 0x00, // reserved
                0x36, 0x4, 0x00, 0x00, // data offset
                0x28, 0x00, 0x00, 0x00, // info header size
                0x00, 0x00, 0x0, 0x00, // width
                0x00, 0x00, 0x00, 0x00, // height
                0x01, 0x00, // plans
                0x08, 0x00, // bits per pixels
                0x00, 0x00, 0x00, 0x00, // compression
                0x00, 0x00, 0x00, 0x00, // image size
                0x00, 0x00, 0x00, 0x00, // x dpi
                0x00, 0x00, 0x00, 0x00, // y dpi
                0x00, 0x00, 0x00, 0x00, // used colors
                0x00, 0x00, 0x00, 0x00  // important colors
        };

        System.arraycopy(bmpHeader, 0, header, 0, bmpHeader.length);

        // Define width and height
        header[18] = (byte)(width & 0xFF);
        header[19] = (byte)((width >> 8) & 0xFF);
        header[20] = (byte)((width >> 16) & 0xFF);
        header[21] = (byte)((width >> 24) & 0xFF);

        header[22] = (byte)(height & 0xFF);
        header[23] = (byte)((height >> 8) & 0xFF);
        header[24] = (byte)((height >> 16) & 0xFF);
        header[25] = (byte)((height >> 24) & 0xFF);

        // Color palette (grayscale)
        for (int i = 54, j = 0; i < 1078; i += 4, j++) {
            header[i] = header[i + 1] = header[i + 2] = (byte)j;
            header[i + 3] = 0;
        }

        return header;
    }

    private void horizontalMirror(byte[] image, int width, int height) {
        for (int i = 0; i < width * height / 2; i++) {
            byte temp = image[i];
            image[i] = image[(width * height - 1) - i];
            image[(width * height - 1) - i] = temp;
        }
    }

    public boolean isDeviceOpen() {
        return isDeviceOpen.get();
    }


    public static class FingerSplitResult {
        private byte[] imageData;
        private int width;
        private int height;
        private int x;
        private int y;
        private int top;
        private int left;
        private int angle;
        private int quality;

        // Getters and Setters
        public byte[] getImageData() { return imageData; }
        public void setImageData(byte[] imageData) { this.imageData = imageData; }
        public int getWidth() { return width; }
        public void setWidth(int width) { this.width = width; }
        public int getHeight() { return height; }
        public void setHeight(int height) { this.height = height; }
        public int getX() { return x; }
        public void setX(int x) { this.x = x; }
        public int getY() { return y; }
        public void setY(int y) { this.y = y; }
        public int getTop() { return top; }
        public void setTop(int top) { this.top = top; }
        public int getLeft() { return left; }
        public void setLeft(int left) { this.left = left; }
        public int getAngle() { return angle; }
        public void setAngle(int angle) { this.angle = angle; }
        public int getQuality() { return quality; }
        public void setQuality(int quality) { this.quality = quality; }
    }
}