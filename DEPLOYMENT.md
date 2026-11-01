# TripWise AI: setup, build and deployment

The app has four parts. Only the frontend is ever public.

```
Browser ──HTTPS──> reverse proxy (Caddy) ──> frontend (Next.js BFF, :3000)
                                               └──> backend (Spring Boot, :8080) ──> PostgreSQL 16 (:5432)
                                                                                └──> ai-service (FastAPI, :8000) ──> Gemini
```

- The browser talks only to Next.js. Next.js keeps the JWTs in httpOnly cookies and proxies `/api/backend/*` to Spring.
- Spring calls the ai-service with the `X-Internal-Token` header. Keep ports 8080, 8000 and 5432 private.

> Free-tier limits and prices change often. They are summarised here as of late 2026 and are **not guaranteed**;
> check each provider's page before you sign up.

---

## 1. Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| JDK | 21 (Temurin or Microsoft build) | Spring backend (non-Docker) |
| Maven | 3.9+ (no wrapper in repo) | Spring backend (non-Docker) |
| Node.js | 24 (as used by `frontend/Dockerfile`) | Frontend (non-Docker) |
| Python | 3.12 | ai-service (non-Docker) |
| Docker + Compose v2 | recent | Docker setups |

## 2. Required secrets and settings

| Variable | Used by | Notes |
|---|---|---|
| `JWT_SECRET` | backend | **Required.** Base64 key of at least 256 bits; startup fails otherwise |
| `AI_SERVICE_API_KEY` | backend | **Required.** Must equal the ai-service `INTERNAL_API_TOKEN` (min 16 chars) |
| `INTERNAL_API_TOKEN` | ai-service | Same value as above |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | backend | Default `jdbc:postgresql://localhost:5432/tripwise_db` |
| `AI_SERVICE_BASE_URL` | backend | e.g. `http://localhost:8000` or `http://ai-service:8000` in Compose |
| `LLM_PROVIDER`, `GEMINI_API_KEY`, `GEMINI_MODEL`, `OPENROUTER_API_KEY`, `OPENROUTER_MODEL` | ai-service | `fake` works offline with no key; `gemini` or `openrouter` (any model) for real plans |
| `BACKEND_URL` | frontend | Spring base URL, no trailing slash |
| `TRUST_PROXY_HEADERS` | frontend | `true` only behind a trusted reverse proxy (Caddy/Nginx) |
| `RATE_LIMIT_*`, `OPENAPI_ENABLED`, `CORS_ALLOWED_ORIGINS` | backend | Optional, see `application.yml` |

The other ai-service options are listed in `ai-service/.env.example` (FX, grounding, daily caps, affiliate IDs).

**Generate secrets:**

```bash
# Linux / macOS
openssl rand -base64 32        # JWT_SECRET
openssl rand -hex 24           # AI_SERVICE_API_KEY / INTERNAL_API_TOKEN
```

