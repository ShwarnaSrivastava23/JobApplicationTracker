import streamlit as st
import sqlite3
import hashlib
from datetime import datetime

DB_NAME = "jobtrack.db"

STATUSES = [
    "Applied",
    "Shortlisted",
    "Interview",
    "Selected",
    "Rejected"
]

# --------------------------------------------------
# PAGE CONFIG
# --------------------------------------------------

st.set_page_config(
    page_title="JobTrack",
    page_icon="💼",
    layout="wide"
)


# --------------------------------------------------
# DATABASE
# --------------------------------------------------

def get_db():
    db = sqlite3.connect(DB_NAME, check_same_thread=False)
    db.row_factory = sqlite3.Row
    return db


def hash_password(password):
    return hashlib.sha256(
        password.encode("utf-8")
    ).hexdigest()


def setup_database():

    db = get_db()

    db.execute("""
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            email TEXT UNIQUE NOT NULL,
            password TEXT NOT NULL
        )
    """)

    db.execute("""
        CREATE TABLE IF NOT EXISTS applications (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            jobtrack_id TEXT UNIQUE,
            job_reference_id TEXT,
            company TEXT NOT NULL,
            role TEXT NOT NULL,
            application_date TEXT,
            status TEXT,
            location TEXT,
            job_link TEXT,
            notes TEXT,
            user_id INTEGER
        )
    """)

    db.execute("""
        CREATE TABLE IF NOT EXISTS application_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            application_id INTEGER NOT NULL,
            status TEXT NOT NULL,
            changed_at TEXT NOT NULL
        )
    """)

    # Support the existing JobTrack database
    columns = {
        row["name"]
        for row in db.execute(
            "PRAGMA table_info(applications)"
        )
    }

    if "jobtrack_id" not in columns:
        db.execute(
            "ALTER TABLE applications ADD COLUMN jobtrack_id TEXT"
        )

    if "job_reference_id" not in columns:
        db.execute(
            "ALTER TABLE applications ADD COLUMN job_reference_id TEXT"
        )

    if "user_id" not in columns:
        db.execute(
            "ALTER TABLE applications ADD COLUMN user_id INTEGER"
        )

    # Give old applications a JobTrack ID
    old_apps = db.execute("""
        SELECT id
        FROM applications
        WHERE jobtrack_id IS NULL
        OR jobtrack_id = ''
    """).fetchall()

    for row in old_apps:
        db.execute(
            """
            UPDATE applications
            SET jobtrack_id = ?
            WHERE id = ?
            """,
            (
                f"JT-{row['id']:04d}",
                row["id"]
            )
        )

    db.commit()
    db.close()


setup_database()


# --------------------------------------------------
# HELPER FUNCTIONS
# --------------------------------------------------

def clean(value):
    return (value or "").strip()


def get_next_jobtrack_id(db):

    max_number = 0

    rows = db.execute(
        "SELECT jobtrack_id FROM applications"
    ).fetchall()

    for row in rows:

        value = row["jobtrack_id"] or ""

        if value.startswith("JT-"):

            try:
                number = int(value[3:])
                max_number = max(max_number, number)

            except ValueError:
                pass

    return f"JT-{max_number + 1:04d}"


def get_applications(user_id):

    db = get_db()

    rows = db.execute(
        """
        SELECT *
        FROM applications
        WHERE user_id = ?
        ORDER BY id DESC
        """,
        (user_id,)
    ).fetchall()

    db.close()

    return rows


def get_application(application_id, user_id):

    db = get_db()

    row = db.execute(
        """
        SELECT *
        FROM applications
        WHERE id = ?
        AND user_id = ?
        """,
        (
            application_id,
            user_id
        )
    ).fetchone()

    db.close()

    return row


# --------------------------------------------------
# ADD APPLICATION
# --------------------------------------------------

