# DK/Phar on free hosting: Render + Aiven

Three free parts:

| Part | Where | What it is |
|---|---|---|
| Database | **Aiven** (free MySQL) | 1 GB storage, no credit card. Tables are made by the app itself. |
| Backend (InventoryApi) | **Render web service** (free, Docker) | Spring Boot. 512 MB, sleeps after 15 min without visitors. |
| Website (InventoryWeb) | **Render static site** (free) | The Angular site. It forwards `/api/*` and `/uploads/*` to the backend, so the website and the API share one address (sign-in works in every browser, iPhones too). |

Order: **1 database → 2 backend → 3 website → 4 connect them → 5 first admin.** About 45 minutes.

---

## 0. Push the latest code

Both repositories must contain this version (it has the `Dockerfile`, `.node-version`, and photos stored in the
database). In each folder:

```
git add -A
git commit -m "Ready for Render"
git push
```

(`D:\Inventory` is InventoryApi, `D:\Angular\inventory-project` is InventoryWeb.)

---

## 1. Database: free MySQL on Aiven

1. Go to **https://aiven.io** → **Sign up** (Google or GitHub is fine). No card is needed.
2. **Create service** → **MySQL** → plan **Free**. Pick the region closest to Bhutan that is offered
   (Singapore / Asia if listed). Name it `dkphar-db`. **Create**.
3. Wait until it says **Running** (a few minutes).
4. On the service's **Overview** page, note: **Host**, **Port**, **User** (`avnadmin`), **Password**,
   **Database** (`defaultdb`).
5. Your database address for the app is (put in your host and port):

   ```
   jdbc:mysql://HOST:PORT/defaultdb?sslMode=REQUIRED&serverTimezone=UTC
   ```

Nothing to run: the app creates every table on its first start (Flyway, versions 1 to 7).

---

## 2. Backend: Render web service (Docker)

1. Go to **https://render.com** → **Sign up with GitHub** → allow access to **InventoryApi** (and InventoryWeb).
2. **New** → **Web Service** → choose **InventoryApi**.
3. Fill in:

   | Setting | Value |
   |---|---|
   | Name | `dkphar-api` (your address becomes `https://dkphar-api.onrender.com`; Render may add letters if taken) |
   | Language | **Docker** |
   | Branch | `main` |
   | Region | **Singapore** (closest to Bhutan) |
   | Root Directory | `Inventory_System` |
   | Dockerfile Path | `./Dockerfile` |
   | Instance Type | **Free** |

