# 💼 JobTrack — Job Application Management System

<p align="center">
  <b>Track. Manage. Organize. Your Job Search.</b>
</p>

<p align="center">
  A web-based job application management system that helps users organize,
  track, and manage their job applications in one place.
</p>

<p align="center">
  <a href="https://jobapplicationtracker-a9zof8pzj5yy4vzj5depcq.streamlit.app/">
    🚀 <b>LIVE DEMO</b>
  </a>
  &nbsp;&nbsp;|&nbsp;&nbsp;
  <a href="https://github.com/ShwarnaSrivastava23/JobApplicationTracker">
    💻 <b>GITHUB</b>
  </a>
</p>

---

## 🚀 Live Demo

### 🌐 Try JobTrack Online

👉 **https://jobapplicationtracker-a9zof8pzj5yy4vzj5depcq.streamlit.app/**

The application is deployed using **Streamlit Community Cloud** and can be accessed directly through a web browser.

No local installation is required to try the live demo.

---

## 📌 About the Project

**JobTrack** is a web-based Job Application Management System developed to help users keep all their job application details organized in one place.

Instead of maintaining job applications manually in spreadsheets or notes, users can add applications, track their current status, search and filter applications, update information, and view the history of status changes.

The project provides a simple interface for managing the complete job application tracking process.

---

# ✨ Key Features

## 🔐 User Authentication

- User registration
- User login and logout
- Password hashing using SHA-256
- User-specific application data

---

## 📝 Add Job Applications

Users can add job applications with important details such as:

- Company Name
- Job Role
- Job ID / Registration ID
- Application Status
- Location
- Job Link
- Notes

Each application is automatically assigned a unique **JobTrack ID**.

Example:

```text
JT-0001
JT-0002
JT-0003

🏗️ Project Structure


JobApplicationTracker/
│
├── streamlit_app.py
├── requirements.txt
├── README.md
├── .gitignore
│
├── jobtrack.db
│
├── Database.java
├── JobTrackServer.java
├── index.html
│
├── frontend/
│   └── style.css
│
└── lib/
    └── sqlite-jdbc-3.53.4.0.jar

🔄 Application Workflow

                   ┌─────────────────┐
                   │      User       │
                   └────────┬────────┘
                            │
                            ▼
                   ┌─────────────────┐
                   │  Signup / Login │
                   └────────┬────────┘
                            │
                            ▼
                   ┌─────────────────┐
                   │    Dashboard    │
                   └────────┬────────┘
                            │
             ┌──────────────┼──────────────┐
             │              │              │
             ▼              ▼              ▼
        Add Job        View Jobs      Search / Filter
             │              │              │
             └──────────────┼──────────────┘
                            │
                            ▼
                   ┌─────────────────┐
                   │ SQLite Database │
                   └────────┬────────┘
                            │
             ┌──────────────┼──────────────┐
             │              │              │
             ▼              ▼              ▼
           Edit          Delete         History
