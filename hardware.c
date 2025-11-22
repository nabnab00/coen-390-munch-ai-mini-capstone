// ===== ESP32 HX711 Scale with SSD1306 OLED (replaces LCD1602) + Classic BT SPP (always streaming) =====
// Behavior: identical to original LCD1602 sketch except for display hardware.
// Requires: ESP32 Arduino core, Bogde HX711, Adafruit SSD1306 & Adafruit GFX, BluetoothSerial

#include <Arduino.h>
#include <math.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include "HX711.h"
#include "BluetoothSerial.h"

// Fixed-PIN pairing for some core versions:
#include "esp_bt_main.h"
#include "esp_bt_device.h"
#include "esp_gap_bt_api.h"

// ---------------- HX711 ----------------
#define DOUT 16
#define SCK  23
HX711 scale;

// Adjust this to your load cell:
float CALIBRATION_FACTOR = 365.0f;

// Noise handling
const int   SAMPLE_COUNT = 30;
const float DEAD_BAND    = 0.3f;   // clamp tiny noise to 0 g
static bool ema_valid    = false;

// ---------------- OLED (SSD1306 I2C 0.96" 128x64) ----------------
#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_RESET    -1
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);
const uint8_t OLED_I2C_ADDR = 0x3C; // typical for GK-OLED-0.96

// I2C pins for ESP32 (confirmed)
const int I2C_SDA = 21;
const int I2C_SCL = 22;

// ---------------- Tare Button ----------------
#define TARE_BTN 15   // do not hold LOW at reset

// ---------------- Bluetooth SPP ----------------
BluetoothSerial serialBT;
const char* BT_NAME = "ESP32-Scale";
const bool  USE_PIN = true;          // set false to disable fixed PIN
const char* PAIR_PIN = "1234";       // 4–16 ASCII digits

// ---------------- Units ----------------
enum UnitMode { UNIT_G, UNIT_OZ, UNIT_LB, UNIT_KG };
static UnitMode unitMode = UNIT_G;

static inline const char* unitStr(UnitMode u) {
  switch (u) {
    case UNIT_G:  return "g";
    case UNIT_OZ: return "oz";
    case UNIT_LB: return "lb";
    case UNIT_KG: return "kg";
  }
  return "g";
}
static inline float toDisplayUnits(float grams) {
  switch (unitMode) {
    case UNIT_G:  return grams;
    case UNIT_OZ: return grams * 0.03527396195f;
    case UNIT_LB: return grams * 0.00220462262185f;
    case UNIT_KG: return grams * 0.001f;
  }
  return grams;
}

// ---------------- Freeze control (strict 3 s) ----------------
enum FreezeState { IDLE, MEASURING, FROZEN };
static FreezeState fstate = IDLE;
static uint32_t measureStartMs = 0;
static float frozenValue = 0.0f;     // in grams (post-tare)

const uint32_t FREEZE_AFTER_MS = 3000; // strict 3 seconds
const float    ARM_THRESHOLD    = 1.0f; // start timing when >= this (g)
const float    NEAR_ZERO        = 0.6f; // consider near zero when below
const uint32_t ZERO_HOLD_MS     = 800;  // auto-unlock hold near zero
static uint32_t zeroSinceMs     = 0;

// ---- Virtual tare in grams (do NOT change HX711 offset except at startup) ----
static float userZero = 0.0f;  // grams to subtract from live reading

// ---- Reading + display selector ----
static float lastLive = 0.0f;
static float ema = 0.0f;

// One HX711 reading in grams using calibration factor (not used in fast path)
static float readStableGramsOnce() {
  return scale.get_units(1);
}

static float median7(float v[7]) {
  for (int i = 0; i < 6; i++) {
    int m = i;
    for (int j = i + 1; j < 7; j++) if (v[j] < v[m]) m = j;
    float t = v[i]; v[i] = v[m]; v[m] = t;
  }
  return v[3];
}

// Fast, watchdog-safe reader in grams
float readStableGrams() {
  // average 3 raw reads (minimal latency) — watchdog-safe wait
  const int N = 3;
  long rawSum = 0;
  for (int i = 0; i < N; i++) {
    while (!scale.is_ready()) {
      delay(1); // yield so BT/USB stays responsive
    }
    rawSum += scale.read();
  }
  float raw = rawSum / (float)N;

  // convert using your calibration
  float g = (raw - scale.get_offset()) / CALIBRATION_FACTOR;

  // faster EMA
  static float emaFast = 0.0f;
  if (!ema_valid) { emaFast = g; ema_valid = true; }
  emaFast = 0.30f * emaFast + 0.70f * g;

  // clamp tiny/negative
  if (fabsf(emaFast) < DEAD_BAND) return 0.0f;
  if (emaFast < 0) return 0.0f;
  return emaFast;
}

