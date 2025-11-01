# Fingerprint Scanner API - Spring Boot

##  Overview

This is a Spring Boot REST API application for capturing and processing fingerprint images using a USB fingerprint scanner. The application interfaces with native Windows DLLs through JNA (Java Native Access) to communicate with the hardware device.

**Current Status:**  **PARTIAL IMPLEMENTATION**

###  Working Features
- Device connection (open/close)
- Single finger capture (300x400 pixels)
- Multiple fingers capture (4 fingers, 1600x1500 pixels)
- Two thumbs capture (2 fingers, 1600x1500 pixels)
- Continuous preview capture via WebSocket
- Finger quality assessment
- Finger splitting (separating multiple fingers from one image)
- Hand detection (left/right hand identification)
- Image format conversion (RAW → BMP → Base64)
- Live finger detection (fake detection)

###  Known Issues - **DEVELOPER ACTION REQUIRED**

**Template creation and comparison are NOT working properly.**

The following endpoints are **NOT functional** and require developer attention:

1. **POST `/api/fingerprint/template/create`**
   - Issue: Template generation fails or produces invalid templates
   - Likely cause: Incorrect parameters being passed to `ZAZ_FpStdLib_CreateISOTemplate`
   - File location: `FingerprintScannerService.java` → `createISOTemplate()` method
   - Developer should investigate the horizontal mirror operation and verify ISO template format

2. **POST `/api/fingerprint/template/compare`**
   - Issue: Comparison always returns low scores or errors
   - Depends on: Fix for template creation first
   - File location: `FingerprintScannerService.java` → `compareTemplates()` method
   - Developer should verify the comparison threshold (currently 45) is appropriate

**Developer Notes:**
- Check if the `horizontalMirror()` operation is necessary for the specific scanner model
- Verify that the biometric device (`fpDevice`) is properly initialized before template operations
- Consider testing with known good templates from the manufacturer's demo software
- Review the C# reference implementation in `Form1.cs` for the correct template workflow

---

##  Architecture

### Technology Stack
- **Java 17+**
- **Spring Boot 3.2.0**
- **JNA (Java Native Access) 5.13.0** - For DLL interaction
- **WebSocket** - For real-time preview streaming
- **Maven** - Build tool
---

##  Getting Started

### Prerequisites

Developer, you will need:
- **Java Development Kit (JDK) 17 or higher**
- **Maven 3.8+**
- **Windows OS** (DLLs are Windows-specific)
- **USB Fingerprint Scanner** (compatible with provided DLLs)
- **IDE** (IntelliJ IDEA, Eclipse, or VS Code recommended)

### Installation Steps

#### 1. Clone the Repository

```bash
git clone <repository-url>
cd fingerprint
```

#### 2. Place DLL Files

 **CRITICAL:** Developer, you MUST place all 5 DLL files in the `lib/` folder at the project root:


#### 3. Configure Application

Edit `src/main/resources/application.properties`:

```properties
# Server Configuration
server.port=8080

# JNA Library Path - Developer should verify this path
jna.library.path=./lib

# Logging Level
logging.level.com.github.bluestring_digital.fingerprint=DEBUG
```

#### 4. Build the Project

```bash
mvn clean install
```

If build fails with "DLL not found" errors, you should:
- Verify DLLs are in `lib/` folder
- Check that DLLs match JVM architecture (32-bit vs 64-bit)
- Ensure all 5 DLLs are present

#### 5. Run the Application

**Development Mode:**
```bash
mvn spring-boot:run
```

**Production Mode (JAR):**
```bash
java -Djna.library.path=./lib -jar target/fingerprint-scanner-1.0.0.jar
```

#### 6. Access the Application

Open browser: **http://localhost:8080**

Developer, you will see the web interface with fingerprint capture controls.

---

##  API Documentation

### Base URL
```
http://localhost:8080/api/fingerprint
```

### Device Management

#### Open Device
```http
POST /device/open
```

**Response:**
```json
{
  "success": true,
  "message": "Device connected successfully"
}
```

**Developer Note:** This must be called before any capture operations. Returns `false` if scanner is not connected or already in use.

---

#### Close Device
```http
POST /device/close
```

**Response:**
```json
{
  "success": true,
  "message": "Device disconnected successfully"
}
```

**Developer Note:** Always close device when done. Failure to close may cause issues with subsequent connections.