4. **Environment Variables** (Add environment variable, one by one):

   | Key | Value |
   |---|---|
   | `SPRING_DATASOURCE_URL` | the `jdbc:mysql://...` address from step 1.5 |
   | `SPRING_DATASOURCE_USERNAME` | `avnadmin` |
   | `SPRING_DATASOURCE_PASSWORD` | the Aiven password |
   | `APP_FILES_STORE` | `database` (photos and documents kept in MySQL: Render's free disk is wiped on every restart) |
   | `APP_PUBLIC_URL` | `https://dkphar-web.onrender.com` (the website's address from step 3; correct it there if Render gives another) |

   For a **demo** where people can try paying (test mode, no real money, the code is 123456), also add:

   | Key | Value |
   |---|---|
   | `APP_PAYMENTS_BANK_MODE` | `test` |
   | `APP_PAYMENTS_BANK_TEST_ON_LIVE_SITE` | `true` |

   Without these two, checkout says "Online payment is not available right now" until the RMA gateway is
   connected (DEPLOY.md, "Payments from a bank account").

5. **Advanced** → **Health Check Path**: `/actuator/health`.
6. **Create Web Service**. The first build takes 5 to 10 minutes. In **Logs**, success looks like
   `Successfully applied 7 migrations` and then `Started InventoryApplication`.
7. Check: open `https://dkphar-api.onrender.com/actuator/health` → `{"status":"UP"}`.

---

## 3. Website: Render static site

1. **New** → **Static Site** → choose **InventoryWeb**.
2. Fill in:

   | Setting | Value |
   |---|---|
   | Name | `dkphar-web` |
   | Branch | `main` |
   | Build Command | `npm ci && npm run build` |
   | Publish Directory | `dist/inventory-project/browser` |

   (Node 24 is chosen by the `.node-version` file in the repository.)
3. **Create Static Site** and wait for **Live**.

---

## 4. Connect the website to the backend

1. Static site → **Redirects/Rewrites** → add these three rules **in this order** (all **Rewrite**), using your
   backend's real address:

   | Source | Destination | Action |
   |---|---|---|
   | `/api/*` | `https://dkphar-api.onrender.com/api/*` | Rewrite |
   | `/uploads/*` | `https://dkphar-api.onrender.com/uploads/*` | Rewrite |
   | `/*` | `/index.html` | Rewrite |

   Save. (The first two send data and photo requests to the backend; the last lets pages like `/admin/orders`
   open directly.) Check that the Action really says **Rewrite**: with **Redirect** the site fails with a CORS
   error, and Chrome then remembers the redirect (clear "Cached images and files" or use an Incognito window).
2. Backend → **Environment** → set these two to the website's real address (no `/` at the end) → **Save** (it
   redeploys):

   | Key | Value |
   |---|---|
   | `APP_PUBLIC_URL` | `https://dkphar-web.onrender.com` |
   | `APP_CORS_ALLOWED_ORIGINS` | `https://dkphar-web.onrender.com` |

   The second one is needed because the forwarded requests reach the backend under its own address: without it,
   sign-up and sign-in fail with "Invalid CORS request" (reading products still works).
3. Open the website. Products load (empty at first) and **Sign up** works.

---

## 5. Make yourself the admin

1. On the website, **Sign up** with your email.
2. In **MySQL Workbench**: **+** new connection → Hostname, Port, Username `avnadmin`, Default schema `defaultdb` →
   **SSL** tab: Use SSL = **Require** → **Store in Vault** the password → **Test Connection** → OK.
3. **File → Open SQL Script** → `D:\Inventory\database\02-first-admin.sql` → put **your** email in the first line
   → run all (⚡). The result shows your account with role ADMIN.

   Or in PowerShell (put in your host, port and email first):

   ```
   & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" --ssl-mode=REQUIRED -h HOST -P PORT -u avnadmin -p defaultdb -e "UPDATE users SET role_id=(SELECT id FROM roles WHERE name='ADMIN'), active=b'1' WHERE email='you@example.com';"
   ```
4. Sign out and in again: the staff menus appear. Add products, give people roles in **People & access**.

---

## 6. (Optional) Copy what you already have on this computer

Products, photos, customers, users and orders from your local `inventorydb`:

1. Restart the backend on this computer once with photos in the database: add `app.files.store=database` to
   `Inventory_System/secrets.properties`, start it from IntelliJ, and look for `Copied N file(s) ... into the
   database` in the log. (It also brings the local database to version 7.)
2. Export it (PowerShell):
   ```
   & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysqldump.exe" -u root -p --single-transaction --set-gtid-purged=OFF --no-tablespaces inventorydb --result-file=D:\inventorydb.sql
   ```
3. On Render, backend → **Settings** → **Suspend** (so it does not write meanwhile).
4. Import into Aiven (this **replaces** what is there):
   ```
   cmd /c '"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" --ssl-mode=REQUIRED -h HOST -P PORT -u avnadmin -p defaultdb < D:\inventorydb.sql'
   ```
5. Backend → **Resume**. Your existing accounts and passwords work; step 5 is not needed.

---

## Good to know about the free plans

- **Sleep:** with no visitors for 15 minutes the backend sleeps. The next visitor sees the website at once, but the
  data takes 1 to 3 minutes to appear (the free CPU is small). To keep it awake, a free monitor such as
  UptimeRobot can open `https://dkphar-api.onrender.com/actuator/health` every 10 minutes; the 750 free hours a
  month are enough for one service running all month.
- **Space:** Aiven free is 1 GB, and photos count. Use photos under 1 MB where you can.
- **Idle database:** Aiven may switch off a free database that is not used for a long time (it emails first).
  Switch it on again in the Aiven console.
- **Emails** (password reset by email, order updates) are not sent until you add an email account. Until then the
  "Forgot password?" page tells customers to call or message the shop, and staff give them a temporary password in
  **People & access**. Render's free plan blocks the usual email ports (25, 465, 587), so Gmail does not work here.
  Brevo's free plan (300 emails a day) also listens on port **2525**, which is not blocked:
  1. Sign up at brevo.com. Under **Senders, domains & dedicated IPs → Senders**, add and confirm the address the
     emails come from.
  2. **SMTP & API → SMTP**: copy the **Login** and create an **SMTP key** (not an API key).
  3. Render → InventoryApi → **Environment**, add:

     | Key | Value |
     |---|---|
     | `APP_MAIL_ENABLED` | `true` |
     | `SPRING_MAIL_HOST` | `smtp-relay.brevo.com` |
     | `SPRING_MAIL_PORT` | `2525` |
     | `SPRING_MAIL_USERNAME` | the Login from step 2 |
     | `SPRING_MAIL_PASSWORD` | the SMTP key from step 2 |
     | `APP_MAIL_FROM` | `DK/Phar <the address you confirmed in step 1>` |

  4. **Save, rebuild, and deploy**. The "Forgot password?" page then shows the form that emails a reset code.
- **Updates:** push to GitHub and Render deploys both by itself. Database changes (new `V9__...sql`) apply on start.
- **Passwords** go only into Render's Environment page, never into the code.
- **Faster and still free:** an Oracle Cloud "Always Free" server (no sleeping, much more memory) follows DEPLOY.md
  instead; it needs a card for identity checks and some Linux work.
