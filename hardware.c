#include <Arduino.h>
#include <math.h>
#include <string.h>
#include "HX711.h"
#include "BluetoothSerial.h"

// ---------------- HX711 ----------------
#define DOUT 16
#define SCK 23
HX711 scale;
float CALIBRATION_FACTOR = 365.0f;

const float DEAD_BAND = 0.3f;
static bool ema_valid = false;

// ---------------- OLED (SSD1306) ----------------
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>

#define SCREEN_WIDTH 128
#define SCREEN_HEIGHT 64
#define OLED_RESET -1
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);

// ---------------- Tare Button ----------------
#define TARE_BTN 15

// ---------------- Bluetooth SPP ----------------
BluetoothSerial serialBT;
const char* BT_NAME = "ESP32_SCALE";
const bool USE_PIN = true;
const char* PAIR_PIN = "1234";

// ---------------- Units ----------------
enum UnitMode { UNIT_G, UNIT_OZ, UNIT_LB, UNIT_KG };
static UnitMode unitMode = UNIT_G;

static inline const char* unitStr(UnitMode u) {
  switch (u) {
    case UNIT_G: return "g";
    case UNIT_OZ: return "oz";
    case UNIT_LB: return "lb";
    case UNIT_KG: return "kg";
  }
  return "g";
}

static inline float toDisplayUnits(float grams) {
  switch (unitMode) {
    case UNIT_G: return grams;
    case UNIT_OZ: return grams * 0.03527396195f;
    case UNIT_LB: return grams * 0.00220462262185f;
    case UNIT_KG: return grams * 0.001f;
  }
  return grams;
}

// ---------------- Freeze control ----------------
enum FreezeState { IDLE, MEASURING, FROZEN };
static FreezeState fstate = IDLE;

static uint32_t measureStartMs = 0;
static float frozenValue = 0.0f;

const uint32_t FREEZE_AFTER_MS = 3000;
const float ARM_THRESHOLD = 1.0f;
const float NEAR_ZERO = 0.6f;
const uint32_t ZERO_HOLD_MS = 800;

static uint32_t zeroSinceMs = 0;
static float userZero = 0.0f;

static float lastLive = 0.0f;

// ---------------- Overload Control ----------------
const float MAX_CAPACITY_G = 5000.0f;
const float OVERLOAD_HYST = 200.0f;   // recover under 4800 g
bool isOverloaded = false;

// ---------------- HX711 Reader ----------------
float readStableGrams() {
  const int N = 3;
  long rawSum = 0;

  for (int i = 0; i < N; i++) {
    while (!scale.is_ready()) {
      delay(1);
    }
    rawSum += scale.read();
  }

  float raw = rawSum / (float)N;
  float g = (raw - scale.get_offset()) / CALIBRATION_FACTOR;

  static float emaFast = 0.0f;
  if (!ema_valid) {
    emaFast = g;
    ema_valid = true;
  }

  emaFast = 0.30f * emaFast + 0.70f * g;

  if (fabsf(emaFast) < DEAD_BAND) return 0.0f;
  if (emaFast < 0) return 0.0f;
  return emaFast;
}

// ---------------- Weight Logic ----------------
float getDisplayWeightGrams() {
  lastLive = readStableGrams();
  float adj = lastLive - userZero;

  // ---------- Overload detection ----------
  if (!isOverloaded) {
    if (adj > MAX_CAPACITY_G) {
      isOverloaded = true;
      fstate = IDLE;
      ema_valid = false;
    }
  } else {
    if (adj < (MAX_CAPACITY_G - OVERLOAD_HYST)) {
      isOverloaded = false;
      fstate = IDLE;
      ema_valid = false;
    }
  }

  if (isOverloaded) return adj;

  // ---------- Freeze logic ----------
  switch (fstate) {
    case IDLE:
      if (adj >= ARM_THRESHOLD) {
        fstate = MEASURING;
        measureStartMs = millis();
        zeroSinceMs = 0;
      } else {
        if (adj < NEAR_ZERO) {
          if (zeroSinceMs == 0) zeroSinceMs = millis();
        } else zeroSinceMs = 0;
      }
      return adj;

    case MEASURING:
      if (millis() - measureStartMs >= FREEZE_AFTER_MS) {
        frozenValue = adj;
        fstate = FROZEN;
        return frozenValue;
      }
      return adj;

    case FROZEN:
      if (adj < NEAR_ZERO) {
        if (zeroSinceMs == 0) zeroSinceMs = millis();
        if (millis() - zeroSinceMs >= ZERO_HOLD_MS) {
          fstate = IDLE;
          ema_valid = false;
          zeroSinceMs = 0;
          return adj;
        }
      } else zeroSinceMs = 0;

      return frozenValue;
  }
  return adj;
}

