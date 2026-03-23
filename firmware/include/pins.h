#pragma once
// ============================================================
// DB20G Interface Board — Pin Definitions (v10 Hardware)
// ESP32-WROOM-32E
// ============================================================

// --- Debug UART (UART0) — directly on J5 flash header ---
// GPIO1 = TX0, GPIO3 = RX0  (default Serial, used by USB-UART adapter)

// --- Radio UART (UART2) — through DPDT relay K1 ---
#define PIN_RADIO_TX   17   // GPIO17 → K1 P1_NO → radio MIC (serial mode)
#define PIN_RADIO_RX   16   // GPIO16 ← K1 P2_NO ← radio SPK (serial mode)
#define RADIO_BAUD     9600

// --- Audio (DAC / ADC) — through DPDT relay K1 ---
#define PIN_DAC_OUT    25   // GPIO25/DAC1 → K1 P1_NC → radio MIC (audio mode)
#define PIN_ADC_IN     36   // GPIO36/VP ← K1 P2_NC ← radio SPK (audio mode, via divider)

// --- PTT Control ---
#define PIN_PTT        4    // GPIO4 → R1 10k → Q1 base → drives PTT line low

// --- Relay Control ---
#define PIN_RELAY      5    // GPIO5 → R2 10k → Q2 base → drives K1 coil
                            // HIGH = relay energized = serial mode
                            // LOW  = relay released  = audio pass-through mode

// --- Status LEDs ---
#define PIN_LED1       18   // GPIO18 → R7 220Ω → LED1 (Power / heartbeat)
#define PIN_LED2       19   // GPIO19 → R8 220Ω → LED2 (PTT active)
#define PIN_LED3       21   // GPIO21 → R9 220Ω → LED3 (Audio activity)
#define PIN_LED4       22   // GPIO22 → R10 220Ω → LED4 (BT connected)

// --- Boot Pins (active pull-ups on PCB) ---
#define PIN_EN          -1  // EN is hardware only (R11 10k pull-up + C7 100nF)
#define PIN_BOOT        0   // GPIO0 = boot mode (R12 10k pull-up; LOW = flash mode)
