# CYPHER hardware node — ESP32-CAM + PIR

First-time friendly, ordered so that **each step works before the next one is
added**. Do not wire everything at once.

| Step | What you prove | Wires needed |
|---|---|---|
| 1 | Board, camera, USB programmer and Arduino setup work | none |
| 2 | Camera streams over WiFi | none |
| 3 | PIR detects motion | 3 wires |
| 4 | LED lights | 2 wires + resistor |
| 5 | Motion → photo → AI engine → incident | steps 2–4 |
| 6 | Buzzer (optional, last) | transistor circuit + 9 V |

---

## 0. Safety rules (read before touching anything)

1. **The 9 V adapter never touches the ESP32-CAM.** It is only for the buzzer
   circuit in step 6. The board runs from 5 V (USB/programmer).
2. **All grounds are common.** ESP32 GND, PIR GND and the 9 V supply's GND must
   be connected together in step 6, or the transistor will not switch.
3. **Unplug before rewiring.** Move wires with the power off.
4. The ESP32's pins are **3.3 V logic and very low current**. Never drive an LED
   without its resistor, never drive a buzzer directly from a pin.

---

## 1. Prove the board and camera work (no extra wiring)

1. Install **Arduino IDE 2.x**.
2. *Preferences → Additional boards manager URLs*, add:
   `https://espressif.github.io/arduino-esp32/package_esp32_index.json`
3. *Boards Manager* → install **esp32** by Espressif.
4. Plug the ESP32-CAM into the **ESP32-CAM-MB** programmer, then USB into the Mac.
   Use a **data** cable, not a charge-only one.
5. Board: **AI Thinker ESP32-CAM**. Tools:
   - PSRAM: **Enabled**
   - Partition scheme: **Huge APP (3MB No OTA/1MB SPIFFS)**
   - Upload speed: **115200** (raise later if you like)
   - Port: the new `usbserial` / `wchusbserial` entry
6. Open **File → Examples → ESP32 → Camera → CameraWebServer**.
7. In the sketch: select `#define CAMERA_MODEL_AI_THINKER` (comment the others
   out) and put your WiFi name/password in `ssid` / `password`.
8. Upload. When it finishes, open **Serial Monitor at 115200** and press the
   board's reset button once.
9. Look for `Camera Ready! Use 'http://192.168.x.x' to connect`.
10. Open that IP in a browser on the same WiFi → **Start Stream**.

If this step fails, stop here: nothing else will work until the camera does.
Checklist: correct board selected, PSRAM enabled, data cable, ribbon cable
reseated, and a strong 5 V supply (a weak USB port causes `camera init failed`
or random resets).

---

## 2. Wire the PIR (3 wires)

**Power off.** HC-SR501 pin order is printed on the board: `VCC / OUT / GND`.

| PIR | ESP32-CAM |
|---|---|
| VCC | **5V** |
| GND | **GND** |
| OUT | **GPIO 13** |

The PIR output is 3.3 V when powered at 5 V, so GPIO 13 is safe.

Then:
1. Power up and **wait 60 seconds** — the sensor calibrates and will output
   false triggers until it settles.
2. Set the **delay** potentiometer to minimum (fully anticlockwise) and leave
   sensitivity in the middle.
3. Set the jumper to **H** (repeatable triggering).

Test sketch (upload, open Serial Monitor):

```cpp
const int PIR_PIN = 13;
void setup() { Serial.begin(115200); pinMode(PIR_PIN, INPUT); }
void loop() { Serial.println(digitalRead(PIR_PIN)); delay(250); }
```

You should see `0` idle and `1` for a couple of seconds when you wave your hand.

---

## 3. Wire one LED (2 wires + resistor)

| From | Through | To |
|---|---|---|
| **GPIO 12** | **220 Ω** (or 330 Ω) | LED long leg (anode, +) |
| LED short leg (cathode, −) | — | **GND** |

Test: `pinMode(12, OUTPUT); digitalWrite(12, HIGH);` — it should light.

---

## 4. Connect it to CYPHER

Hardware side is done; now point it at the software.

1. On the Mac, find the WiFi address:

   ```bash
   ipconfig getifaddr en0
   ```

2. Start the AI engine so the board can reach it:

   ```bash
   cd ~/CYPHER/.dsh-scratch/work
   GATEWAY_URL=http://localhost:8081 \
   YOLO_WEIGHTS=~/CYPHER/.dsh-scratch/training/runs/cypher-v3-6class/weights/best.pt \
   YOLO_CONFIDENCE=0.35 \
   ~/CYPHER/.dsh-scratch/ml/bin/python -m uvicorn ai.app.main:app \
     --host 0.0.0.0 --port 5000
   ```

3. Allow incoming connections if macOS asks (System Settings → Network →
   Firewall), or the board will time out.
4. Verify from the Mac itself:

   ```bash
   curl -X POST "http://localhost:5000/monitor/frame?camera_id=1" -F file=@some_photo.jpg
   ```

   A JSON answer with `"detections"` means the engine is ready.
5. Put the Mac's IP in `hardware/esp32cam_pir/esp32cam_pir.ino`, upload it, and
   wave at the PIR. Serial shows the POST result, the cockpit shows the
   incident, and the LED corresponds to the outcome.

The AI engine accepts the photo; it logs in to the gateway itself, so the board
needs **no password**. The gateway + Postgres must be running for incidents to
appear (the engine still detects without them).

---

## 5. Buzzer (optional, last — 9 V + transistor)

Never connect the 9 V to the board. The BC547 is the switch:

```
GPIO 14 ── 1 kΩ ── BC547 base (B)
                  BC547 collector (C) ── buzzer (−)
                  buzzer (+) ─────────── +9 V
BC547 emitter (E) ─── GND (ESP32) ─── GND (9 V supply)
```

- The **two grounds must be joined**, or nothing switches.
- If the buzzer is the magnetic type, put a **1N4148 diode across the buzzer**
  (cathode/band to +9 V) to protect the transistor.
- A BC547 handles 100 mA; check the buzzer's label. If it draws more, use a
  Darlington or a small relay.
- Do not use the SD card on this board: its pins (2, 4, 12–15) clash with the
  PIR and LED.

---

## Pin summary

| Signal | GPIO | Notes |
|---|---|---|
| Camera | 0, 5, 18–27, 32–39 | reserved by the camera |
| PIR OUT | **13** | safe, input |
| LED | **12** | safe, output, with resistor |
| Buzzer control | **14** | via BC547, step 6 only |