```powershell
# Windows PowerShell 5
$b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

Never commit real values. Keep them in `.env` files (git-ignored) or in your cloud's secret store.

---

## 3. Local development without Docker (H2)

Uses the temporary H2 profile, so PostgreSQL isn't needed. Open three terminals from the repo root.

**a) ai-service**

```powershell
cd ai-service
python -m venv .venv
.\.venv\Scripts\python -m pip install -e ".[dev]"
Copy-Item .env.example .env          # LLM_PROVIDER=fake, no Gemini key needed
.\.venv\Scripts\uvicorn app.main:app --port 8000
```

On Linux/macOS use `.venv/bin/...` and `cp`.

**b) Spring backend (H2 file DB in `./data/`)**

```powershell
$env:JAVA_HOME = "<path to JDK 21>"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET = "<base64 secret>"
$env:AI_SERVICE_API_KEY = "tripwise-local-internal-token"   # = INTERNAL_API_TOKEN in ai-service/.env
$env:AI_SERVICE_BASE_URL = "http://localhost:8000"
mvn spring-boot:run "-Dspring-boot.run.profiles=h2"
```

In IntelliJ, set Active profiles = `h2` and add the same environment variables to the run configuration.

**c) Frontend**

```powershell
cd frontend
Copy-Item .env.example .env.local    # BACKEND_URL=http://localhost:8080
npm ci
npm run dev                          # http://localhost:3000
```

**Checks:**
- Backend: `mvn test`
- ai-service: `.\.venv\Scripts\python -m pytest -q`
- Frontend: `npm run lint`, `npm run typecheck`, `npm test`, `npm run build`

## 4. Local development with Docker (PostgreSQL)

The existing `docker-compose.yml` runs PostgreSQL, the ai-service and the frontend. Spring runs on the host so you can debug it in IntelliJ.

```powershell
docker compose up -d --build         # postgres :5432, ai-service 127.0.0.1:8000, frontend :3000
$env:JWT_SECRET = "<base64 secret>"
$env:AI_SERVICE_API_KEY = "tripwise-local-internal-token"
$env:AI_SERVICE_BASE_URL = "http://localhost:8000"
mvn spring-boot:run                  # default profile = PostgreSQL; Flyway runs db/migration
```

- To use a real LLM, set either `LLM_PROVIDER=gemini` with `GEMINI_API_KEY`, or `LLM_PROVIDER=openrouter` with `OPENROUTER_API_KEY` (and optionally `OPENROUTER_MODEL`), in a root `.env` file before `docker compose up`.
- Stop with `docker compose down`. Add `-v` to also delete the database volume.
- The first PostgreSQL start is the first real run of migrations V6/V7 on Postgres. Watch the Flyway log, or run `mvn test -Dtest=PostgresMigrationTest` with Docker running.

## 5. Production build artefacts

| Part | Non-Docker build | Docker image |
|---|---|---|
| Backend | `mvn -B -DskipTests package` → `target/tripwise-ai-0.0.1-SNAPSHOT.jar`, run with `java -jar` | `Dockerfile.backend` |
| ai-service | `pip install .` then `uvicorn app.main:app --host 0.0.0.0 --port 8000` | `ai-service/Dockerfile` |
| Frontend | `npm ci; npm run build; npm run start` (port 3000) | `frontend/Dockerfile` (Next.js standalone) |

All base images (`eclipse-temurin`, `node:24-alpine`, `python:3.12-slim`, `postgres:16-alpine`, `caddy`) are multi-arch, so the same files build on x86 and ARM (Oracle Ampere, AWS Graviton).

### 5.1 Production files (repo root)

| File | Purpose |
|---|---|
| `Dockerfile.backend` | Multi-stage build: Maven + JDK 21 → JRE 21 image running as a non-root user, heap capped at 60% of container memory |
| `.dockerignore` | Keeps the backend build context small (no `target/`, `data/`, `frontend/`, `ai-service/`, `.env*`) |
| `compose.prod.yml` | PostgreSQL 16, ai-service, backend, frontend and Caddy on one VM. Only Caddy publishes ports (80/443). The backend waits for healthy Postgres and ai-service |
| `Caddyfile` | HTTPS for `$DOMAIN` (automatic Let's Encrypt) → `frontend:3000` |
| `.env.prod.example` | Every production setting with notes. Copy to `.env` |

Spring trusts `X-Forwarded-For` only from private networks (Tomcat defaults, which include Docker's), so the chain
Caddy → frontend (`TRUST_PROXY_HEADERS=true`) → Spring rate-limits by the real client IP with no extra setting.

### 5.2 Configure and start

```bash
cp .env.prod.example .env && chmod 600 .env
# fill in DOMAIN, DB_PASSWORD, JWT_SECRET, AI_SERVICE_API_KEY, LLM_PROVIDER (+ GEMINI_API_KEY for gemini, or OPENROUTER_API_KEY for openrouter)
docker compose -f compose.prod.yml config --quiet   # fails with a clear message if a required value is missing
```
Start it:

```bash
docker compose -f compose.prod.yml up -d --build
docker compose -f compose.prod.yml logs -f backend     # watch Flyway + startup
```

> **HTTPS is required.** In production the frontend sets auth cookies with `Secure`, so login won't work over
> plain `http://<ip>`. Always open the app through the HTTPS domain served by Caddy.

---

## 6. Deploy on Oracle Cloud Always Free (recommended)

**Why Oracle:** its Always Free tier includes Ampere A1 ARM capacity, up to **4 OCPUs and 24 GB RAM** in total, plus about 200 GB of block storage. That comfortably runs all four parts plus PostgreSQL and doesn't expire. A card is needed for verification.