---

#### Get Device Status
```http
GET /device/status
```

**Response:**
```json
{
  "isOpen": true,
  "isCapturing": false
}
```

---

#### Set Finger Type
```http
POST /device/finger-type
Content-Type: application/json

{
  "type": "normal"
}
```

**Accepted Values:** `normal`, `dry`, `wet`

**Developer Note:** Adjusts scanner sensitivity for different skin conditions. Use `dry` for dry skin, `wet` for sweaty fingers.

---

### Capture Operations

#### Capture Single Finger
```http
POST /capture/single
```

**Response:**
```json
{
  "success": true,
  "image": "data:image/bmp;base64,Qk1...",
  "quality": 75,
  "width": 300,
  "height": 400
}
```

**Quality Scale:**
- 70-100: Excellent
- 50-69: Good
- 30-49: Fair
- 0-29: Poor (rejected)

**Developer Note:** Quality below 30 typically indicates finger not properly placed or dirty scanner surface.

---

#### Capture Multiple Fingers (4 Fingers)
```http
POST /capture/multiple?expectedFingers=4
```

**Response:**
```json
{
  "success": true,
  "fingers": [
    {
      "image": "data:image/bmp;base64,...",
      "quality": 80,
      "angle": 5,
      "x": 200,
      "y": 300,
      "index": 0
    },
    // ... 3 more fingers
  ],
  "count": 4,
  "quality": 78,
  "handDetection": {
    "hand": "left",
    "handDescription": "Left Hand",
    "confidence": 85,
    "reason": "4 fingers with larger gap on right (left thumb missing)",
    "fingers": [...]
  }
}
```

**Developer Note:** 
- Expects exactly 4 fingers (without thumb)
- Automatically detects left or right hand
- Returns error if wrong number of fingers detected

---

#### Capture Two Thumbs
```http
POST /capture/thumbs
```

**Response:**
```json
{
  "success": true,
  "thumbs": [
    {
      "image": "data:image/bmp;base64,...",
      "quality": 88,
      "angle": 45,
      "x": 400,
      "y": 500,
      "position": "left",
      "index": 0
    },
    {
      "image": "data:image/bmp;base64,...",
      "quality": 90,
      "angle": -42,
      "x": 1100,
      "y": 520,
      "position": "right",
      "index": 1
    }
  ],
  "count": 2,
  "handDetection": {
    "hand": "both",
    "handDescription": "Both Hands",
    "confidence": 85
  }
}
```

**Developer Note:** Position indicates relative position (left thumb is leftmost in image, not necessarily from left hand).

---

#### Start Continuous Preview
```http
POST /capture/start
```

**Response:**
```json
{
  "success": true,
  "message": "Continuous capture started"
}
```

**WebSocket Topic:** `/topic/fingerprint`

**Frame Data:**
```json
{
  "image": "data:image/bmp;base64,...",
  "quality": 65,
  "width": 800,
  "height": 750,
  "timestamp": 1699999999999
}
```

**Developer Note:** 
- Streams at ~5 FPS (200ms interval)
- Only sends frames with quality > 20
- Remember to call `/capture/stop` when done to release resources

---

### Template Operations  **NOT WORKING**

#### Create Templates
```http
POST /template/create
```

**Expected Response:**
```json
{
  "success": true,
  "templates": [
    "RklUAAABAA...",
    "RklUAAABAA..."
  ],
  "count": 2
}
```

** Current Issue:** 
- Templates are created but may be invalid
- Comparison with these templates fails
- **Developer Action Required:** Debug `createISOTemplate()` method in `FingerprintScannerService.java`

**Debugging Steps for Developer:**
1. Add logging before/after `ZAZ_FpStdLib_CreateISOTemplate` call
2. Verify `fpDevice` is not 0
3. Check if `horizontalMirror()` is needed for your scanner model
4. Compare byte array sizes with C# implementation
5. Test with manufacturer's demo software to get known good templates

---

#### Compare Templates
```http
POST /template/compare
Content-Type: application/json

{
  "template1": "RklUAAABAA...",
  "template2": "RklUAAABAA..."
}
```

**Expected Response:**
```json
{
  "success": true,
  "score": 87,
  "match": true,
  "message": "Fingerprints match (score: 87)"
}
```

** Current Issue:**
- Always returns low scores or errors
- Depends on template creation being fixed first
- **Developer Action Required:** Fix after template creation is working

