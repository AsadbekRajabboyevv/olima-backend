# OLIMA AI — backend

Spring Boot 3 / Java 21 API va pgvector Postgres. Frontend alohida repoda: `olima-frontend`.

## Lokal ishga tushirish

```bash
cp .env.example .env    # qiymatlarni to'ldiring
docker compose up -d --build
```

Yoki IDE'dan `SPRING_PROFILES_ACTIVE=local` bilan.

## Serverda deploy

Repo `/opt/olima/backend` ga clone qilingan, `.env` o'sha papkada.

```bash
cd /opt/olima/backend
git pull
docker compose up -d --build
docker compose logs -f olima-api
```

Konteynerlar `olima_net` tarmog'ida; tashqariga port chiqmaydi — frontend (`olima-web`)
shu tarmoq orqali `olima-api:8080` ga proxy qiladi.

## Baza zaxirasi

```bash
docker exec olima-postgres pg_dump -U olima -d olima -Fc > olima-$(date +%F).dump
```
