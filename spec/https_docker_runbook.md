# HTTPS на VPS: Nginx в Docker

Локальный Compose не меняется. Сервер использует дополнительно
`docker-compose.https.yml`; конфиги домена, сертификаты и MCP YAML находятся
вне репозитория. Нужен Docker Compose >= 2.24.4 (поддержка `!reset`).
Новые образы приложения не собираются: используется уже установленный образ.
Команды ниже выполнять из каталога проекта на VPS, сохраняя прежнее имя
Compose-проекта и существующий `.env` с реквизитами БД. Не выполнять `down -v`.

## 1. Подготовка без ожидания DNS

```bash
docker compose version
mkdir -p /home/deploy/doneit-secrets/nginx /home/deploy/doneit-secrets/acme
sudo mkdir -p /etc/letsencrypt
```

Добавить в существующий серверный `.env` (не коммитить):

```dotenv
DONEIT_NGINX_CONFIG_DIR=/home/deploy/doneit-secrets/nginx
DONEIT_ACME_WEBROOT=/home/deploy/doneit-secrets/acme
DONEIT_LETSENCRYPT_DIR=/etc/letsencrypt
# Сохранить существующий DONEIT_MCP_CONFIG_FILE и реквизиты БД.
```

Настроить домен и сокращение команды в текущем Bash-сеансе:

```bash
export DONEIT_DOMAIN=done-it-all.ru
export DONEIT_NGINX_CONFIG_DIR=/home/deploy/doneit-secrets/nginx
dc() { docker compose -f docker-compose.yml -f docker-compose.mcp.yml -f docker-compose.https.yml "$@"; }
sed "s/DOMAIN_NAME/$DONEIT_DOMAIN/g" deploy/nginx/bootstrap.conf.example > "$DONEIT_NGINX_CONFIG_DIR/default.conf"
```

Переменные из `.env` читает Compose, но не Bash, поэтому путь для `sed`
также экспортируется выше.

Если MCP не используется, убрать `-f docker-compose.mcp.yml` из функции.
Не выводить полный `compose config`: он может содержать пароли; проверять `-q`.

```bash
dc config -q
dc pull nginx
dc run --rm --no-deps nginx nginx -t
dc up -d --no-build --no-deps nginx
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
```

`--no-deps nginx` принципиален: приложение пока не пересоздаётся и остаётся
доступным по старому HTTP :8080. На 80 пока только ACME, остальные запросы — 503;
443 ещё не обслуживает TLS. Разрешить 80/443 также в firewall провайдера.

## 2. Сертификат после появления публичного DNS

Проверить A-запись через 1.1.1.1 и 8.8.8.8. Она должна указывать на VPS.
При наличии AAAA проверить работоспособность IPv6; текущие примеры рассчитаны
на IPv4. Certbot уже установлен на VPS; используем webroot, а не standalone.

```bash
sudo certbot certonly --webroot -w /home/deploy/doneit-secrets/acme \
  -d "$DONEIT_DOMAIN" --email sob.dan02b@gmail.com --agree-tos --dry-run
```

После успешного теста повторить без `--dry-run`. Проверить имя сертификата:
`sudo certbot certificates`. Если Certbot назначил имя с суффиксом (например,
`-0001`), скорректировать пути сертификата в следующем серверном конфиге.

## 3. Включение HTTPS

Перед переключением дополнить существующий серверный MCP YAML, сохранив
остальные параметры и токены:

```yaml
doneit:
  mcp:
    allowed-hosts:
      - done-it-all.ru
    allowed-origins:
      - https://done-it-all.ru
```

Host задаётся без схемы и порта, Origin — со схемой. Небраузерный MCP-клиент
может не отправлять Origin. Не добавлять `*`. Изменения загрузятся при
пересоздании app ниже. Authorization, Accept, MCP-Protocol-Version,
Mcp-Session-Id и Last-Event-ID передаются Nginx без удаления; буферизация выключена.

```bash
sed "s/DOMAIN_NAME/$DONEIT_DOMAIN/g" deploy/nginx/https.conf.example > "$DONEIT_NGINX_CONFIG_DIR/default.conf"
dc exec nginx nginx -t
dc up -d --no-build --no-deps app
dc exec nginx nginx -s reload
dc ps
curl -I "https://$DONEIT_DOMAIN/login"
curl -I "http://$DONEIT_DOMAIN/login"
```

