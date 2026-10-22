/*
 * CYPHER — ESP32-CAM + HC-SR501 motion node
 *
 * Motion -> capture a JPEG -> POST it to the CYPHER AI engine -> incident.
 *
 *   PIR OUT  -> GPIO 13
 *   LED      -> GPIO 12 (through 220-330 ohm)
 *
 * Board: AI-Thinker ESP32-CAM, PSRAM enabled. Camera pins below are the
 * official map from Espressif's CameraWebServer example.
 *
 * Edit the five constants in the CONFIG block, upload, watch the Serial
 * Monitor at 115200.
 */

#include "esp_camera.h"
#include <WiFi.h>
#include <HTTPClient.h>

// ------------------------------------------------------------- CONFIG ------
const char* WIFI_SSID = "YOUR_WIFI_NAME";
const char* WIFI_PASS = "YOUR_WIFI_PASSWORD";

// The computer running the AI engine, e.g. "http://192.168.1.10:5000"
const char* AI_ENGINE = "http://192.168.1.10:5000";
const int   CAMERA_ID = 1;

const int PIR_PIN = 13;
const int LED_PIN = 12;

const unsigned long COOLDOWN_MS = 8000;   // ignore repeats for 8 s
const int UPLOAD_RETRIES = 3;

// -------------------------------------------------------- CAMERA PINS ------
#define PWDN_GPIO_NUM   32
#define RESET_GPIO_NUM  -1
#define XCLK_GPIO_NUM    0
#define SIOD_GPIO_NUM   26
#define SIOC_GPIO_NUM   27
#define Y9_GPIO_NUM     35
#define Y8_GPIO_NUM     34
#define Y7_GPIO_NUM     39
#define Y6_GPIO_NUM     36
#define Y5_GPIO_NUM     21
#define Y4_GPIO_NUM     19
#define Y3_GPIO_NUM     18
#define Y2_GPIO_NUM      5
#define VSYNC_GPIO_NUM  25
#define HREF_GPIO_NUM   23
#define PCLK_GPIO_NUM   22

unsigned long lastCapture = 0;

bool initCamera() {
  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer   = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sccb_sda = SIOD_GPIO_NUM;
  config.pin_sccb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;
  config.frame_size = FRAMESIZE_SVGA;     // 800x600: small upload, enough detail
  config.jpeg_quality = 10;
  config.fb_count = 1;
  config.fb_location = CAMERA_FB_IN_PSRAM;
  config.grab_mode = CAMERA_GRAB_WHEN_EMPTY;

  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("Camera init failed: 0x%x\n", err);
    return false;
  }
  return true;
}

bool connectWifi() {
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  Serial.print("WiFi");
  for (int i = 0; i < 60 && WiFi.status() != WL_CONNECTED; i++) {
    delay(500);
    Serial.print(".");
  }
  Serial.println();
  if (WiFi.status() == WL_CONNECTED) {
    Serial.print("Connected: ");
    Serial.println(WiFi.localIP());
    return true;
  }
  Serial.println("WiFi failed");
  return false;
}

/** POSTs one JPEG as multipart/form-data to the AI engine. */
bool sendFrame(camera_fb_t* frame) {
  String boundary = "----cypher" + String(millis());
  String head = "--" + boundary + "\r\n"
                "Content-Disposition: form-data; name=\"file\"; filename=\"frame.jpg\"\r\n"
                "Content-Type: image/jpeg\r\n\r\n";
  String tail = "\r\n--" + boundary + "--\r\n";
  size_t total = head.length() + frame->len + tail.length();

  uint8_t* body = (uint8_t*)malloc(total);
  if (!body) {
    Serial.println("Out of memory building the upload");
    return false;
  }
  memcpy(body, head.c_str(), head.length());
  memcpy(body + head.length(), frame->buf, frame->len);
  memcpy(body + head.length() + frame->len, tail.c_str(), tail.length());

  String url = String(AI_ENGINE) + "/monitor/frame?camera_id=" + String(CAMERA_ID);
  int code = -1;
  String answer;
  for (int attempt = 1; attempt <= UPLOAD_RETRIES; attempt++) {
    HTTPClient http;
    http.setTimeout(20000);
    if (!http.begin(url)) {
      Serial.println("http.begin failed");
      break;
    }
    http.addHeader("Content-Type", "multipart/form-data; boundary=" + boundary);
    code = http.POST(body, total);
    if (code > 0) {
      answer = http.getString();
    }
    http.end();
    if (code == 200) {
      break;
    }
    Serial.printf("Upload attempt %d -> %d\n", attempt, code);
    delay(1000);
  }
  free(body);

  Serial.printf("POST %s -> %d\n", url.c_str(), code);
  if (answer.length()) {
    Serial.println(answer.substring(0, 200));
  }
  return code == 200;
}

void setup() {
  Serial.begin(115200);
  delay(300);
  pinMode(PIR_PIN, INPUT);
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);

  Serial.println("\nCYPHER motion node starting");
  if (!initCamera()) {
    Serial.println("Stopping: camera did not initialise");
    while (true) delay(1000);
  }
  if (!connectWifi()) {
    Serial.println("Stopping: no WiFi");
    while (true) delay(1000);
  }
  Serial.printf("Warming up the PIR for 60 s (ignore it until then)...\n");
  delay(60000);
  Serial.println("Ready. Wave your hand.");
}

void loop() {
  if (digitalRead(PIR_PIN) == HIGH && millis() - lastCapture > COOLDOWN_MS) {
    lastCapture = millis();

    Serial.println("Motion detected -> capturing");
    digitalWrite(LED_PIN, HIGH);

    camera_fb_t* frame = esp_camera_fb_get();
    if (!frame) {
      Serial.println("Capture failed");
      digitalWrite(LED_PIN, LOW);
      return;
    }

    bool ok = sendFrame(frame);
    esp_camera_fb_return(frame);

    // two short blinks when the engine answered, one long one when it did not
    digitalWrite(LED_PIN, LOW);
    if (ok) {
      for (int i = 0; i < 2; i++) {
        digitalWrite(LED_PIN, HIGH); delay(120);
        digitalWrite(LED_PIN, LOW);  delay(120);
      }
    } else {
      digitalWrite(LED_PIN, HIGH); delay(800);
      digitalWrite(LED_PIN, LOW);
    }
  }
  delay(20);
}
