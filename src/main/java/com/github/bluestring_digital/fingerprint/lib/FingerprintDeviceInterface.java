package com.github.bluestring_digital.fingerprint.lib;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;


public interface FingerprintDeviceInterface {

    interface LiveScan extends Library {
        LiveScan INSTANCE = Native.load("lib/GALSXXYY.dll", LiveScan.class);

        int LIVESCAN_Init();
        int LIVESCAN_Close();
        int LIVESCAN_GetChannelCount();
        int LIVESCAN_SetBright(int nChannel, int nBright);
        int LIVESCAN_SetContrast(int nChannel, int nContrast);
        int LIVESCAN_GetBright(int nChannel, int[] nBright);
        int LIVESCAN_GetContrast(int nChannel, int[] nContrast);
        int LIVESCAN_GetMaxImageSize(int nChannel, int[] pnWidth, int[] pnHeight);
        int LIVESCAN_GetCaptWindow(int nChannel, int[] pnOriginX, int[] pnOriginY,
                                   int[] pnWidth, int[] pnHeight);
        int LIVESCAN_SetCaptWindow(int nChannel, int pnOriginX, int pnOriginY,
                                   int pnWidth, int pnHeight);
        int LIVESCAN_Setup();
        int LIVESCAN_BeginCapture(int nChannel);
        int LIVESCAN_GetFPRawData(int nChannel, byte[] pRawData);
        int LIVESCAN_EndCapture(int nChannel);
        int LIVESCAN_IsSupportCaptWindow(int nChannel);
        int LIVESCAN_IsSupportSetup();
        int LIVESCAN_GetPreviewImageSize();
        int LIVESCAN_GetPreviewData(int nChannel, byte[] pRawData);
        int LIVESCAN_IsSupportPreview();
        int LIVESCAN_GetVersion();
        int LIVESCAN_GetDesc(byte[] pszDesc);
        int LIVESCAN_GetErrorInfo(int nErrorNo, byte[] pszErrorInfo);
        int LIVESCAN_GetSrcFPRawData(int nChannel, byte[] pRawData);
        int LIVESCAN_GetRollFPRawData(byte[] pRawData, int width, int height);
        int LIVESCAN_GetFlatFPRawData(byte[] pRawData, int width, int height);
        int LIVESCAN_DistortionCorrection(byte[] pRawData, int width, int height, byte[] a);
        int LIVESCAN_Beep(int beepType);
        int LIVESCAN_SetLCDImage(int imageIndex);
        int LIVESCAN_SetLedLight(int imageIndex);
        int LIVESCAN_GetFingerArea(byte[] img, int width, int height);
        int LIVESCAN_SetFingerDryWet(int nLevel);
    }


    interface Mosaic extends Library {
        Mosaic INSTANCE = Native.load("lib/GAMC.dll", Mosaic.class);

        int MOSAIC_Init();
        int MOSAIC_Close();
        int MOSAIC_IsSupportIdentifyFinger();
        int MOSAIC_IsSupportImageQuality();
        int MOSAIC_IsSupportFingerQuality();
        int MOSAIC_IsSupportImageEnhance();
        int MOSAIC_IsSupportRollCap();
        int MOSAIC_SetRollMode(int nRollMode);
        int MOSAIC_Start(byte[] pFingerBuf, int nWidth, int nHeight);
        int MOSAIC_DoMosaic(byte[] pFingerBuf, int nWidth, int nHeight);
        int MOSAIC_Stop();
        int MOSAIC_ImageQuality(byte[] pFingerBuf, int nWidth, int nHeight);
        int MOSAIC_FingerQuality(byte[] pFingerBuf, int nWidth, int nHeight);
        int MOSAIC_ImageEnhance(byte[] pFingerBuf, int nWidth, int nHeight, byte[] pTargetImg);
        int MOSAIC_IsFinger(byte[] pFingerBuf, int nWidth, int nHeight);
        int MOSAIC_GetErrorInfo(int nErrorNo, byte[] pszErrorInfo);
        int MOSAIC_GetVersion();
        int MOSAIC_GetDesc(byte[] pszDesc);
    }


    interface FpSplit extends Library {
        FpSplit INSTANCE = Native.load("lib/FpSplit.dll", FpSplit.class);

        int FPSPLIT_Init(int nImgW, int nImgH, int nPreview);
        void FPSPLIT_Uninit();
        int FPSPLIT_DoSplit(byte[] pImgBuf, int nImgW, int nImgH, int nPreview,
                            int nSplitW, int nSplitH, IntByReference pnFpNum, Pointer pInfo);


        @Structure.FieldOrder({"x", "y", "top", "left", "angle", "quality", "pOutBuf"})
        class FPSPLIT_INFO extends Structure {
            public int x;        // offset 0
            public int y;        // offset 4
            public int top;      // offset 8
            public int left;     // offset 12
            public int angle;    // offset 16
            public int quality;  // offset 20
            public Pointer pOutBuf; // offset 24

            public FPSPLIT_INFO() {
                super();
            }

            public FPSPLIT_INFO(Pointer p) {
                super(p);
                read();
            }

            public byte[] getImageData(int width, int height) {
                if (pOutBuf == null) {
                    return new byte[0];
                }
                byte[] data = new byte[width * height];
                pOutBuf.read(0, data, 0, data.length);
                return data;
            }
        }
    }

    interface FpStdLib extends Library {
        FpStdLib INSTANCE = Native.load("lib/ZAZ_FpStdLib.dll", FpStdLib.class);

        int ZAZ_FpStdLib_OpenDevice();
        void ZAZ_FpStdLib_CloseDevice(int device);
        int ZAZ_FpStdLib_Calibration(int device);
        int ZAZ_FpStdLib_GetImage(int device, byte[] image);
        int ZAZ_FpStdLib_IsFinger(int device, byte[] image);
        int ZAZ_FpStdLib_GetImageQuality(int device, byte[] image);
        int ZAZ_FpStdLib_GetNFIQuality(int device, byte[] image);
        int ZAZ_FpStdLib_CreateANSITemplate(int device, byte[] image, byte[] itemplate);
        int ZAZ_FpStdLib_CreateISOTemplate(int device, byte[] image, byte[] itemplate);
        int ZAZ_FpStdLib_CompareTemplates(int device, byte[] sTemplate, byte[] fTemplate);
        int ZAZ_FpStdLib_SearchingANSITemplates(int device, byte[] sTemplate,
                                                int arrayCnt, byte[] fTemplateArray,
                                                int matchedScoreTh);
        int ZAZ_FpStdLib_SearchingISOTemplates(int device, byte[] sTemplate,
                                               int arrayCnt, byte[] fTemplateArray,
                                               int matchedScoreTh);
        int ZAZ_FpStdLib_CompressToWSQImage(int device, byte[] rawImage, byte[] wsqImage);
        int ZAZ_FpStdLib_UnCompressFromWSQImage(int device, byte[] wsqImage,
                                                int wsqSize, byte[] rawImage);
        int ZAZ_FpStdLib_GetANSIImageRecord(int device, byte[] image, byte[] itemplate);
        int ZAZ_FpStdLib_GetISOImageRecord(int device, byte[] image, byte[] itemplate);
    }

    interface Fione extends Library {
        Fione INSTANCE = Native.load("lib/ZhiAngCamera.dll", Fione.class);

        int GetImageAreaHary(byte[] img, int width, int height);
        int SetImageHary(int val);
        int SetImageArea(int val);
        int GetFingerFake(byte[] m_previewFingerPtr, int g_outWidth, int g_outHeight);
    }
}