def add_application(
    user_id,
    company,
    role,
    reference,
    status,
    location,
    link,
    notes
):

    db = get_db()

    jobtrack_id = get_next_jobtrack_id(db)

    now = datetime.now()

    cursor = db.execute(
        """
        INSERT INTO applications
        (
            jobtrack_id,
            job_reference_id,
            company,
            role,
            application_date,
            status,
            location,
            job_link,
            notes,
            user_id
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (
            jobtrack_id,
            reference,
            company,
            role,
            now.strftime("%Y-%m-%d"),
            status,
            location,
            link,
            notes,
            user_id
        )
    )

    application_id = cursor.lastrowid

    db.execute(
        """
        INSERT INTO application_history
        (
            application_id,
            status,
            changed_at
        )
        VALUES (?, ?, ?)
        """,
        (
            application_id,
            status,
            now.strftime("%Y-%m-%d %H:%M:%S")
        )
    )

    db.commit()
    db.close()


# --------------------------------------------------
# UPDATE APPLICATION
# --------------------------------------------------

def update_application(
    application_id,
    user_id,
    company,
    role,
    reference,
    status,
    location,
    link,
    notes
):

    db = get_db()

    old = db.execute(
        """
        SELECT status
        FROM applications
        WHERE id = ?
        AND user_id = ?
        """,
        (
            application_id,
            user_id
        )
    ).fetchone()

    if not old:
        db.close()
        return

    db.execute(
        """
        UPDATE applications

        SET company = ?,
            role = ?,
            job_reference_id = ?,
            status = ?,
            location = ?,
            job_link = ?,
            notes = ?

        WHERE id = ?
        AND user_id = ?
        """,
        (
            company,
            role,
            reference,
            status,
            location,
            link,
            notes,
            application_id,
            user_id
        )
    )

    # Save status change in history
    if old["status"] != status:

        db.execute(
            """
            INSERT INTO application_history
            (
                application_id,
                status,
                changed_at
            )
            VALUES (?, ?, ?)
            """,
            (
                application_id,
                status,
                datetime.now().strftime(
                    "%Y-%m-%d %H:%M:%S"
                )
            )
        )

    db.commit()
    db.close()


# --------------------------------------------------
# DELETE APPLICATION
# --------------------------------------------------

def delete_application(
    application_id,
    user_id
):

    db = get_db()

    db.execute(
        """
        DELETE FROM application_history
        WHERE application_id = ?
        """,
        (application_id,)
    )

    db.execute(
        """
        DELETE FROM applications
        WHERE id = ?
        AND user_id = ?
        """,
        (
            application_id,
            user_id
        )
    )

    db.commit()
    db.close()


# --------------------------------------------------
# HISTORY
# --------------------------------------------------

def get_history(application_id):

    db = get_db()

    rows = db.execute(
        """
        SELECT status, changed_at
        FROM application_history

        WHERE application_id = ?

        ORDER BY id DESC
        """,
        (application_id,)
    ).fetchall()

    db.close()

    return rows


# --------------------------------------------------
# LOGOUT
# --------------------------------------------------

def logout():

    st.session_state.user = None
    st.session_state.page = "Dashboard"
    st.session_state.editing_id = None

    st.rerun()


# --------------------------------------------------
# SESSION STATE
# --------------------------------------------------

if "user" not in st.session_state:
    st.session_state.user = None

if "page" not in st.session_state:
    st.session_state.page = "Dashboard"

if "editing_id" not in st.session_state:
    st.session_state.editing_id = None


# --------------------------------------------------
# CSS
# --------------------------------------------------

st.markdown(
    """
    <style>

    .main-title {
        font-size: 2.5rem;
        font-weight: 700;
    }

    .sub-title {
        color: #777;
        margin-bottom: 25px;
    }

    </style>
    """,
    unsafe_allow_html=True
)


# ==================================================
# LOGIN / SIGNUP
# ==================================================

def auth_page():

    st.markdown(
        '<div class="main-title">💼 JobTrack</div>',
        unsafe_allow_html=True
    )

    st.markdown(
        '<div class="sub-title">'
        'Job Application Management System'
        '</div>',
        unsafe_allow_html=True
    )

    login_tab, signup_tab = st.tabs(
        [
            "Login",
            "Create Account"
        ]
    )

    # ---------------- LOGIN ----------------

    with login_tab:

        with st.form("login_form"):

            email = st.text_input(
                "Email"
            )

            password = st.text_input(
                "Password",
                type="password"
            )

            submitted = st.form_submit_button(
                "Login",
                use_container_width=True
            )

        if submitted:

            email = clean(email).lower()

            db = get_db()

            user = db.execute(
                """
                SELECT *
                FROM users
                WHERE email = ?
                """,
                (email,)
            ).fetchone()

            db.close()

            if (
                user
                and
                hash_password(password)
                == user["password"]
            ):

                st.session_state.user = {
                    "id": user["id"],
                    "name": user["name"],
                    "email": user["email"]
                }

                st.session_state.page = "Dashboard"

                st.rerun()

            else:

                st.error(
                    "Invalid email or password."
                )

    # ---------------- SIGNUP ----------------

    with signup_tab:

        with st.form("signup_form"):

            name = st.text_input(
                "Full Name"
            )

            email = st.text_input(
                "Email Address"
            )

            password = st.text_input(
                "Password",
                type="password"
            )

            confirm = st.text_input(
                "Confirm Password",
                type="password"
            )

            submitted = st.form_submit_button(
                "Create Account",
                use_container_width=True
            )

        if submitted:

            name = clean(name)
            email = clean(email).lower()

            if not name or not email or not password:

                st.error(
                    "Please fill all required fields."
                )

            elif password != confirm:

                st.error(
                    "Passwords do not match."
                )

            elif len(password) < 6:

                st.error(
                    "Password must contain at least 6 characters."
                )

            else:

                db = get_db()

                try:

                    db.execute(
                        """
                        INSERT INTO users
                        (name, email, password)

                        VALUES (?, ?, ?)
                        """,
                        (
                            name,
                            email,
                            hash_password(password)
                        )
                    )

                    db.commit()

                    st.success(
                        "Account created successfully. "
                        "You can now log in."
                    )

                except sqlite3.IntegrityError:

                    st.error(
                        "An account with this email already exists."
                    )

                finally:

                    db.close()


# ==================================================
# SIDEBAR
# ==================================================

def show_sidebar():

    user = st.session_state.user

    pages = [
        "Dashboard",
        "Add Application",
        "View Applications"
    ]

    with st.sidebar:

        st.title("💼 JobTrack")

        st.caption(
            f"Welcome, {user['name']}"
        )

        st.caption(
            user["email"]
        )

        st.divider()

        st.session_state.page = st.radio(
            "Navigation",
            pages,
            index=pages.index(
                st.session_state.page
            )
        )

        st.divider()

        if st.button(
            "Logout",
            use_container_width=True
        ):

            logout()


# ==================================================
# DASHBOARD
# ==================================================

def dashboard():

    applications = get_applications(
        st.session_state.user["id"]
    )

    st.markdown(
        '<div class="main-title">Dashboard</div>',
        unsafe_allow_html=True
    )

    st.markdown(
        '<div class="sub-title">'
        'Track your job applications in one place.'
        '</div>',
        unsafe_allow_html=True
    )

    counts = {}

    for status in STATUSES:

        counts[status] = sum(
            app["status"] == status
            for app in applications
        )

    cols = st.columns(6)

    cols[0].metric(
        "Total",
        len(applications)
    )

    for i, status in enumerate(STATUSES):

        cols[i + 1].metric(
            status,
            counts[status]
        )

    st.divider()

    st.subheader(
        "Recent Applications"
    )

    if not applications:

        st.info(
            "No applications yet. "
            "Use 'Add Application' to begin."
        )

        return

    for app in applications[:5]:

        with st.container(border=True):

            c1, c2, c3 = st.columns(
                [3, 2, 1]
            )

            with c1:

                st.subheader(
                    app["company"]
                )

                st.write(
                    app["role"]
                )

                st.caption(
                    f"{app['jobtrack_id']} • "
                    f"{app['application_date']}"
                )

            with c2:

                st.write(
                    f"**Status:** "
                    f"{app['status']}"
                )

                if app["location"]:

                    st.write(
                        f"📍 {app['location']}"
                    )

            with c3:

                if app["job_link"]:

                    st.link_button(
                        "Job Link",
                        app["job_link"]
                    )


# ==================================================
# ADD APPLICATION
# ==================================================

def add_application_page():

    st.title(
        "➕ Add Application"
    )

    with st.form(
        "add_application_form"
    ):

        left, right = st.columns(2)

        with left:

            company = st.text_input(
                "Company Name *"
            )

            role = st.text_input(
                "Job Role *"
            )

            reference = st.text_input(
                "Job ID / Registration ID"
            )

            location = st.text_input(
                "Location"
            )

        with right:

            status = st.selectbox(
                "Status",
                STATUSES
            )

            link = st.text_input(
                "Job Link"
            )

            notes = st.text_area(
                "Notes"
            )

        submitted = st.form_submit_button(
            "Add Application",
            use_container_width=True
        )

    if submitted:

        company = clean(company)
        role = clean(role)
        reference = clean(reference)
        location = clean(location)
        link = clean(link)
        notes = clean(notes)

        if not company or not role:

            st.error(
                "Company Name and Job Role are required."
            )

            return

        if link and not link.startswith(
            ("http://", "https://")
        ):

            st.error(
                "Job Link must start with "
                "http:// or https://"
            )

            return

        add_application(
            st.session_state.user["id"],
            company,
            role,
            reference,
            status,
            location,
            link,
            notes
        )

        st.success(
            "Application added successfully."
        )

        st.rerun()


# ==================================================
# EDIT APPLICATION
# ==================================================

def edit_application_page(
    application_id
):

    user_id = st.session_state.user["id"]

    app = get_application(
        application_id,
        user_id
    )

    if not app:

        st.error(
            "Application not found."
        )

        st.session_state.editing_id = None

        return

    st.title(
        "✏️ Edit Application"
    )

    st.caption(
        f"JobTrack ID: {app['jobtrack_id']}"
    )

    current_status = app["status"]

    if current_status not in STATUSES:

        current_status = "Applied"

    with st.form(
        "edit_application_form"
    ):

        left, right = st.columns(2)

        with left:

            company = st.text_input(
                "Company Name *",
                value=app["company"] or ""
            )

            role = st.text_input(
                "Job Role *",
                value=app["role"] or ""
            )

            reference = st.text_input(
                "Job ID / Registration ID",
                value=app["job_reference_id"] or ""
            )

            location = st.text_input(
                "Location",
                value=app["location"] or ""
            )

        with right:

            status = st.selectbox(
                "Status",
                STATUSES,
                index=STATUSES.index(
                    current_status
                )
            )

            link = st.text_input(
                "Job Link",
                value=app["job_link"] or ""
            )

            notes = st.text_area(
                "Notes",
                value=app["notes"] or ""
            )

        save_col, cancel_col = st.columns(2)

        save = save_col.form_submit_button(
            "Save Changes",
            use_container_width=True
        )

        cancel = cancel_col.form_submit_button(
            "Cancel",
            use_container_width=True
        )

    if cancel:

        st.session_state.editing_id = None

        st.rerun()

    if save:

        company = clean(company)
        role = clean(role)
        reference = clean(reference)
        location = clean(location)
        link = clean(link)
        notes = clean(notes)

        if not company or not role:

            st.error(
                "Company Name and Job Role are required."
            )

            return

        if link and not link.startswith(
            ("http://", "https://")
        ):

            st.error(
                "Invalid Job Link."
            )

            return

        update_application(
            application_id,
            user_id,
            company,
            role,
            reference,
            status,
            location,
            link,
            notes
        )

        st.success(
            "Application updated successfully."
        )

        st.session_state.editing_id = None

        st.rerun()


# ==================================================
# VIEW APPLICATIONS
# ==================================================

def view_applications_page():

    user_id = st.session_state.user["id"]

    if st.session_state.editing_id is not None:

        edit_application_page(
            st.session_state.editing_id
        )

        return

    st.title(
        "📋 Applications"
    )

    applications = get_applications(
        user_id
    )

    search = st.text_input(
        "🔍 Search by company, role, "
        "JobTrack ID or Job ID"
    )

    status_filter = st.selectbox(
        "Filter by Status",
        ["All"] + STATUSES
    )

    search = search.lower().strip()

    filtered = []

    for app in applications:

        searchable = " ".join([
            app["company"] or "",
            app["role"] or "",
            app["jobtrack_id"] or "",
            app["job_reference_id"] or "",
            app["location"] or ""
        ]).lower()

        if search and search not in searchable:

            continue

        if (
            status_filter != "All"
            and app["status"] != status_filter
        ):

            continue

        filtered.append(app)

    st.write(
        f"Showing **{len(filtered)}** application(s)."
    )

    if not filtered:

        st.info(
            "No applications match your search/filter."
        )

        return

    for app in filtered:

        with st.container(border=True):

            c1, c2 = st.columns(
                [4, 1]
            )

            with c1:

                st.subheader(
                    f"{app['company']} — "
                    f"{app['role']}"
                )

                st.caption(
                    f"JobTrack ID: "
                    f"{app['jobtrack_id']} | "
                    f"Applied: "
                    f"{app['application_date']}"
                )

            with c2:

                st.write(
                    f"**{app['status']}**"
                )

            if app["job_reference_id"]:

                st.write(
                    f"**Job ID:** "
                    f"{app['job_reference_id']}"
                )

            if app["location"]:

                st.write(
                    f"**Location:** "
                    f"{app['location']}"
                )

            if app["job_link"]:

                st.link_button(
                    "Open Job Link",
                    app["job_link"]
                )

            if app["notes"]:

                st.write(
                    f"**Notes:** "
                    f"{app['notes']}"
                )

            edit, history, delete = st.columns(3)

            if edit.button(
                "✏️ Edit",
                key=f"edit_{app['id']}",
                use_container_width=True
            ):

                st.session_state.editing_id = app["id"]

                st.rerun()

            if history.button(
                "🕒 History",
                key=f"history_{app['id']}",
                use_container_width=True
            ):

                key = f"show_history_{app['id']}"

                st.session_state[key] = not st.session_state.get(
                    key,
                    False
                )

                st.rerun()

            if delete.button(
                "🗑️ Delete",
                key=f"delete_{app['id']}",
                use_container_width=True
            ):

                st.session_state[
                    f"confirm_delete_{app['id']}"
                ] = True

                st.rerun()

            if st.session_state.get(
                f"show_history_{app['id']}",
                False
            ):

                st.markdown(
                    "**Status History**"
                )

                for item in get_history(
                    app["id"]
                ):

                    st.write(
                        f"• **{item['status']}** "
                        f"— {item['changed_at']}"
                    )

            if st.session_state.get(
                f"confirm_delete_{app['id']}",
                False
            ):

                st.warning(
                    "Are you sure you want "
                    "to delete this application?"
                )

                yes, no = st.columns(2)

                if yes.button(
                    "Yes, Delete",
                    key=f"yes_{app['id']}",
                    use_container_width=True
                ):

                    delete_application(
                        app["id"],
                        user_id
                    )

                    st.rerun()

                if no.button(
                    "Cancel",
                    key=f"no_{app['id']}",
                    use_container_width=True
                ):

                    st.session_state.pop(
                        f"confirm_delete_{app['id']}",
                        None
                    )

                    st.rerun()


# ==================================================
# MAIN
# ==================================================

if st.session_state.user is None:

    auth_page()

else:

    show_sidebar()

    if st.session_state.page == "Dashboard":

        dashboard()

    elif st.session_state.page == "Add Application":

        add_application_page()

    elif st.session_state.page == "View Applications":

        view_applications_page()