1. **Create the VM.**
   - Go to Compute → Instances → Create. Image: Ubuntu 24.04. Shape: `VM.Standard.A1.Flex`, 2–4 OCPU and 12–24 GB.
   - Add your SSH public key and assign a public IPv4.
   - If you get "Out of host capacity", try another availability domain or retry later. Upgrading the account to Pay-As-You-Go keeps the free allowance and usually fixes this; set a budget alert if you do.
2. **Open ports 80 and 443. Both layers are required.**
   - **VCN security list** (or NSG): add ingress rules for TCP 80 and 443 from `0.0.0.0/0`.
   - **The OS firewall:** Oracle's Ubuntu images ship with iptables rules that reject new connections. Run:
     ```bash
     sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
     sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
     sudo netfilter-persistent save
     ```
3. **Install Docker:**
   ```bash
   curl -fsSL https://get.docker.com | sudo sh
   sudo usermod -aG docker $USER && newgrp docker
   ```
4. **Point a domain at the VM's public IP** (see section 9). Wait until `nslookup <domain>` returns that IP.
5. **Deploy:**
   ```bash
   git clone <your repo url> tripwise && cd tripwise
   cp .env.prod.example .env && chmod 600 .env && nano .env    # see section 5.2
   docker compose -f compose.prod.yml up -d --build
   ```
6. **Verify:**
   - Open `https://<domain>`, register and create a trip.
   - Check status with `docker compose -f compose.prod.yml ps`.
   - Spring's health endpoint is `/actuator/health`; it is reachable only inside the Docker network.
7. **Idle reclamation:** Oracle may reclaim Always Free instances that stay almost idle for about 7 days. A live app with some traffic usually avoids this; Pay-As-You-Go accounts are exempt.

## 7. AWS options

- **New accounts (since mid-2025):** the free plan gives credits, up to about USD 200, usable for up to 6 months, instead of the older 12-month t2/t3.micro offer. Treat AWS as a **trial**, not permanent free hosting.
- **Single VM (simplest):**
  - Use EC2 `t4g.small` (ARM, 2 GB) or `t3.small`, Ubuntu 24.04, and follow the same steps as Oracle from step 3.
  - Security group: allow 22 from your IP only, and 80/443 from anywhere.
  - Allocate an Elastic IP so the address doesn't change on restart. Public IPv4 addresses are billed hourly, which counts against your credits.
- **On a 1 GB instance:**
  - Add swap: `sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile`.
  - Cap the JVM with `JAVA_TOOL_OPTIONS=-Xmx384m`.
  - Expect slow builds. Build the images in CI and push them to a registry instead.
- **Managed database (optional):** RDS for PostgreSQL 16 on the smallest instance. Then set `DB_URL` to the RDS endpoint and remove the `postgres` service.
- **Lightsail:** a fixed monthly price including the IP, and simpler than EC2. Some plans have had free first months.

## 8. Azure options

- **Free account:** about USD 200 credit for 30 days, plus 12 months of selected services. These have included **750 h/month of a B1s Linux VM** (1 vCPU, 1 GB) and **750 h/month of Azure Database for PostgreSQL Flexible Server (B1ms)** with 32 GB storage.
- **Azure for Students:** about USD 100 credit with no card, if you have a school email.
- **Setup:**
  - Create a B1s Ubuntu VM with a Standard static public IP. In its NSG, allow 80 and 443 (and 22 from your IP only).
  - Follow the Oracle steps from step 3, using the same 1 GB tips as AWS (swap, `-Xmx384m`).
  - Alternatively, run PostgreSQL on Flexible Server. Use JDBC URL `jdbc:postgresql://<server>.postgres.database.azure.com:5432/tripwise_db?sslmode=require` and allow the VM in the server's firewall.
- After 12 months B1s is billed at normal rates, so move to Oracle or budget for it.

### 8.1 Microsoft for Startups: free credits for development (premium AI models + Azure)

The Microsoft for Startups programme (Founders Hub) gives eligible startups **Azure credits that can be used during
development**, for example to:
- call premium LLMs (Azure AI Foundry / Azure OpenAI) while building and testing the planner;
- run dev/test VMs and databases.

No paid subscription is needed until the credits run out. It can also host production later.

