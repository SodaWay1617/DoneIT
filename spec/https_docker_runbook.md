# HTTPS на VPS: Nginx в Docker

Локальный Compose не меняется. Сервер использует дополнительно
`docker-compose.https.yml`; конфиги домена, сертификаты и MCP YAML находятся
вне репозитория. Нужен Docker Compose >= 2.24.4 (поддержка `!reset`).
При обновлении используется уже установленный образ приложения. На новом VPS
образ нужно один раз доставить или собрать, как описано ниже.
Команды ниже выполнять из каталога проекта на VPS, сохраняя прежнее имя
Compose-проекта и существующий `.env` с реквизитами БД. Не выполнять `down -v`.

## 0. Новый VPS: сразу HTTPS, без публичного приложения на 8080

Потребуются Ubuntu/Debian, пользователь deploy с sudo, Git, Docker Engine с
Compose >= 2.24.4, Certbot, SSH-доступ и управление DNS. Установку Docker
выполнить по официальной инструкции для своей ОС; проверить `docker info`.
Для пакетной установки Certbot: `sudo apt-get update`, затем
`sudo apt-get install --no-install-recommends certbot`. Nginx на хосте не нужен.
Проверить `df -h /`: оставить место под образы, БД, логи и резервные копии.

1. Клонировать репозиторий в `/home/deploy/DoneIT` и перейти в него. URL взять
   из своего Git remote. При восстановлении сохранить прежнее имя Compose-проекта,
   чтобы не создать новый пустой том PostgreSQL вместо существующего.
2. Настроить A-запись домена на публичный IPv4 VPS, дождаться ответа публичных
   DNS. AAAA добавлять только при настроенном IPv6. Порты 80/443 должны быть
   доступны у провайдера; 8080/5432 не открывать. Перед включением UFW сначала
   разрешить фактический SSH-порт (для стандартного: `sudo ufw allow OpenSSH`),
   затем 80/443. Сохранить текущую SSH-сессию до проверки нового подключения.
3. Создать серверный `.env` через `umask 077; touch .env; chmod 600 .env` и
   заполнить редактором. Этот блок — образец, значения паролей заменить:

   ```dotenv
   POSTGRES_DB=doneit
   POSTGRES_USER=doneit
   POSTGRES_PASSWORD=REPLACE_WITH_STRONG_PASSWORD
   SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/doneit
   SPRING_DATASOURCE_USERNAME=doneit
   SPRING_DATASOURCE_PASSWORD=REPLACE_WITH_SAME_PASSWORD
   DONEIT_MCP_CONFIG_FILE=/home/deploy/doneit-secrets/mcp.yml
   DONEIT_NGINX_CONFIG_DIR=/home/deploy/doneit-secrets/nginx
   DONEIT_ACME_WEBROOT=/home/deploy/doneit-secrets/acme
   DONEIT_LETSENCRYPT_DIR=/etc/letsencrypt
   ```

   Пароли PostgreSQL и Spring должны совпадать. Переменные POSTGRES_* задают
   учётные данные только при инициализации пустого тома: изменение `.env`
   не меняет пароль уже существующей БД.
4. Выполнить подготовку каталогов и функции `dc` из раздела 1. Серверный MCP
   YAML создать до `dc config -q` по разделу «MCP-клиент» ниже. Для первого
   запуска без MCP убрать MCP override из функции — можно включить его позже.
5. Доставить готовый образ приложения с доверенной машины с подходящей
   архитектурой, либо один раз выполнить `dc build app`. Скачивание PostgreSQL
   и Nginx при первом развёртывании необходимо; повторные сборки ради конфигов
   не нужны. Затем `dc up -d --no-build postgres` и проверить healthy в `dc ps`.
   При восстановлении загрузить проверенный backup в БД до запуска приложения;
   не восстанавливать поверх рабочих данных. На пустой БД приложение применит
   Liquibase, но получение первого пользовательского аккаунта нужно подготовить
   отдельно: окно самостоятельной регистрации в эту инструкцию не входит.
6. Запустить только bootstrap Nginx по разделу 1, получить сертификат по разделу 2.
   Приложение пока не запускать через базовый Compose. При переключении в разделе 3
   `dc up -d --no-build --no-deps app` запустит его сразу без публикации 8080.
7. Настроить hook продления и пройти приёмку ниже. До её завершения не считать
   новое развёртывание готовым.

Команды `export` и функция `dc` живут только в текущем Bash-сеансе. После нового
SSH-входа перейти в каталог проекта и повторить их из раздела 1. Постоянные
параметры Compose читает из `.env`; не выполнять `source .env` с паролями.
При обновлениях не запускать просто `docker compose up`: всегда использовать
тот же комплект файлов через `dc`, иначе вернётся публикация 8080.

