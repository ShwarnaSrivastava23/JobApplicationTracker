# 💼 JobTrack — Job Application Management System

A simple web-based application that helps users manage and track their job applications in one place.

---

## ✨ Features

- 🔐 User Signup and Login
- ➕ Add job applications
- ✏️ Update application details
- 🗑️ Delete applications
- 🔎 Search applications
- 📊 Track application status
- 🕒 View application history
- 💾 Store application data using SQLite
- 📱 Simple and responsive user interface

---

## 🛠️ Technologies Used

| Technology | Purpose |
|------------|---------|
| ☕ Java | Backend development |
| 🌐 HTML | Web page structure |
| 🎨 CSS | User interface styling |
| ⚡ JavaScript | Frontend functionality |
| 🗄️ SQLite | Database |
| 🔗 JDBC | Java-Database connection |

---

## 📂 Project Structure

```text
JobApplicationTracker/
│
├── frontend/
│   └── style.css
│
├── lib/
│   └── sqlite-jdbc-3.53.4.0.jar
│
├── Database.java
├── JobTrackServer.java
├── index.html
└── jobtrack.db

Working


                👤 User
                   │
                   ▼
            🔐 Login / Signup
                   │
                   ▼
          📊 JobTrack Dashboard
                   │
                   ▼
          ➕ Add Job Application
                   │
                   ▼
            🗄️ SQLite Database
                   │
                   ▼
       🔎 Search & Manage Applications
                   │
                   ▼
          🕒 Application History
