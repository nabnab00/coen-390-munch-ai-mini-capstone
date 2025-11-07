#include <Arduino.h>
#include "HX711.h"
#include "BluetoothSerial.h"

// Some cores need these for fixed PIN pairing:
#include "esp_bt_main.h"
#include "esp_bt_device.h"
#include "esp_gap_bt_api.h"

// ---------------- HX711 ----------------
#define DOUT 16
#define SCK  23
HX711 scale;
float CALIBRATION_FACTOR = 299.0f;   // adjust for your load cell
const int SAMPLE_COUNT = 30;
const float DEAD_BAND = 0.5f;
static bool ema_valid = false;

// ---------------- LCD1602 ----------------
#include <hd44780.h>
#include <hd44780ioClass/hd44780_pinIO.h>
const int LCD_RS = 22;
const int LCD_E  = 21;
const int LCD_D4 = 19;
const int LCD_D5 = 18;
const int LCD_D6 = 17;
const int LCD_D7 = 4;
hd44780_pinIO lcd(LCD_RS, LCD_E, LCD_D4, LCD_D5, LCD_D6, LCD_D7);

#define TARE_BTN 15

// ---------------- Bluetooth SPP ----------------
BluetoothSerial serialBT;
const char* BT_NAME = "ESP32-Scale";
const bool  USE_PIN = true;          // set false if you don’t want a PIN
const char* PAIR_PIN = "1234";       // 4–16 ASCII digits

// ---------------- Helpers ----------------
float readStableGrams() {
  // median-of-7 to reduce spikes
  float v[7];
  for (int i = 0; i < 7; i++) v[i] = scale.get_units(1);
  for (int i = 0; i < 6; i++) {
    int m = i;
    for (int j = i+1; j < 7; j++) if (v[j] < v[m]) m = j;
    float t = v[i]; v[i] = v[m]; v[m] = t;
  }
  float med = v[3];

  // exponential moving average for stability
  static float ema = 0.0f;
  if (!ema_valid) { ema = med; ema_valid = true; }
  ema = 0.85f * ema + 0.15f * med;

  // clamp tiny and negative values (match your current behavior)
  if (fabsf(ema) < DEAD_BAND) return 0.0f;
  if (ema < 0) return 0.0f;
  return ema;
}

void btSendWeightOnce() {
  float g = readStableGrams();
  if (g > -0.5f && g < 0.5f) g = 0;
  char buf[64];
  snprintf(buf, sizeof(buf), "{\"weight_g\":%.2f}\n", g);
  serialBT.print(buf);
}

// ---------------- Setup/Loop ----------------
void setup() {
  Serial.begin(115200);
  pinMode(TARE_BTN, INPUT_PULLUP);

  // LCD
  delay(200);
  lcd.begin(16, 2);
  lcd.clear();
  lcd.setCursor(0, 0); lcd.print("ESP32 Scale");
  lcd.setCursor(0, 1); lcd.print("Init...");

  // HX711
  scale.begin(DOUT, SCK);
  delay(200);
  scale.set_scale(CALIBRATION_FACTOR);
  scale.tare(20);

  // Bluetooth
  if (USE_PIN) {
    // Works across cores: set a fixed legacy PIN at GAP level
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
}

void loop() {
  static bool streamMode = false;
  static unsigned long lastLCD = 0;
  static unsigned long lastStream = 0;

  // Handle local tare button
  if (digitalRead(TARE_BTN) == LOW) {
    scale.tare(20);
    ema_valid = false;
    lcd.setCursor(0, 1); lcd.print("Tared           ");
    delay(300);
    while (digitalRead(TARE_BTN) == LOW); // wait release
  }

  // Handle BT commands
  while (serialBT.available()) {
    char c = (char)serialBT.read();
    if (c == 'w' || c == 'W') {
      btSendWeightOnce();
    } else if (c == 't' || c == 'T') {
      scale.tare(20);
      ema_valid = false;
      serialBT.println("tared");
    } else if (c == 's' || c == 'S') {
      streamMode = !streamMode;
      serialBT.print("stream:");
      serialBT.println(streamMode ? "on" : "off");
    }
  }

  // Stream mode: send weight periodically over BT
  if (streamMode && (millis() - lastStream >= 200)) {
    lastStream = millis();
    btSendWeightOnce();  // one JSON line every 200 ms
  }

  // LCD update
  if (millis() - lastLCD >= 500) {
    lastLCD = millis();
    float g = readStableGrams();
    if (g > -0.5f && g < 0.5f) g = 0;

    char line[17];
    snprintf(line, sizeof(line), "%8.2f g", g);

    lcd.setCursor(0, 0);
    lcd.print("Weight:        ");
    lcd.setCursor(8, 0);
    lcd.print(line);
  }
}
