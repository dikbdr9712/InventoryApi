# DK/Phar Inventory: putting it on a live server

One Linux server (Ubuntu 24.04 LTS works well, 2 GB RAM or more) runs everything:

```
visitor ──HTTPS──> Nginx ──┬── /            the Angular website (files)
                           ├── /api/...     Spring Boot on 127.0.0.1:8080
                           └── /uploads/... Spring Boot (product photos)
                                   │
                                 MySQL 8 (only reachable from the server itself)
```

Everything is on **one address** (for example `https://dkphar.bt`), so the session cookie, CORS and
the phone's location (which needs HTTPS) all just work.

Files used here: `deploy/nginx-dkphar.conf`, `deploy/dkphar.service`, `deploy/dkphar.env.example`,
and the database files in `database/` (see `database/README.md`).

---

## 1. Prepare the server (once)

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y nginx mysql-server certbot python3-certbot-nginx ufw
# Java 26 (the project is built for it): install a JDK 26 package, for example Eclipse Temurin 26
java -version

# firewall: only SSH and the website are open; MySQL and port 8080 stay closed to the internet
sudo ufw allow OpenSSH && sudo ufw allow 'Nginx Full' && sudo ufw enable

# a user for the application, and its folder
sudo useradd --system --home /opt/dkphar --shell /usr/sbin/nologin dkphar
sudo mkdir -p /opt/dkphar /etc/dkphar /var/www/dkphar
sudo chown dkphar:dkphar /opt/dkphar
```

Point the domain's DNS (A record) at the server's address before step 5.

## 2. The database (once)

Follow **`database/README.md` → A new server**, steps 1 to 3: run `01-create-database-and-user.sql`
with a long random password.

## 3. Settings and secrets (once)

```bash
sudo cp deploy/dkphar.env.example /etc/dkphar/dkphar.env
sudo nano /etc/dkphar/dkphar.env          # fill in: site address, database password, email server
sudo chown root:dkphar /etc/dkphar/dkphar.env && sudo chmod 640 /etc/dkphar/dkphar.env
```

The live server uses the **prod** profile (`application-prod.properties`), which:
- sends the session cookie over HTTPS only and trusts Nginx's forwarded headers;
- switches the **test payment page off**, and paying from a bank account stays off until the real RMA
  gateway is connected (`APP_PAYMENTS_BANK_MODE=rma`; test mode refuses to start on the live site);
- keeps logs at INFO and never shows program details in error answers.

## 4. Build and copy the application (every release)

On the development computer:

```bash
# backend: the jar
cd D:\Inventory\Inventory_System
mvn clean package            # runs the tests too; the jar is target/Rest-api-0.0.1-SNAPSHOT.jar

# website: the files
cd D:\Angular\inventory-project
npx ng build                 # the files are in dist/inventory-project/browser
```

Copy them to the server (WinSCP, or `scp`):

```bash
scp target/Rest-api-0.0.1-SNAPSHOT.jar  user@server:/tmp/inventory.jar
scp -r dist/inventory-project/browser/* user@server:/tmp/web/
```

On the server:

```bash
sudo mv /tmp/inventory.jar /opt/dkphar/inventory.jar && sudo chown dkphar:dkphar /opt/dkphar/inventory.jar
sudo rsync -a --delete /tmp/web/ /var/www/dkphar/
```

## 5. Start it (once, then it starts by itself after every reboot)

```bash
sudo cp deploy/dkphar.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now dkphar
journalctl -u dkphar -f                     # wait for "Started InventoryApplication"

sudo cp deploy/nginx-dkphar.conf /etc/nginx/sites-available/dkphar    # change dkphar.bt to your domain
sudo ln -s /etc/nginx/sites-available/dkphar /etc/nginx/sites-enabled/
sudo certbot --nginx -d dkphar.bt -d www.dkphar.bt                    # free HTTPS, renews itself
sudo nginx -t && sudo systemctl reload nginx
```

Check: `curl -s https://dkphar.bt/actuator/health` → `{"status":"UP"}`
(only if Nginx forwards `/actuator`; otherwise `curl -s http://127.0.0.1:8080/actuator/health` on the server).

Then **make the first admin**: `database/README.md`, steps 5 and 6.

## 6. After going live

- **Backups**: set up `database/backup.sh` (instructions inside) and copy the backups off the server.
- **Email**: send yourself a password reset from the sign-in page to check the email settings.
- **Payments from a bank account (real money)**: the connection to the RMA Payment Gateway is built
  (`RmaBankGatewayClient`). It needs DK/Phar's merchant registration with RMA. Steps:
  1. Register DK/Phar as a merchant (beneficiary) with the RMA Payment Gateway. RMA gives: a merchant id,
     the test (UAT) and live addresses, its public key / certificate, and asks for (or gives) DK/Phar's key pair.
     It may ask for the server's IP address.
  2. Put the private key on the server, readable only by the app: `/etc/dkphar/rma-merchant.pem` (PEM, PKCS#8;
     an old "BEGIN RSA PRIVATE KEY" file is converted once with
     `openssl pkcs8 -topk8 -nocrypt -in merchant.key -out rma-merchant.pem`), and RMA's key as `/etc/dkphar/rma-public.pem`.
  3. In `/etc/dkphar/dkphar.env`: `APP_PAYMENTS_BANK_MODE=rma`, `APP_PAYMENTS_BANK_RMA_URL=<RMA test address>`,
     `APP_PAYMENTS_BANK_RMA_BENEFICIARY_ID=<merchant id>`, `APP_PAYMENTS_BANK_RMA_PRIVATE_KEY=/etc/dkphar/rma-merchant.pem`,
     `APP_PAYMENTS_BANK_RMA_PUBLIC_KEY=/etc/dkphar/rma-public.pem`. Check the bank ids in `app.payments.bank.banks`
     against RMA's list (1010 Bank of Bhutan, 1020 BNB, 1030 DPNB, 1040 T Bank, 1050 BDBL, 1060 DK Bank).
  4. Restart and try every case on RMA's test address with RMA's test accounts: right code, wrong code 3 times,
     unknown account, not enough money. Compare the message fields with RMA's specification (see the comment at
     the top of RmaBankGatewayClient.java) and adjust if RMA's kit differs.
  5. Switch `APP_PAYMENTS_BANK_RMA_URL` to the live address, restart, and make one small real payment.
  If the bank's answer to a debit ever does not arrive, the payment waits under **Order verification -> Bank payments
  to check**: ask the bank, then press "Money arrived" (with the bank's journal number) or "Nothing was taken".
  Until RMA is connected customers pay by bank transfer (QR) and staff check the journal number.
- **Updates**: repeat step 4, then `sudo systemctl restart dkphar`. Database changes (new `V2__...sql`
  files) are applied by Flyway on that start. Take a backup first.

## Developer computer (Windows)

- Secrets live in `Inventory_System/secrets.properties` (git ignores it):
  `spring.datasource.password=...`
- Start the backend from IntelliJ as before; the website with `npm start` (or the preview).
- Emails and text messages are not sent: they are written to the backend's log (look for
  "Email not sent"), including password reset links, so you can test everything locally.
- Paying from a bank account is in TEST MODE: at checkout choose Bank transfer -> "Pay from my bank account",
  any bank, any account number, and the code 123456 (an account ending 0000 is "not found", 9999 has "not enough
  money", 5555 = the bank never answers, so the payment waits for staff in Order verification).
  No bank is contacted and no money moves.
