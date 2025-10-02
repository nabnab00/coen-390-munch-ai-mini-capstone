# COEN 390 MUNCH AI Mini Capstone

A connected food scale that combines bluetooth weight streaming with AI-based food recognition.  

---

## 🚀 Features
- **Accurate weight measurement** using load cell + HX711 amplifier.
- **ESP32 microcontroller** with built-in bluetooth for phone connectivity.
- **Mobile app** receives real-time weight data and links with AI to identify food.
- **Nutrition logging**: combines identified food + measured weight to compute calories/macros.
- **Tare & calibration support** for accurate daily use.

---

## 🛠 Hardware
- **5 kg Load Cell + HX711 amplifier** – measures food weight
- **ESP32 (WROOM32D / S3)** – microcontroller with bluetooth
- **ESP32-S3 with LCD** - microcontroller with LCD screen
- **housing**

---

## 📱 Software
- **Firmware**: ESP32 reads HX711 and sends info via bluetooth
  - `weight_g` (grams, float32)
  - `tare_cmd` (write to tare)
  - `cal_factor` (read/write calibration)
- **Mobile app**:  
  - Scans and connects via BLE
  - Displays live weight
  - Captures food photo → runs AI classifier
  - Logs macros automatically

