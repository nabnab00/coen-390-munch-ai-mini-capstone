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

static float readStableGramsOnce() {
  // One HX711 reading in grams using calibration factor
  return scale.get_units(1);
}

static float median7(float v[7]) {
  // Simple selection sort for 7 samples
  for (int i = 0; i < 6; i++) {
    int m = i;
    for (int j = i + 1; j < 7; j++) if (v[j] < v[m]) m = j;
    float t = v[i]; v[i] = v[m]; v[m] = t;
  }
  return v[3];
}

float readStableGrams() {
  // Median-of-7 to reduce spikes
  float v[7];
  for (int i = 0; i < 7; i++) v[i] = readStableGramsOnce();
  float med = median7(v);

  // Exponential moving average for stability
  if (!ema_valid) { ema = med; ema_valid = true; }
  ema = 0.85f * ema + 0.15f * med;

  // Clamp tiny noise to zero and prevent small negatives
  if (fabsf(ema) < DEAD_BAND) return 0.0f;
  if (ema < 0) return 0.0f;
  return ema;
}

void btSendWeight() {
  float g = readStableGrams();
  if (g > -0.5f && g < 0.5f) g = 0;     // additional clamp
  char buf[64];
  // JSON line the Android app expects: key "weight_g" with trailing newline
  snprintf(buf, sizeof(buf), "{\"weight_g\":%.2f}\n", g);
  serialBT.print(buf);
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
  scale.tare(20);                       // zero out
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

  // Optional hello so the app knows we are alive
  serialBT.println("{\"status\":\"ready\"}");
}

void loop() {
  static unsigned long lastLCD = 0;
  static unsigned long lastTx  = 0;

  // Local tare button
  if (digitalRead(TARE_BTN) == LOW) {
    scale.tare(20);
    ema_valid = false;
    lcd.setCursor(0, 1); lcd.print("Tared           ");
    delay(300);
    while (digitalRead(TARE_BTN) == LOW) { /* wait for release */ }
  }

  // Optional command handling (keep only tare; streaming removed)
  while (serialBT.available()) {
    char c = (char)serialBT.read();
    if (c == 't' || c == 'T') {
      scale.tare(20);
      ema_valid = false;
      serialBT.println("tared");
    }
    // ignore all other input
  }

  // Always transmit weight every 200 ms
  if (millis() - lastTx >= 200) {
    lastTx = millis();
    btSendWeight();
  }

  // LCD update every 500 ms
  if (millis() - lastLCD >= 500) {
    lastLCD = millis();
    float g = readStableGrams();
    if (g > -0.5f && g < 0.5f) g = 0;

    char line[17];
    snprintf(line, sizeof(line), "%8.2f g", g);

    lcd.setCursor(0, 0); lcd.print("Weight:        ");
    lcd.setCursor(8, 0); lcd.print(line);
  }
}
