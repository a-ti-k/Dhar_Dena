# Dhar-Dena (ধার-দেনা) 💸

A modern, privacy-focused Android application for tracking personal loans, debts, and repayments cleanly and accurately.

> **Attribution & Credits:**  
> 💡 **Concept & Idea:** Created and conceptualized by **[@a-ti-k](https://github.com/a-ti-k)**  
> 🛠️ **App Engineering & Building:** Developed and implemented with assistance from **Android Studio AI Assistant**

---

## 🌟 Key Features

- **Summary Dashboard:** Instantly view total money owed to you ("You are owed") vs. money you owe ("You owe") in local currency (`৳`).
- **Loan Tracking:** Record loans given or borrowed with friend details, contact info, loan date, target repayment date, and optional notes.
- **Repayment Management:** Track partial and full repayments with dated payment histories and real-time remaining balance calculations.
- **Daily Reminders:** Optional local background notifications via Android `WorkManager` for upcoming repayment deadlines.
- **Privacy First:** 100% on-device local storage using Room/SQLite. Zero cloud analytics, zero external servers, and zero mandatory contact permissions.
- **Modern Android Design:** Built with Jetpack Compose Material 3, custom adaptive app icons, edge-to-edge layout support, and Android 12+ Splash Screen integration.

---

## 🛠️ Tech Stack

- **Language:** Kotlin 2.1
- **UI Framework:** Jetpack Compose with Material 3 Design
- **Architecture:** MVVM (Model-View-ViewModel) + Unidirectional Data Flow (StateFlow)
- **Local Database:** Room Persistence Library (SQLite)
- **Background Tasks:** Android WorkManager (`CoroutineWorker`)
- **Navigation:** Jetpack Navigation Compose
- **Target SDK:** Android 15 (API 35) / Min SDK: Android 8.0 (API 26)

---

## 🚀 Getting Started

1. **Clone the Repository:**
   ```bash
   git clone https://github.com/a-ti-k/Dhar-Dena.git
   cd Dhar-Dena
   ```
2. **Open in Android Studio:**
   - Open Android Studio (Ladybug / 2024.2+ recommended).
   - Let Gradle sync the project dependencies.
3. **Run the App:**
   - Connect an Android device (API 26+) or launch an emulator.
   - Run the `:app` configuration.

---

## 📄 License

This project is open-source and available under the [MIT License](LICENSE).