## 1. Подготовка без ожидания DNS

```bash
docker compose version
mkdir -p /home/deploy/doneit-secrets/nginx /home/deploy/doneit-secrets/acme
chmod 700 /home/deploy/doneit-secrets
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

Если `DONEIT_MCP_CONFIG_FILE` раньше задавался только через `export`, сохранить
его абсолютный путь в `.env`. Убедиться, что файл существует, не печатая секреты.
Для уже подключённого MCP путь можно найти командой:

```bash
docker inspect doneit-app --format '{{range .Mounts}}{{println .Source "->" .Destination}}{{end}}'
```

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
  -d "$DONEIT_DOMAIN" --email sob.dan02b@gmail.com --agree-tos --non-interactive --no-eff-email --dry-run
```

После успешного теста повторить без `--dry-run`. Проверить имя сертификата:
`sudo certbot certificates`. Если Certbot назначил имя с суффиксом (например,
`-0001`), скорректировать пути сертификата в следующем серверном конфиге.
Для другого владельца заменить email. `--no-eff-email` отключает передачу
email для рассылки EFF; для согласия использовать `--eff-email` вместо него.
`--non-interactive` также обходит наблюдавшийся UnicodeDecodeError при вводе
ответа на вопрос EFF. Ошибки запроса читать в `/var/log/letsencrypt/letsencrypt.log`.
Сообщение entrypoint Nginx о невозможности изменить read-only default.conf
ожидаемо; критерием проверки служит успешный `nginx -t` (именно с `-t`).

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

## MCP-клиент и выпуск токена

После обновления с миграцией 0032 основной способ — **Настройки → MCP-токены**:
выпуск на 1–21 день, однократное копирование секрета и отзыв без перезапуска.
Пошаговая настройка окружения и перенос прежних YAML-токенов описаны в
`mcp_mvp_runbook.md`. Ниже — прежний CLI-генератор для первоначального bootstrap.
На новом сервере можно сразу задать `tokens: []` вместе с enabled=true и
allowed-hosts/origins, затем войти через браузер и выпустить токен в настройках.

На Windows-ноутбуке из каталога репозитория (логин заменить своим):

```powershell
.\scripts\new-mcp-token.ps1 -Username 'YOUR_DONEIT_LOGIN' -ClientId 'laptop-https-02' -Hours 12 -Hosts 'done-it-all.ru' -AllowWrite
```

Использовать новый ClientId при каждом выпуске; существующие файлы скрипт
не перезаписывает. `-AllowWrite` нужен только для разрешённых изменений;
без него токен только для чтения. Срок MVP — максимум 12 часов, затем повторный
выпуск. На VPS перенести только `.mcp-local/laptop-https-02.yml` в путь
DONEIT_MCP_CONFIG_FILE, ограничить права `chmod 600` и добавить allowed-origins
из раздела 3 при необходимости. `.token` оставить на ноутбуке. Имя пользователя
должно соответствовать существующему аккаунту, доступ определяется его проектами.

При замене всего YAML прежние токены перестанут работать после пересоздания app.
Если нужны несколько клиентов, объединить записи `tokens` осознанно, сохранив
общие allowed-hosts/origins; не перетирать конфигурацию других клиентов случайно.

В PowerShell настроить Codex CLI:

```powershell
$env:DONEIT_MCP_TOKEN = (Get-Content -Raw '.\.mcp-local\laptop-https-02.token').Trim()
[Environment]::SetEnvironmentVariable('DONEIT_MCP_TOKEN', $env:DONEIT_MCP_TOKEN, 'User')
codex mcp add doneit --url https://done-it-all.ru/mcp --bearer-token-env-var DONEIT_MCP_TOKEN
codex mcp list
```

Запустить `codex resume` из этого терминала. VS Code полностью перезапустить,
чтобы он получил новую пользовательскую переменную. Проверить MCP сначала
только на чтение: list_projects, затем list_tasks. Не выводить токен в терминал.

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

Строка Certbot `Hook 'deploy-hook' ran with error output` сама по себе не
означает ошибку: Nginx пишет диагностические сообщения в stderr. В проверенном
запуске следом были `test is successful`, `signal process started` и итог
`all simulated renewals succeeded`. При реальной ошибке hook её устранить,
даже если сам сертификат успешно продлён.

## 5. Приёмка и проверка портов

На VPS:

```bash
curl -I "https://$DONEIT_DOMAIN/login"
curl -I "http://$DONEIT_DOMAIN/login"
sudo ss -ltnp '( sport = :8080 or sport = :5432 )'
docker port doneit-app
docker port doneit-postgres
sudo ufw status
```