**Threshold:** Score >= 45 indicates match

---

### Additional Features

#### Detect Hand
```http
POST /detect/hand?width=1600&height=1500
```

**Response:**
```json
{
  "success": true,
  "fingerCount": 4,
  "quality": 78,
  "hand": "left",
  "handDescription": "Left Hand",
  "confidence": 85,
  "fingers": [...]
}
```

**Developer Note:** Uses position analysis and angle detection to determine hand type.

---

#### Liveness Check (Fake Detection)
```http
POST /liveness/check
```

**Response:**
```json
{
  "success": true,
  "score": 145,
  "isLive": true,
  "message": "Live finger detected"
}
```

**Threshold:** Score > 120 indicates live finger

**Developer Note:** Helps detect silicone fakes, photos, or other spoofing attempts.

---

##  Development Guide

### Key Files Developer Should Know

#### 1. `FingerprintScannerService.java`
**Location:** `src/main/java/.../service/FingerprintScannerService.java`

**Purpose:** Core service handling all scanner operations

**Key Methods:**
- `openDevice()` - Initialize scanner connection
- `captureRawImage(width, height)` - Capture raw image data
- `getFingerQuality()` - Assess fingerprint quality
- `splitFingers()` - Separate multiple fingers
- `createISOTemplate()` -  **NOT WORKING** - Generate biometric template
- `compareTemplates()` -  **NOT WORKING** - Compare two templates

**Developer TODO:**
```java
// Around line 250
public byte[] createISOTemplate(byte[] imageData) {
    // TODO: Fix template generation
    // Current issue: Templates may be invalid
    // Check:
    // 1. Is horizontalMirror needed?
    // 2. Is fpDevice properly initialized?
    // 3. Are image dimensions correct?
    
    if (fpDevice == 0) {
        fpDevice = fpStdLib.ZAZ_FpStdLib_OpenDevice();
        if (fpDevice == 0) {
            throw new RuntimeException("Failed to initialize biometric algorithm");
        }
    }

    // This might be the issue - verify if needed
    horizontalMirror(imageData, SINGLE_WIDTH, SINGLE_HEIGHT);

    byte[] template = new byte[1024];
    int result = fpStdLib.ZAZ_FpStdLib_CreateISOTemplate(fpDevice, imageData, template);

    if (result == 0) {
        throw new RuntimeException("Failed to create ISO template");
    }

    return template;
}
```

---

#### 2. `FingerprintDeviceInterface.java`
**Location:** `src/main/java/.../lib/FingerprintDeviceInterface.java`

**Purpose:** JNA interface definitions for DLLs

**Developer Note:** If DLL signatures are incorrect, modify this file. Compare with C# `FingerDll.cs` for reference.

---

#### 3. `FingerprintController.java`
**Location:** `src/main/java/.../controller/FingerprintController.java`

**Purpose:** REST API endpoints

**Developer Note:** Add new endpoints here. Follow existing patterns for error handling.

---

#### 4. `HandDetectionService.java`
**Location:** `src/main/java/.../service/HandDetectionService.java`

**Purpose:** Algorithm for detecting left/right hand

**Developer Note:** Uses gap analysis and angle detection. Can be improved with machine learning.

---

### Adding New Endpoints

Developer can follow this pattern:

```java
@PostMapping("/your-endpoint")
public ResponseEntity<Map<String, Object>> yourEndpoint() {
    Map<String, Object> response = new HashMap<>();
    try {
        // Your logic here
        response.put("success", true);
        response.put("data", yourData);
        return ResponseEntity.ok(response);
        
    } catch (IllegalStateException e) {
        log.error("State error", e);
        response.put("success", false);
        response.put("message", "Device not connected");
        return ResponseEntity.status(409).body(response);
        
    } catch (Exception e) {
        log.error("Error", e);
        response.put("success", false);
        response.put("message", e.getMessage());
        return ResponseEntity.status(500).body(response);
    }
}
```

---

### Testing

#### Manual Testing with cURL

```bash
# Test device connection
curl -X POST http://localhost:8080/api/fingerprint/device/open

# Test single capture
curl -X POST http://localhost:8080/api/fingerprint/capture/single

# Test template creation (currently failing)
curl -X POST http://localhost:8080/api/fingerprint/template/create
```

#### Unit Tests