// Decide what to show/transmit with virtual tare + strict 3s freeze
float getDisplayWeightGrams() {
  // live raw grams
  lastLive = readStableGrams();

  // apply virtual tare offset
  float adj = lastLive - userZero;

  // ---- state machine on adjusted value ----
  switch (fstate) {
    case IDLE:
      if (adj >= ARM_THRESHOLD) {
        fstate = MEASURING;
        measureStartMs = millis();
        zeroSinceMs = 0;
      } else {
        if (adj < NEAR_ZERO) {
          if (zeroSinceMs == 0) zeroSinceMs = millis();
        } else {
          zeroSinceMs = 0;
        }
      }
      return adj;

    case MEASURING:
      if (millis() - measureStartMs >= FREEZE_AFTER_MS) {
        frozenValue = adj;    // freeze adjusted value at exactly 3 s
        fstate = FROZEN;
        return frozenValue;
      }
      return adj;

    case FROZEN:
      // stay frozen; auto-unlock if near zero long enough
      if (adj < NEAR_ZERO) {
        if (zeroSinceMs == 0) zeroSinceMs = millis();
        if (millis() - zeroSinceMs >= ZERO_HOLD_MS) {
          fstate = IDLE;
          ema_valid = false;  // re-arm filter for next placement
          zeroSinceMs = 0;
          return adj;
        }
      } else {
        zeroSinceMs = 0;
      }
      return frozenValue;
  }
  return adj; // fallback
}

void btSendWeight() {
  float g_disp = getDisplayWeightGrams();           // grams after tare + freeze
  if (g_disp > -0.5f && g_disp < 0.5f) g_disp = 0; // additional clamp
  float shown = toDisplayUnits(g_disp);

  char buf[96];
  // Backward-compatible grams + new display value and unit
  // Example: {"weight":4.23,"unit":"oz","weight_g":120.00}
  snprintf(buf, sizeof(buf),
           "{\"weight\":%.2f,\"unit\":\"%s\",\"weight_g\":%.2f}\n",
           shown, unitStr(unitMode), g_disp);
  serialBT.print(buf);
}

// Unified command handler for USB Serial + Bluetooth
void handleCmd(char c) {
  if (c == 't' || c == 'T') {
    float disp_g = getDisplayWeightGrams(); // current adjusted+freeze-aware grams
    userZero += disp_g;                     // virtual tare to zero current display
    fstate = IDLE;                          // restart 3 s cycle
    ema_valid = false;                      // re-arm filter
    serialBT.println("tared");
    Serial.println("tared");
  } else if (c == 'r' || c == 'R') {
    userZero = 0.0f;                        // clear virtual tare
    fstate = IDLE;                          // exit frozen state
    ema_valid = false;                      // re-arm filter
    scale.tare(20);                         // optional: re-zero hardware baseline
    serialBT.println("reset");
    Serial.println("reset");
  } else if (c == 'g' || c == 'G') {
    unitMode = UNIT_G;
    serialBT.println("unit:g");
    Serial.println("unit:g");
  } else if (c == 'o' || c == 'O') {
    unitMode = UNIT_OZ;
    serialBT.println("unit:oz");
    Serial.println("unit:oz");
  } else if (c == 'l' || c == 'L') {
    unitMode = UNIT_LB;
    serialBT.println("unit:lb");
    Serial.println("unit:lb");
  }
  else if (c == 'k' || c == 'K') {
    unitMode = UNIT_KG;
    serialBT.println("unit:kg");
    Serial.println("unit:kg");
  }
}