Ожидается HTTPS 200, HTTP 308 с HTTPS Location; JSESSIONID имеет Secure,
HttpOnly, SameSite=Lax. `ss` содержит только заголовок, оба `docker port` пусты.
В UFW разрешены SSH и 80/443. `8080/tcp` и `5432/tcp` в `dc ps` без отображения
адреса хоста — внутренние порты, они не означают публикацию наружу.

На ноутбуке без VPN/TUN (Happ/Xray может локально принять TCP и дать ложный True):

```powershell
Test-NetConnection 168.113.157.77 -Port 443
Test-NetConnection 168.113.157.77 -Port 8080
Test-NetConnection 168.113.157.77 -Port 5432
```

При другом VPS заменить IP. Проверить InterfaceAlias: Wi-Fi/Ethernet, не
happ-xray. Ожидается TcpTestSucceeded=True для 443 и False для 8080/5432.
DNS не участвует в проверке по IP. При сомнении повторить из другой внешней сети.

В браузере проверить вход/выход, remember-me после повторного открытия,
формы с CSRF, создание/редактирование задач, отсутствие HTTP-предупреждения.
На iPhone проверить HTTPS-ярлык/PWA, навигацию, прокрутку и клавиатуру.
Через MCP проверить чтение; запись и негативные сценарии — по отдельному
плану задачи DONEIT-10, не менять реальные задачи только ради проверки.

### Фактический результат 31.10.2026

- VPS done-it-all.ru / 168.113.157.77: HTTPS /login 200, HTTP 308, Secure /
  HttpOnly / SameSite=Lax у JSESSIONID подтверждены выводом curl.
- Certbot выпустил сертификат до 29.01.2027; dry-run продления с deploy hook
  успешен, Nginx перечитал конфигурацию. Дату не использовать как постоянную:
  актуальную проверять через `sudo certbot certificates`.
- MCP с новым токеном подключён, list_projects и list_tasks успешно выполнены.
- Пользователь подтвердил мобильную версию и remember-me после открытия.
- На VPS отсутствуют слушатели и публикация 8080/5432; UFW разрешает SSH,80,443.
- Внешний 8080 без Happ/Xray: TcpTestSucceeded=False. Недоступность внешнего
  5432 без VPN также подтверждена пользователем.
- Дополнительная приёмка MCP выполнена 31.10.2026 по HTTPS:
  - Через штатный MCP создана DONEIT-11, прочитана, изменены описание и оценка,
    изменения перечитаны, задача закрыта в CLOSED (не удалена) и перечитана снова.
    Это отдельная тестовая запись; рабочие задачи не изменялись.
  - Прямой HTTPS initialize — 200; notifications/initialized — 202; tools/list
    с Mcp-Session-Id и MCP-Protocol-Version — 200, Content-Type text/event-stream,
    получено SSE-событие data. Authorization прошёл через Nginx.
  - Без токена и с заведомо неверным Bearer — 401; недопустимый Origin — 403
    как без токена, так и с действующим; неизвестный Mcp-Session-Id — 404.
    Недопустимый Host приводит к закрытию соединения Nginx (444).
  - Тестовая транспортная MCP-сессия завершена через DELETE — 200; сессия
    рабочего Codex не затронута. TLS проверялся штатным trust store, без `-k`.
  - Пользователь также подтвердил браузер и настоящий iPhone; remember-me
    после повторного открытия проверен ранее.
- Границы проверки: нет второго тестового аккаунта/токена для проверки сессии
  другого пользователя и недоступных ему проектов; неизвестная сессия проверена,
  но это не заменяет межпользовательский тест. Старый HTTP-токен не предъявлялся
  повторно: ранее он истёк и серверный YAML заменён новым. Проверка неверного
  токена не выдаётся за отдельную проверку отзыва именно старого секрета.

## 6. Обновление и восстановление

После git pull восстановить функцию `dc`, выполнить `dc config -q`.
Для изменения только MCP YAML: `dc up -d --no-build --no-deps --force-recreate app`
(Compose не обнаруживает изменения содержимого bind-mounted файла сам).
Для изменений Nginx: `dc exec nginx nginx -t`, затем
`dc exec nginx nginx -s reload`. Если изменён код приложения, доставить новый
образ либо собрать один раз и пересоздать app; БД не пересоздавать.

Для восстановления на другом VPS нужны репозиторий/образ, проверенная копия БД,
серверный `.env` и доступ к DNS. Конфиги и сертификаты не входят в backup БД.
Если сертификатов нет — выпустить заново после перенастройки DNS; MCP-токены
перевыпустить. Секретные файлы восстанавливать из отдельно защищённой копии.
Нельзя считать Git достаточным backup данных и настроек.

Контролировать `df -h /` и `docker system df` перед обновлениями. Не собирать
образы ради конфигурации. Неиспользуемый build cache чистить отдельно через
`docker builder prune -a`, когда сборок нет; рабочие тома не удалять.

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