Developer, you should add tests in `src/test/java/`:

```java
@SpringBootTest
public class FingerprintServiceTest {
    
    @Autowired
    private FingerprintScannerService service;
    
    @Test
    public void testDeviceConnection() {
        boolean result = service.openDevice();
        assertTrue(result);
        service.closeDevice();
    }
    
    // TODO: Add tests for template creation
    @Test
    public void testTemplateCreation() {
        // Developer should implement this
        fail("Template creation test not implemented");
    }
}
```

---

##  Troubleshooting

### Issue: "Can't load library: GALSXXYY"

**Cause:** JNA cannot find DLLs

**Solution for Developer:**

1. Verify DLLs are in `lib/` folder:
   ```bash
   dir lib\*.dll
   ```

2. Check JVM architecture matches DLL architecture:
   ```bash
   java -version
   # Should show "64-Bit" if using 64-bit DLLs
   ```

3. Set library path explicitly:
   ```bash
   mvn spring-boot:run -Djna.library.path=./lib
   ```

4. Add debug logging:
   ```properties
   # application.properties
   jna.debug_load=true
   ```

---

### Issue: "Invalid memory access"

**Cause:** Wrong dimensions passed to DLL functions

**Solution for Developer:**

1. Check that `width * height` matches buffer size:
   ```java
   byte[] buffer = new byte[width * height];
   // Buffer must be exactly this size
   ```

2. Always call `SetCaptWindow` before capture:
   ```java
   liveScan.LIVESCAN_SetCaptWindow(0, 0, 0, width, height);
   byte[] data = new byte[width * height];
   liveScan.LIVESCAN_GetFPRawData(0, data);
   ```

3. Verify quality parameters match capture dimensions:
   ```java
   // WRONG
   int quality = mosaic.MOSAIC_FingerQuality(data, 800, 750); // Wrong dimensions

   // CORRECT
   int quality = mosaic.MOSAIC_FingerQuality(data, width, height); // Use actual dimensions
   ```

---

### Issue: Template Creation Fails

**Current Status:**  **KNOWN ISSUE**

**Steps for Developer to Debug:**

1. **Enable detailed logging:**
   ```java
   log.debug("Creating template for device: {}", fpDevice);
   log.debug("Image data size: {}", imageData.length);
   log.debug("Expected size: {}", SINGLE_WIDTH * SINGLE_HEIGHT);
   ```

2. **Check device initialization:**
   ```java
   if (fpDevice == 0) {
       log.error("Biometric device not initialized!");
       fpDevice = fpStdLib.ZAZ_FpStdLib_OpenDevice();
       log.info("Device opened: {}", fpDevice);
   }
   ```

3. **Test without horizontal mirror:**
   ```java
   // Comment out this line and test
   // horizontalMirror(imageData, SINGLE_WIDTH, SINGLE_HEIGHT);
   ```

4. **Compare with C# implementation:**
   - See `Form1.cs` in the reference C# code
   - Check if additional preprocessing is needed
   - Verify ISO template format expectations

5. **Test with manufacturer's software:**
   - Capture fingerprint with official software
   - Extract template
   - Compare with template generated by this application
   - Look for differences in byte patterns

6. **Check DLL version:**
   - Ensure `ZAZ_FpStdLib.dll` is the correct version
   - Try obtaining updated DLLs from manufacturer

---

##  Support

### Getting Help

Developer can:
1. Check this README thoroughly
2. Review reference C# implementation
3. Check application logs in `logs/` folder
4. Enable debug logging in `application.properties`
5. Contact hardware manufacturer for DLL documentation
6. Contact me through 868660661 - Miro (last option)

### Reporting Issues

When reporting issues, Developer should include:
- Java version (`java -version`)
- OS version
- DLL versions
- Full error stack trace
- Steps to reproduce
- Expected vs actual behavior

---

##  CRITICAL REMINDER FOR DEVELOPER

**Before deploying to production, Developer MUST:**

1.  Fix template creation (`createISOTemplate()` method)
2.  Fix template comparison (`compareTemplates()` method)
3.  Add comprehensive unit tests
4.  Implement security (authentication, HTTPS)
5.  Add database integration for template storage
6.  Set up proper logging and monitoring
7.  Document any workarounds or quirks discovered
8.  Update this README with solutions found

**Good luck, Developer! rsrsrs**
