// ===== ESP32 HX711 Scale with LCD1602 + Classic BT SPP (always streaming) =====
// Requires: ESP32 Arduino core, Bogde HX711, hd44780 library.

#include <Arduino.h>
#include <math.h>
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

// ---------------- LCD1602 (HD44780) ----------------
#include <hd44780.h>
#include <hd44780ioClass/hd44780_pinIO.h>
// RS, E, D4, D5, D6, D7  (LCD RW pin must be tied to GND)
const int LCD_RS = 22;
const int LCD_E  = 21;
const int LCD_D4 = 19;
const int LCD_D5 = 18;
const int LCD_D6 = 17;
const int LCD_D7 = 4;
hd44780_pinIO lcd(LCD_RS, LCD_E, LCD_D4, LCD_D5, LCD_D6, LCD_D7);

// ---------------- Tare Button ----------------
#define TARE_BTN 15   // do not hold LOW at reset

// ---------------- Bluetooth SPP ----------------
BluetoothSerial serialBT;
const char* BT_NAME = "ESP32-Scale";
const bool  USE_PIN = true;          // set false to disable fixed PIN
const char* PAIR_PIN = "1234";       // 4–16 ASCII digits

// ---------------- Helpers ----------------
static float ema = 0.0f;

// ---- Freeze control (strict 3 s) ----
enum FreezeState { IDLE, MEASURING, FROZEN };
static FreezeState fstate = IDLE;
static uint32_t measureStartMs = 0;
static float frozenValue = 0.0f;

const uint32_t FREEZE_AFTER_MS = 3000; // strict 3 seconds
const float    ARM_THRESHOLD    = 1.0f; // start timing when >= this (g)
const float    NEAR_ZERO        = 0.6f; // consider near zero when below
const uint32_t ZERO_HOLD_MS     = 800;  // auto-unlock hold near zero
static uint32_t zeroSinceMs     = 0;

// ---- Virtual tare in grams (do NOT change HX711 offset except at startup) ----
static float userZero = 0.0f;  // grams to subtract from live reading

// ---- Reading + display selector ----
static float lastLive = 0.0f;

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

// ~1–3 s settle at 10 SPS, sub-second at 80 SPS
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

  // convert using your existing calibration
  float g = (raw - scale.get_offset()) / CALIBRATION_FACTOR;

  // faster EMA: weight new data more
  static float emaFast = 0.0f;
  if (!ema_valid) { emaFast = g; ema_valid = true; }

  // 30% old, 70% new -> snappy but still stable
  emaFast = 0.30f * emaFast + 0.70f * g;

  // light clamp
  if (fabsf(emaFast) < DEAD_BAND) return 0.0f;
  if (emaFast < 0) return 0.0f;
  return emaFast;
}

// Decide what to show/transmit with virtual tare + strict 3s freeze
float getDisplayWeight() {
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
  float g = getDisplayWeight();
  if (g > -0.5f && g < 0.5f) g = 0;     // additional clamp
  char buf[64];
  // JSON line the Android app expects: key "weight_g" with trailing newline
  snprintf(buf, sizeof(buf), "{\"weight_g\":%.2f}\n", g);
  serialBT.print(buf);
}

// Unified command handler for USB Serial + Bluetooth
void handleCmd(char c) {
  if (c == 't' || c == 'T') {
    float disp = getDisplayWeight(); // current adjusted+freeze-aware value
    userZero += disp;                // virtual tare to zero current display
    fstate = IDLE;                   // restart 3 s cycle
    ema_valid = false;               // re-arm filter
    serialBT.println("tared");
    Serial.println("tared");
  } else if (c == 'r' || c == 'R') {
    userZero = 0.0f;                 // clear virtual tare
    fstate = IDLE;                   // exit frozen state
    ema_valid = false;               // re-arm filter
    scale.tare(20);                  // optional: re-zero hardware baseline
    serialBT.println("reset");
    Serial.println("reset");
  }
}

// ---------------- Setup / Loop ----------------
void setup() {
  Serial.begin(115200);
  pinMode(TARE_BTN, INPUT_PULLUP);

  // LCD init
  delay(200);
  lcd.begin(16, 2);
  lcd.clear();
  lcd.setCursor(0, 0); lcd.print("ESP32 Scale");
  lcd.setCursor(0, 1); lcd.print("Init...");

  // HX711 init
  scale.begin(DOUT, SCK);
  delay(200);
  scale.set_scale(CALIBRATION_FACTOR);  // set your factor
  scale.tare(20);                       // hardware zero once at boot
  ema_valid = false;

  // Bluetooth pairing PIN (legacy)
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

  lcd.clear();
  lcd.setCursor(0, 0); lcd.print("Weight:");
  lcd.setCursor(0, 1); lcd.print("BT: "); lcd.print(BT_NAME);

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
    float disp = getDisplayWeight(); // current adjusted+freeze-aware reading
    userZero += disp;                // make current displayed weight drop to 0
    fstate = IDLE;                   // restart 3 s cycle
    ema_valid = false;               // re-arm filter
    lcd.setCursor(0, 1); lcd.print("Tared           ");
    delay(300);
    while (digitalRead(TARE_BTN) == LOW) { /* wait for release */ }
  }

  // Always transmit weight every 100 ms
  if (millis() - lastTx >= 100) {
    lastTx = millis();
    btSendWeight();
  }

  // LCD update every 200 ms
  if (millis() - lastLCD >= 200) {
    lastLCD = millis();
    float g = getDisplayWeight();
    if (g > -0.5f && g < 0.5f) g = 0;

    char line[17];
    snprintf(line, sizeof(line), "%8.2f g", g);

    lcd.setCursor(0, 0); lcd.print("Weight:        ");
    lcd.setCursor(8, 0); lcd.print(line);
  }
}