- **What you get:**
  - Azure credits in stages, larger once the startup is verified or investor-backed. Amounts have ranged from about
    USD 1k–5k up to about USD 150k.
  - Credits can be spent on VMs, PostgreSQL Flexible Server and Container Apps.
  - Credits also cover premium LLMs through Azure AI Foundry: the OpenAI GPT-4o / GPT-4.1 / GPT-5 families and o-series
    reasoning models, plus catalogue models such as Llama, Mistral, DeepSeek and others where offered in your region.
  - Extras such as GitHub Enterprise and Microsoft 365 seats, and technical advisory hours.
- **Eligibility:** a privately held, software-based startup, applied for with a LinkedIn profile and a company website or
  domain (this is a reason to own a real domain, see section 9). Personal or student projects usually don't qualify;
  use the Azure free account or Azure for Students instead.
- **Apply:** sign up at `https://www.microsoft.com/startups`, create the Azure subscription from the portal, and
  **set a budget alert** in Cost Management. Credits expire (typically within a year per stage) and paid usage starts
  silently once they run out.

> Credit amounts, included models and eligibility rules change often and are summarised from memory. Check the
> current Founders Hub terms and model availability per region before relying on them.

**Using the credits for development:**

1. In the Azure portal (startup subscription), create an **Azure AI Foundry** project.
   Deploy one model for development, e.g. a small, cheap model (`gpt-4o-mini` / `gpt-4.1-mini` class) for everyday work and a premium one for quality checks.
2. Note the **endpoint**, **API key** and **deployment name**. Keep them in `ai-service/.env` only, never in git.
3. Set a **budget alert** and a low **tokens-per-minute quota** on each deployment, so a test loop can't burn the credits.
4. Day to day, keep `LLM_PROVIDER=fake` for unit tests and UI work (free, deterministic). Switch to the Azure model only when testing real itinerary quality.
5. Optional: a small dev VM or **PostgreSQL Flexible Server** paid by the same credits, to test the PostgreSQL migrations (V6/V7) without Docker on your laptop.

Other free development options for LLMs:
- **GitHub Models** offers free, rate-limited prototyping access to several OpenAI and open models with a GitHub token.
- A **Gemini API free tier** key works with the existing `gemini` provider today.
- A **Visual Studio subscription**, if your employer provides one, includes monthly Azure dev/test credit.

**Hosting with startup credits.** Pick one:

| Option | Fit |
|---|---|
| **One VM** (e.g. `B2s`/`B2ms`, 4–8 GB) running `compose.prod.yml` | Simplest; same steps as Oracle from step 3 (section 6) |
| VM + **Azure Database for PostgreSQL Flexible Server** | Managed backups. Set `DB_URL=jdbc:postgresql://<server>.postgres.database.azure.com:5432/tripwise_db?sslmode=require` and remove the `postgres` service |
| **Azure Container Apps** (one app per service) + Flexible Server | Managed scaling and HTTPS. Keep backend and ai-service on **internal** ingress, frontend external. Run **one replica** of backend and ai-service (rate limits, caps and the async generation queue are in-memory) |

Store secrets in Azure Key Vault or Container Apps secrets, not in images.

**Using Azure AI Foundry models in TripWise: not implemented yet.**

The ai-service supports `LLM_PROVIDER=gemini`, `openrouter` (any OpenRouter model, paid from OpenRouter credits rather than Azure credits) and `fake` (`ai-service/app/llm/`). To use Azure startup credits for the LLM, an `azure_openai` provider is needed:

- A new `app/llm/azure_openai.py` implementing the existing `LlmProvider` protocol (`app/llm/base.py`). It can follow `openrouter.py`: the OpenAI-compatible API over `httpx`, so no new package is needed, and the shared prompts in `prompts.py`.
  - It would be wired in `factory.py` and keep the global `LLM_DAILY_CAP` wrapper (`caps.py`).
- New settings, for example `AZURE_OPENAI_ENDPOINT`, `AZURE_OPENAI_API_KEY` (or managed identity), `AZURE_OPENAI_DEPLOYMENT` and `AZURE_OPENAI_API_VERSION`.
- **Web price hints stay Gemini-only:** they depend on Google Search grounding. With the Azure provider, plans keep estimates, fare rules and booking links, and set `webHintsSkipped`. Azure's equivalent ("Grounding with Bing Search") is a separate paid feature with its own terms.
- All prices still come from the deterministic pricing engine, so switching the LLM changes only itinerary wording, city lookup and chat behaviour, never the cost maths.