// ---------------- Setup / Loop ----------------
void setup() {
  Serial.begin(115200);
  pinMode(TARE_BTN, INPUT_PULLUP);

  // OLED init (mimic original LCD "ESP32 Scale" / "Init..." message)
  Wire.begin(I2C_SDA, I2C_SCL);
  delay(50);
  if (!display.begin(SSD1306_SWITCHCAPVCC, OLED_I2C_ADDR)) {
    Serial.println("SSD1306 allocation failed");
    // continue - behavior otherwise same (if display not present, serial output still works)
  } else {
    display.clearDisplay();
    display.setTextSize(1);
    display.setTextColor(SSD1306_WHITE);
    display.setCursor(0, 0);
    display.println("ESP32 Scale");
    display.println("Init...");
    display.display();
  }

  // HX711 init (identical order to your original)
  scale.begin(DOUT, SCK);
  delay(200);
  scale.set_scale(CALIBRATION_FACTOR);  // set your factor
  scale.tare(20);                       // hardware zero once at boot
  ema_valid = false;

  // Bluetooth pairing PIN (legacy) - identical to original
  if (USE_PIN) {
    esp_bt_pin_type_t pin_type = ESP_BT_PIN_TYPE_FIXED;
    esp_bt_pin_code_t pin_code;
    memset(pin_code, 0, sizeof(pin_code));
    size_t n = strnlen(PAIR_PIN, 16);
    for (size_t i = 0; i < n; i++) pin_code[i] = (uint8_t)PAIR_PIN[i];
    esp_bt_gap_set_pin(pin_type, n, pin_code);
  }

  bool ok = serialBT.begin(BT_NAME);  // Classic Bluetooth SPP
  Serial.printf("BT start %s\n", ok ? "OK" : "FAIL");

  // Show main UI on OLED (mimic LCD layout)
  if (display.width() > 0) {
    display.clearDisplay();
    // Top label like LCD row 0
    display.setTextSize(1);
    display.setCursor(0, 0);
    display.print("Weight:");

    // Second row (large weight) placeholder
    display.setTextSize(2);
    display.setCursor(0, 12);
    display.print("0.00 g");

    // Small BT row at bottom (mimics LCD row 1 layout that displayed BT)
    display.setTextSize(1);
    display.setCursor(0, 52);
    display.print("BT: ");
    display.print(BT_NAME);
    display.display();
  }

  // hello so the app knows we are alive
  serialBT.println("{\"status\":\"ready\"}");
}

void loop() {
  static unsigned long lastLCD = 0;
  static unsigned long lastTx  = 0;

  // Process USB Serial commands first
  while (Serial.available()) {
    char c = (char)Serial.read();
    handleCmd(c);
  }

  // Process Bluetooth commands
  while (serialBT.available()) {
    char c = (char)serialBT.read();
    handleCmd(c);
  }

  // Local tare button -> VIRTUAL TARE
  if (digitalRead(TARE_BTN) == LOW) {
    float disp_g = getDisplayWeightGrams(); // current adjusted+freeze-aware grams
    userZero += disp_g;                     // make display drop to 0
    fstate = IDLE;                          // restart 3 s cycle
    ema_valid = false;                      // re-arm filter

    // show temporary message on OLED (mimics lcd.setCursor(0,1); lcd.print("Tared"))
    if (display.width() > 0) {
      display.clearDisplay();
      display.setTextSize(1);
      display.setCursor(0, 0);
      display.print("Weight:");
      display.setTextSize(2);
      display.setCursor(0, 12);
      display.print("Tared");
      display.display();
    }

    delay(300);
    while (digitalRead(TARE_BTN) == LOW) { /* wait for release */ }
  }

  // Always transmit weight every 100 ms
  if (millis() - lastTx >= 100) {
    lastTx = millis();
    btSendWeight();
  }

  // OLED update every 200 ms (matches original LCD update cadence)
  if (millis() - lastLCD >= 200) {
    lastLCD = millis();
    float g_disp = getDisplayWeightGrams();
    if (g_disp > -0.5f && g_disp < 0.5f) g_disp = 0;
    float shown = toDisplayUnits(g_disp);

    char line[17];
    // e.g., "  12.34 oz" fits 16 chars (same formatting as original LCD)
    snprintf(line, sizeof(line), "%8.2f %s", shown, unitStr(unitMode));

    if (display.width() > 0) {
      display.clearDisplay();

      // Top label (like LCD row 0)
      display.setTextSize(1);
      display.setCursor(0, 0);
      display.print("Weight:");

      // Weight big (mimic the LCD row 1 formatting)
      display.setTextSize(2);
      display.setCursor(0, 12);
      display.print(line);

      // Bottom small BT info (mimic LCD second row BT text presence)
      display.setTextSize(1);
      display.setCursor(0, 52);
      display.print("BT: ");
      display.print(BT_NAME);

      display.display();
    }
  }
}