При ошибке `nginx -t` не продолжать: вернуть bootstrap-конфиг и исправить пути.
Пересоздание app включает обработку forwarded headers, Secure session cookie
и удаляет публикацию 8080. Заголовки proxy перезаписываются, включая Forwarded;
не подключать недоверенные контейнеры к сети приложения. Nginx использует Docker
DNS, поэтому смена IP app после пересоздания не требует перезапуска proxy.

Проверить вход/выход, создание/редактирование задачи, Secure cookie через браузер,
RU/EN, iPhone PWA. HTTPS — другой origin: потребуется повторный вход и, возможно,
переустановка PWA. MCP-клиенту задать `https://done-it-all.ru/mcp`, сохранив Bearer;
проверить initialize/list tools и чтение. Без Bearer ожидать отказ, а не HTML-вход.
Тестирование записи — только на согласованной тестовой задаче.

Проверить в DevTools: JSESSIONID и remember-me имеют Secure, HttpOnly,
SameSite=Lax. Изменяющий запрос браузерного UI без CSRF должен отклоняться;
не отключать CSRF ради proxy. Location не должен содержать HTTP или :8080.
Предупреждение HTTP на странице устройств скрывается по `request.isSecure()`:
после обработки proxy headers отдельное отключение не нужно.
Проверить установку PWA именно с нового HTTPS origin. В текущем репозитории
manifest/start_url и service worker не найдены; если PWA настроена внешним
способом, обновить стартовый адрес там и проверить на устройстве.

Выпустить новый MCP-токен по `spec/mcp_mvp_runbook.md`: на VPS хранить только
хеш с ограниченным сроком, сырой секрет — в окружении клиента. После успешного
подключения по HTTPS удалить старый HTTP-токен из YAML, пересоздать только app
и проверить: старый получает 401, новый работает. Секреты не писать в команды
curl, Git или переписку. При перезапуске MCP-сессии сбрасываются, клиент должен
заново выполнить initialize.

```bash
sudo ss -ltnp '( sport = :8080 )'
sudo ufw delete allow 8080/tcp
```

Проверить с ноутбука, что внешний :8080 недоступен. UFW сам по себе не гарантирует
закрытие опубликованных Docker-портов; именно поэтому Compose удаляет ports app.
Дальнейшие обновления выполнять с тем же набором `-f`: базовый Compose вернёт :8080.
Проверить также недоступность внешнего :5432: у postgres публикации порта нет.
Не добавлять `ports` для БД в серверный override.

## 4. Автоматическое продление

```bash
sudo install -m 755 scripts/reload-nginx-cert.sh /etc/letsencrypt/renewal-hooks/deploy/doneit-nginx
sudo systemctl enable --now certbot.timer
sudo certbot renew --dry-run --run-deploy-hooks
systemctl list-timers certbot.timer
```

Hook запускается от root и требует доступного `docker` в PATH. Он проверяет
конфигурацию и перечитывает сертификаты без остановки Nginx. Весь каталог
`/etc/letsencrypt` примонтирован read-only, включая цели symlink live → archive.
При установке Certbot через snap проверить его штатный таймер вместо certbot.timer.

## Откат

Вернуть предыдущую команду Compose (базовый + MCP) и пересоздать только app
через `up -d --no-build --no-deps app`; это вернёт HTTP :8080 и прежние cookies.
Если правило UFW удалено, временно восстановить его. Nginx можно остановить
через `dc stop nginx`. БД и тома не удалять. HTTP использовать только временно.

## Проверки и источники

На сервере обязательны `dc config -q`, `nginx -t`, тест renewal и проверки
приложения/MCP: локальная статическая проверка не заменяет их.
[Compose merge](https://docs.docker.com/reference/compose-file/merge/),
[Nginx proxy](https://nginx.org/en/docs/http/ngx_http_proxy_module.html),
[Certbot webroot и renewal](https://eff-certbot.readthedocs.io/en/stable/using.html).