Ask before this is built: it adds new config and needs your Azure deployment name and API version.

## 9. Free domain / subdomain options

The app needs a **server**: Next.js API routes, cookies and Spring. **GitHub Pages only hosts static files**, so it can't run TripWise. A `username.github.io` page can at most serve a landing page that links to the real app.

| Option | Cost | Example | Notes |
|---|---|---|---|
| **DuckDNS** | Free | `tripwise.duckdns.org` | Sign in with GitHub, create a subdomain and set the IP. Works with Caddy's automatic HTTPS. **Best free choice.** |
| **is-a.dev** | Free | `tripwise.is-a.dev` | Request a subdomain by GitHub pull request to `is-a-dev/register`. Manual review; meant for developer projects. Set an A record to the VM IP. |
| **sslip.io / nip.io** | Free | `1-2-3-4.sslip.io` | Zero setup; the hostname contains the IP. Good for testing. Shared domain, so you may hit Let's Encrypt rate limits. |
| **Cloudflare Quick Tunnel** | Free | `random-words.trycloudflare.com` | `cloudflared tunnel --url http://localhost:3000`. No open ports, but the URL changes on every start. Demo only. |
| **eu.org** | Free | `tripwise.eu.org` | A real free domain, but approval can take weeks. |
| **Cheap paid domain** (`.xyz`, `.site`, `.online`, …) | ~USD 1–12/yr | `tripwise.xyz` | **Best overall.** Buy at Cloudflare Registrar (at-cost renewals) or Porkbun. Cloudflare DNS also lets you use a **named Cloudflare Tunnel**: HTTPS with no inbound ports, which avoids the Oracle firewall steps entirely. |

**DuckDNS quick setup:**
1. Log in at duckdns.org and add `tripwise`.
2. Set the IP to the VM's public IP. To keep it updated, run this from cron every 5 minutes:
   ```
   curl "https://www.duckdns.org/update?domains=tripwise&token=<token>&ip="
   ```
3. Set `DOMAIN=tripwise.duckdns.org` in `.env` and start the stack. Caddy obtains the certificate.

**Recommendation:**
- Free: Oracle A1 + Docker Compose + Caddy + DuckDNS.
- About USD 10/yr: Oracle A1 + a domain on Cloudflare + Caddy, or a named Cloudflare Tunnel.

---

## 10. Operations checklist

- **Updates:** `git pull && docker compose -f compose.prod.yml up -d --build`. Flyway applies new migrations on backend start. Never edit an applied migration.
- **Backups (cron, daily):**
  ```bash
  docker compose -f compose.prod.yml exec -T postgres pg_dump -U tripwise_user tripwise_db | gzip > ~/backups/tripwise-$(date +%F).sql.gz
  ```
  Copy the backups off the VM, for example to OCI Object Storage, which has free tier storage.
- **Logs:** `docker compose -f compose.prod.yml logs --tail=200 <service>`. Secrets and tokens are never logged by design.
- **Security:**
  - SSH keys only, no password login.
  - Only 80/443 public; PostgreSQL, Spring and the ai-service unreachable from outside.
  - Rotate `JWT_SECRET` if leaked; this logs everyone out.
- **Gemini costs:**
  - Keep `GROUNDING_ENABLED=false` unless you accept grounding charges.
  - Set `LLM_DAILY_CAP`.
  - Use `LLM_PROVIDER=fake` for demos with no key.
- **Single instance only:** rate limits, caps and caches are in memory. Don't run more than one backend or ai-service replica.

## 11. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| Backend exits at start with a JWT error | `JWT_SECRET` missing, not Base64, or shorter than 32 bytes |
| `Schema-validation` error at start | Entity doesn't match the schema; check Flyway ran (`flyway_schema_history`) |
| Login seems to work but you're immediately logged out | You're on plain HTTP; production cookies are `Secure`, so use the HTTPS domain |
| Trip generation fails, or multi-city save returns 503 | ai-service down, or `AI_SERVICE_API_KEY` ≠ `INTERNAL_API_TOKEN` (ai-service log shows 401) |
| Caddy can't get a certificate | DNS doesn't point to the VM yet, or port 80/443 is blocked (cloud firewall **and** iptables on Oracle) |
| Builds killed on a 1 GB VM | Add swap, or build images elsewhere and pull them |
