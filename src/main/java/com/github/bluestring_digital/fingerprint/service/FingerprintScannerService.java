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

    // Dimensões padrão
    // Dimensões padrão — agora fixas e seguras
    private static final int DEFAULT_WIDTH = 1600;
    private static final int DEFAULT_HEIGHT = 1500;
    private static final int SINGLE_WIDTH = 300;
    private static final int SINGLE_HEIGHT = 400;


    public int safeFingerQuality(byte[] imageData) {
        int width = 800;
        int height = 750;

        try {
            int quality = mosaic.MOSAIC_FingerQuality(imageData, width, height);
            log.info("Qualidade detectada com resolução {}x{} = {}", width, height, quality);
            return quality;
        } catch (Error e) {
            log.warn("Falha ao avaliar qualidade em {}x{} -> {}", width, height, e.getMessage());
            return -1;
        }
    }
    
    /**
     * Abre a conexão com o dispositivo
     */
    public boolean openDevice() {
        try {
            int result = liveScan.LIVESCAN_Init();
            if (result == 1) {
                mosaic.MOSAIC_Init();
                isDeviceOpen.set(true);
                log.info("Dispositivo aberto com sucesso");
                return true;
            }
            log.error("Falha ao abrir dispositivo: código {}", result);
            return false;
        } catch (Exception e) {
            log.error("Erro ao abrir dispositivo", e);
            return false;
        }
    }

    /**
     * Fecha a conexão com o dispositivo
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
                log.info("Dispositivo fechado com sucesso");
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Erro ao fechar dispositivo", e);
            return false;
        }
    }

    /**
     * Configura tipo de dedo (seco/normal/úmido)
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
     * Captura uma imagem raw do dispositivo
     */
    public byte[] captureRawImage(int width, int height) {
        if (!isDeviceOpen.get()) {
            throw new IllegalStateException("Dispositivo não está aberto");
        }

        // Define a janela de captura (área do sensor)
        liveScan.LIVESCAN_SetCaptWindow(0, 0, 0, width, height);

        byte[] rawData = new byte[width * height];
        int result = liveScan.LIVESCAN_GetFPRawData(0, rawData);

        if (result != 1) {
            log.error("Falha ao capturar imagem. Código: {}", result);
            throw new RuntimeException("Falha ao capturar imagem: código " + result);
        }

        log.debug("Imagem capturada com sucesso: {}x{}", width, height);
        return rawData;
    }

    /**
     * Verifica se há dedo na imagem
     */
    public boolean isFinger(byte[] imageData, int width, int height) {
        return mosaic.MOSAIC_IsFinger(imageData, width, height) > 0;
    }

    /**
     * Separa múltiplos dedos em uma imagem
     */
    public List<FingerSplitResult> splitFingers(byte[] imageData, int width, int height) {
        fpSplit.FPSPLIT_Init(width, height, 1);

        IntByReference fingerNum = new IntByReference(0);

        // Calcula o tamanho da estrutura
        FpSplit.FPSPLIT_INFO template = new FpSplit.FPSPLIT_INFO();
        int structSize = template.size();

        // Aloca memória para array de 10 estruturas
        Pointer infoArrayPtr = new Memory(structSize * 10);

        // Aloca buffers individuais para cada dedo e configura os ponteiros
        Pointer[] fingerBuffers = new Pointer[10];
        for (int i = 0; i < 10; i++) {
            fingerBuffers[i] = new Memory(SINGLE_WIDTH * SINGLE_HEIGHT);
            // Escreve o ponteiro do buffer no offset correto (24 bytes após o início de cada estrutura)
            infoArrayPtr.setPointer(i * structSize + 24, fingerBuffers[i]);
        }

        // Executa a separação
        int result = fpSplit.FPSPLIT_DoSplit(
                imageData, width, height, 1,
                SINGLE_WIDTH, SINGLE_HEIGHT,
                fingerNum, infoArrayPtr
        );

        List<FingerSplitResult> fingers = new ArrayList<>();
        int count = fingerNum.getValue();

        log.debug("Separação de dedos: resultado={}, quantidade={}", result, count);

        // Lê as informações de cada dedo detectado
        for (int i = 0; i < count && i < 10; i++) {
            // Cria estrutura apontando para o offset correto
            FpSplit.FPSPLIT_INFO info = new FpSplit.FPSPLIT_INFO(
                    infoArrayPtr.share(i * structSize)
            );
            info.read();

            // Lê os dados da imagem
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

        // Libera memória
        for (Pointer buffer : fingerBuffers) {
            if (buffer instanceof Memory) {
                // Memory é garbage collected, mas podemos liberar explicitamente
                ((Memory) buffer).clear();
            }
        }

        fpSplit.FPSPLIT_Uninit();
        return fingers;
    }

    /**
     * Converte imagem raw para BMP com cabeçalho
     */
    public byte[] rawToBmp(byte[] rawData, int width, int height) {
        byte[] bmpData = new byte[1078 + width * height];

        // Cabeçalho BMP
        byte[] header = createBmpHeader(width, height);
        System.arraycopy(header, 0, bmpData, 0, 1078);

        // Espelha verticalmente (BMP é bottom-up)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                bmpData[1078 + y * width + x] =
                        rawData[(height - y - 1) * width + x];
            }
        }

        return bmpData;
    }

    /**
     * Converte BMP para Base64 para envio ao frontend
     */
    public String bmpToBase64(byte[] bmpData) {
        return "data:image/bmp;base64," + Base64.getEncoder().encodeToString(bmpData);
    }

    /**
     * Cria template ISO da impressão digital
     */
    public byte[] createISOTemplate(byte[] imageData) {
        if (fpDevice == 0) {
            fpDevice = fpStdLib.ZAZ_FpStdLib_OpenDevice();
            if (fpDevice == 0) {
                throw new RuntimeException("Falha ao inicializar algoritmo biométrico");
            }
        }

        // Espelha horizontalmente a imagem (para compatibilidade)
        horizontalMirror(imageData, SINGLE_WIDTH, SINGLE_HEIGHT);

        byte[] template = new byte[1024];
        int result = fpStdLib.ZAZ_FpStdLib_CreateISOTemplate(fpDevice, imageData, template);

        if (result == 0) {
            throw new RuntimeException("Falha ao criar template ISO");
        }

        return template;
    }

    /**
     * Compara dois templates biométricos
     */
    public int compareTemplates(byte[] template1, byte[] template2) {
        if (fpDevice == 0) {
            throw new IllegalStateException("Dispositivo biométrico não inicializado");
        }

        return fpStdLib.ZAZ_FpStdLib_CompareTemplates(fpDevice, template1, template2);
    }

    /**
     * Detecta impressão digital falsa (fake)
     */
    public int detectFake(byte[] imageData, int width, int height) {
        return fione.GetFingerFake(imageData, width, height);
    }

    /**
     * Emite beep do dispositivo
     */
    public void beep(int times) {
        if (isDeviceOpen.get()) {
            liveScan.LIVESCAN_Beep(times);
        }
    }

    /**
     * Para captura contínua
     */
    public void stopCapture() {
        isCapturing.set(false);
    }

    // ========== Métodos auxiliares ==========

    private byte[] createBmpHeader(int width, int height) {
        byte[] header = new byte[1078];

        // Cabeçalho BMP padrão
        byte[] bmpHeader = {
                0x42, 0x4d, // BM
                0x0, 0x0, 0x0, 0x00, // tamanho do arquivo
                0x00, 0x00, 0x00, 0x00, // reservado
                0x36, 0x4, 0x00, 0x00, // offset dos dados
                0x28, 0x00, 0x00, 0x00, // tamanho do info header
                0x00, 0x00, 0x0, 0x00, // largura
                0x00, 0x00, 0x00, 0x00, // altura
                0x01, 0x00, // planos
                0x08, 0x00, // bits por pixel
                0x00, 0x00, 0x00, 0x00, // compressão
                0x00, 0x00, 0x00, 0x00, // tamanho da imagem
                0x00, 0x00, 0x00, 0x00, // dpi x
                0x00, 0x00, 0x00, 0x00, // dpi y
                0x00, 0x00, 0x00, 0x00, // cores usadas
                0x00, 0x00, 0x00, 0x00  // cores importantes
        };

        System.arraycopy(bmpHeader, 0, header, 0, bmpHeader.length);

        // Define largura e altura
        header[18] = (byte)(width & 0xFF);
        header[19] = (byte)((width >> 8) & 0xFF);
        header[20] = (byte)((width >> 16) & 0xFF);
        header[21] = (byte)((width >> 24) & 0xFF);

        header[22] = (byte)(height & 0xFF);
        header[23] = (byte)((height >> 8) & 0xFF);
        header[24] = (byte)((height >> 16) & 0xFF);
        header[25] = (byte)((height >> 24) & 0xFF);

        // Paleta de cores (escala de cinza)
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

    // ========== Classes auxiliares ==========

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

        // Getters e Setters
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