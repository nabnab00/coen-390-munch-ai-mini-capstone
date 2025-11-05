#include <Arduino.h>
#include <WiFi.h>
#include <WebServer.h>
#include "HX711.h"

// ------- HX711 -------
#define DOUT 16          // HX711 DT
#define SCK   23          // HX711 SCK  (boot strap pin; works, but avoid if you can)
HX711 scale;
float CALIBRATION_FACTOR = 349.0f;   // set yours
const int SAMPLE_COUNT = 30;
const float DEAD_BAND = 0.5f;   // ignore tiny noise
static bool ema_valid = false;  // for filter reset on tare


// ------- Wi-Fi / HTTP -------
const char* WIFI_SSID = "BELL241";
const char* WIFI_PASS = "jkvr65790204$";
WebServer server(80);

// ------- LCD1602 (HD44780, 4-bit) -------
#include <hd44780.h>
#include <hd44780ioClass/hd44780_pinIO.h>
// RS, E, D4, D5, D6, D7  (RW on LCD must be tied to GND)
const int LCD_RS = 22;
const int LCD_E  = 21;
const int LCD_D4 = 19;
const int LCD_D5 = 18;
const int LCD_D6 = 17;
const int LCD_D7 = 4;    // per your wiring
hd44780_pinIO lcd(LCD_RS, LCD_E, LCD_D4, LCD_D5, LCD_D6, LCD_D7);

#define TARE_BTN 15


// ------- helpers -------
float readStableGrams() {
  // median-of-7 to kill spikes
  float v[7];
  for (int i = 0; i < 7; i++) v[i] = scale.get_units(1);
  // simple selection sort for small N
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

  // clamp tiny and negative values
  if (fabsf(ema) < DEAD_BAND) return 0.0f;
  if (ema < 0) return 0.0f;
  return ema;
}


void handleRoot() {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "text/html",
    "<h3>ESP32 Scale</h3>"
    "<p><a href='/weight'>/weight</a> &nbsp; <a href='/tare'>/tare</a></p>");
}

void handleWeight() {
  float g = readStableGrams();
  if (g > -0.5f && g < 0.5f) g = 0;
  char buf[64];
  snprintf(buf, sizeof(buf), "{\"weight_g\":%.2f}", g);
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "application/json", buf);
}

void handleTare() {
  scale.tare(20);
  ema_valid = false;   // reset filter after taring
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.send(200, "text/plain", "tared");
}

void connectWiFi() {
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  Serial.print("Connecting to WiFi");
  while (WiFi.status() != WL_CONNECTED) { delay(500); Serial.print("."); }
  Serial.println();
  Serial.print("IP: "); Serial.println(WiFi.localIP());
}

// ------- setup/loop -------
void setup() {
  Serial.begin(115200);
pinMode(TARE_BTN, INPUT_PULLUP);

  // LCD first so you can see messages even before Wi-Fi
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

  // Wi-Fi + server
  connectWiFi();
  server.on("/",        HTTP_GET, handleRoot);
  server.on("/weight",  HTTP_GET, handleWeight);
  server.on("/tare",    HTTP_GET, handleTare);
  server.onNotFound([](){ server.send(404, "text/plain", "Not Found"); });
  server.begin();

  // Show IP on LCD
  lcd.clear();
  lcd.setCursor(0, 0); lcd.print("Weight:");
  lcd.setCursor(0, 1); lcd.print(WiFi.localIP());
}

void loop() {
  server.handleClient();

// Check button press for tare
if (digitalRead(TARE_BTN) == LOW) {
  scale.tare(20);
  ema_valid = false;         // reset filter if you’re using it
  lcd.setCursor(0, 1);
  lcd.print("Tared!         ");
  delay(1000);               // short display hold
  lcd.setCursor(0, 1);
  lcd.print("               ");  // clear message
  while (digitalRead(TARE_BTN) == LOW); // wait for release
}

  // Periodic LCD update
  static unsigned long last = 0;
  if (millis() - last >= 500) {
    last = millis();
    float g = readStableGrams();
    if (g > -0.5f && g < 0.5f) g = 0;

    char line[17];
    snprintf(line, sizeof(line), "%8.2f g", g);

    lcd.setCursor(0, 0);              // "Weight:"
    lcd.print("Weight:        ");      // pad to clear old chars
    lcd.setCursor(8, 0);
    lcd.print(line);
  }

  // simple auto-reconnect
  static unsigned long lastCheck = 0;
  if (millis() - lastCheck > 5000) {
    lastCheck = millis();
    if (WiFi.status() != WL_CONNECTED) connectWiFi();
  }
}
