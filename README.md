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

# 🛠️ Tech Stack

<div align="center">

### 🎨 Frontend

<img src="https://img.shields.io/badge/HTML5-E34F26?style=for-the-badge&logo=html5&logoColor=white"/>
<img src="https://img.shields.io/badge/CSS3-1572B6?style=for-the-badge&logo=css3&logoColor=white"/>
<img src="https://img.shields.io/badge/JavaScript-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black"/>
<img src="https://img.shields.io/badge/Streamlit-FF4B4B?style=for-the-badge&logo=streamlit&logoColor=white"/>

### ⚙️ Backend

<img src="https://img.shields.io/badge/Python-3776AB?style=for-the-badge&logo=python&logoColor=white"/>
<img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white"/>

### 🗄️ Database

<img src="https://img.shields.io/badge/SQLite-003B57?style=for-the-badge&logo=sqlite&logoColor=white"/>

### 🔧 Tools & Deployment

<img src="https://img.shields.io/badge/Git-F05032?style=for-the-badge&logo=git&logoColor=white"/>
<img src="https://img.shields.io/badge/GitHub-181717?style=for-the-badge&logo=github&logoColor=white"/>
<img src="https://img.shields.io/badge/Streamlit%20Cloud-FF4B4B?style=for-the-badge&logo=streamlit&logoColor=white"/>

</div>

---

## 🧩 Technology Overview

| Technology | Used For |
|---|---|
| 🐍 **Python** | Main application development |
| 🎈 **Streamlit** | Web interface and application deployment |
| 🗄️ **SQLite** | Storing users, applications, and history |
| ☕ **Java** | Original backend implementation |
| 🌐 **HTML/CSS/JavaScript** | Original web interface |
| 🔐 **SHA-256** | Password hashing |
| 🔧 **Git** | Version control |
| 🐙 **GitHub** | Source code hosting |
| ☁️ **Streamlit Community Cloud** | Live deployment |

---

## 🏗️ Architecture

```text
                    👤 USER
                      │
                      ▼
              ┌───────────────┐
              │   Streamlit   │
              │  Web Interface│
              └───────┬───────┘
                      │
                      ▼
              ┌───────────────┐
              │    Python     │
              │ Application   │
              │    Logic      │
              └───────┬───────┘
                      │
                      ▼
              ┌───────────────┐
              │    SQLite     │
              │   Database    │
              └───────────────┘
                      │
          ┌───────────┼───────────┐
          ▼           ▼           ▼
       Users     Applications   History


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