// ---------------- Bluetooth Send ----------------
void btSendWeight() {
  float g_disp = getDisplayWeightGrams();

  char buf[96];

  if (isOverloaded) {
    snprintf(buf, sizeof(buf),
             "{\"error\":\"overload\",\"weight_g\":%.2f}\n",
             g_disp);
  } else {
    if (g_disp > -0.5f && g_disp < 0.5f) g_disp = 0;
    float shown = toDisplayUnits(g_disp);

    snprintf(buf, sizeof(buf),
             "{\"weight\":%.2f,\"unit\":\"%s\",\"weight_g\":%.2f}\n",
             shown, unitStr(unitMode), g_disp);
  }

  serialBT.print(buf);
}

// ---------------- Handle Commands ----------------
void handleCmd(char c) {
  if (c == 't' || c == 'T') {
    float disp_g = getDisplayWeightGrams();
    userZero += disp_g;
    fstate = IDLE;
    ema_valid = false;
    serialBT.println("tared");
  }
  else if (c == 'r' || c == 'R') {
    userZero = 0.0f;
    fstate = IDLE;
    ema_valid = false;
    scale.tare(20);
    serialBT.println("reset");
  }
  else if (c == 'g' || c == 'G') unitMode = UNIT_G;
  else if (c == 'o' || c == 'O') unitMode = UNIT_OZ;
  else if (c == 'l' || c == 'L') unitMode = UNIT_LB;
  else if (c == 'k' || c == 'K') unitMode = UNIT_KG;
}

// ---------------- SETUP ----------------
void setup() {
  Serial.begin(115200);
  pinMode(TARE_BTN, INPUT_PULLUP);

  if (USE_PIN) serialBT.setPin(PAIR_PIN, strlen(PAIR_PIN));
  serialBT.begin(BT_NAME, false);

  // OLED init
  Wire.begin(21, 22);
  display.begin(SSD1306_SWITCHCAPVCC, 0x3C);
  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0, 0);
  display.println("ESP32 Scale Init...");
  display.display();

  // HX711 init
  scale.begin(DOUT, SCK);
  delay(200);
  scale.set_scale(CALIBRATION_FACTOR);
  scale.tare(20);
  ema_valid = false;

  display.clearDisplay();
  display.setCursor(0, 0);
  display.println("Weight:");
  display.display();

  serialBT.println("{\"status\":\"ready\"}");
}

// ---------------- LOOP ----------------
void loop() {
  static unsigned long lastTx = 0;
  static unsigned long lastOLED = 0;

  while (Serial.available()) handleCmd((char)Serial.read());
  while (serialBT.available()) handleCmd((char)serialBT.read());

  // Tare button
  if (digitalRead(TARE_BTN) == LOW) {
    float disp_g = getDisplayWeightGrams();
    userZero += disp_g;
    fstate = IDLE;
    ema_valid = false;

    display.setCursor(0, 20);
    display.println("Tared");
    display.display();

    delay(300);
    while (digitalRead(TARE_BTN) == LOW) {}
  }

  // Transmit
  if (millis() - lastTx >= 100) {
    lastTx = millis();
    btSendWeight();
  }

  // OLED update
  if (millis() - lastOLED >= 200) {
    lastOLED = millis();

    float g_disp = getDisplayWeightGrams();

    display.clearDisplay();
    display.setTextSize(1);
    display.setCursor(0, 0);

    if (isOverloaded) {
      display.println("Error");
    } else {
      if (g_disp > -0.5f && g_disp < 0.5f) g_disp = 0;
      float shown = toDisplayUnits(g_disp);

      display.println("Weight:");
      display.setTextSize(2);
      display.setCursor(0, 20);
      display.printf("%.2f %s", shown, unitStr(unitMode));
    }

    display.display();
  }
}
