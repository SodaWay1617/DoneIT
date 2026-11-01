# PostgreSQL: ежедневные резервные копии на VPS

Задача DONEIT-5. Полный логический дамп одной БД DoneIt через `pg_dump -Fc`
из существующего PostgreSQL 17. Работа приложения не останавливается; volume
не копируется. Новых контейнеров, Docker-сборок, облачных ключей и публикации БД нет.
На хосте нужен Python 3.10+ со стандартной библиотекой и tzdata, Docker CLI и cron.
Скрипт предназначен для Linux, не для PowerShell.

## 1. Установка на существующий VPS

Войти как `deploy` (не root), перейти в `/home/deploy/DoneIT`, выполнить git pull.
Сборка/redeploy приложения ради backup не нужны. Проверить:

```bash
python3 --version
python3 -c 'from zoneinfo import ZoneInfo; print(ZoneInfo("Europe/Moscow"))'
docker exec doneit-postgres pg_dump --version
df -h /
```

Если зависимостей нет, установить пакетами ОС `python3 tzdata cron`, затем
`sudo systemctl enable --now cron`. Уже установленный cron не переустанавливать.
Пользователь deploy должен уже иметь доступ к Docker, как при redeploy.

```bash
mkdir -p /home/deploy/doneit-backups
chmod 700 /home/deploy/doneit-backups
python3 scripts/backup-postgres.py
```

По умолчанию каталог `~/doneit-backups`, контейнер `doneit-postgres`, база/роль
из POSTGRES_DB/POSTGRES_USER контейнера. Пароль используется только внутри
контейнера из имеющегося окружения; на хост не копируется и в лог не попадает.
Скрипт не читает `.env` и не выводит `docker inspect`.
Другие имена можно задать через `--container`, `--database`, `--user`.
При изменённой локальной аутентификации PostgreSQL сначала проверить ручной запуск.

Результат — JSON-строка `result: OK`, размер и путь. Exit code 0 — успех,
1 — ошибка, 75 — уже идёт другой ручной запуск. При ошибке не считать новую
копию успешной, смотреть `tail -n 10 ~/doneit-backups/backup.log`.
Вне времени запуска `--scheduled` тихо возвращает 0 без обращения к Docker.

## 2. Как создаётся копия и сохраняется место

- Каталог 700, новые файлы, журнал и lock — 600. Нельзя указывать каталог внутри
  репозитория, web-root `/var/www` или Docker storage. Каталог не публикуется Nginx.
- Имя: `doneit_2026-11-01T030000+0300_Europe-Moscow_<uuid>.dump`, рядом
  `<имя>.dump.sha256`. UUID предотвращает коллизии ручных запусков.
- Сначала приватный `.partial`, exit code pg_dump, ненулевой размер,
  `pg_restore --list`, SHA-256. Запись синхронизируется на диск, checksum
  публикуется первой, `.dump` — последним атомарным переименованием.
  Это атомарность публикации файла, не транзакция между несколькими файлами.
- На весь запуск действует файловая блокировка. Параллельный запуск не создаёт
  второй дамп. При обычной ошибке временные файлы удаляются; SIGKILL/потеря
  питания могут оставить `.partial` или orphan checksum — это не готовая копия.
- Проверка места: не менее **2 × pg_database_size + 512 MiB резерва** до начала,
  резерв проверяется и после записи. Это консервативная оценка, не квота против
  роста других файлов на сервере. При нехватке старые копии не удаляются.
- После успеха остаются последние **14 разных дней**, по одной проверенной
  копии на день. Ручной повтор за сегодня заменяет сегодняшнюю копию только
  после проверки новой и не вытесняет вчерашнюю. Невалидные/unpaired файлы и
  чужие файлы автоматически не удаляются: их нужно отдельно исследовать.
- Журнал `backup.log` и один предыдущий `backup.log.1`: около 1 MiB каждый.
  Пишутся только время, результат, размер, путь и контролируемые причины ошибок.
  Вывод PostgreSQL намеренно не копируется в журнал: он может содержать данные.
- Проверка TOC и checksum выявляет часть повреждений; полное восстановление
  обязательно до признания копии рабочей. SHA-256 не защищает от злоумышленника,
  который может заменить и дамп, и checksum на скомпрометированном VPS.

`--keep-days 14` и `--min-free-mib 512` — текущие значения по умолчанию.
Уменьшение этих настроек — отдельное решение владельца. Планировать место
для 14 копий, новой временной копии, рабочей БД и резерва; контролировать `df -h /`.

## 3. Расписание 03:00 Europe/Moscow

Открыть **пользовательский** crontab, не `sudo crontab`:

```bash
crontab -e
```

Добавить одну строку из `scripts/doneit-backup.cron.example`, сохранив другие задания:

```cron
* * * * * /usr/bin/python3 /home/deploy/DoneIT/scripts/backup-postgres.py --scheduled >/dev/null 2>&1
```

Cron запускает короткую проверку времени каждую минуту; настоящий дамп создаётся
только в 03:00 по Europe/Moscow, независимо от timezone хоста и поддержки CRON_TZ.
Не менять часовой пояс всего VPS ради этого задания. Успешный scheduled-запуск
отмечается приватным `.last-scheduled-success`, повтор в ту же минуту не дублирует
его. Ручная копия до 03:00 не отменяет плановую.

```bash
crontab -l
systemctl is-active cron
timedatectl status
```

Проверить дату нового файла и `result: OK` после ближайших 03:00. Нужны
корректные системные часы. Если VPS в этот момент выключен, автоматического
восстановления пропущенного запуска нет: после включения выполнить ручной backup.
Расписание и PATH требуют проверки на реальном VPS; локальная симуляция времени
не заменяет наблюдение первого cron-запуска. Статус cron не доказывает успех pg_dump.

## 4. Проверка и еженедельное скачивание

На VPS, указав конкретное имя из успешного журнала:

```bash
cd /home/deploy/doneit-backups
sha256sum -c -- 'ИМЯ.dump.sha256'
docker exec -i doneit-postgres pg_restore --list < 'ИМЯ.dump' >/dev/null
stat -c '%a %n' . 'ИМЯ.dump' 'ИМЯ.dump.sha256'
tail -n 5 backup.log
```

Проверить exit codes; права должны быть 700/600/600. Не выбирать `.partial`.
Раз в неделю на **ноутбуке**, без VPN/TUN при проблемах с SSH:

```powershell
$destination = Join-Path $env:USERPROFILE 'DoneIt-backups'
New-Item -ItemType Directory -Force -Path $destination | Out-Null
scp 'deploy@done-it-all.ru:doneit-backups/ИМЯ.dump' $destination
scp 'deploy@done-it-all.ru:doneit-backups/ИМЯ.dump.sha256' $destination
$backup = Join-Path $destination 'ИМЯ.dump'
$expected = ((Get-Content -LiteralPath "$backup.sha256" -Raw).Trim() -split '\s+')[0]
if ((Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash -ne $expected) { throw 'Checksum mismatch' }
```

Заменить оба `ИМЯ` на одинаковое конкретное имя. Не передавать бинарный дамп
через PowerShell Get-Content/текстовый pipeline; scp сохраняет байты.
Скачивание инициирует ноутбук, сервер не хранит его ключей или адреса для загрузки.
Дампы содержат персональные данные, хеши паролей, MCP/remember-me авторизацию:
хранить в приватном каталоге устройства, не Git и не общей папке. В репозитории
добавлена страховочная ignore-маска для `.dump` и `.dump.sha256`.

## 5. Обязательное пробное восстановление

Предпочтительно восстановить скачанную копию локально в PostgreSQL 17.
На VPS допустима отдельная временная БД внутри существующего контейнера,
если хватает места и ресурсов. Не подключать к ней публичное приложение/MCP.
**Не применять `--clean`, `--create` или dropdb к рабочей doneit.**

Пример для Bash, с уникальным именем тестовой БД:

```bash
(
    set -euo pipefail
    dump=/home/deploy/doneit-backups/ИМЯ.dump
    restore_db="doneit_backup_restore_$(date +%Y%m%d%H%M%S)_test"
    cd "$(dirname "$dump")"
    sha256sum -c -- "$(basename "$dump").sha256"
    docker exec doneit-postgres sh -eu -c '
        case "$1" in doneit_backup_restore_*_test) ;; *) exit 64;; esac
        [ "$1" != "$POSTGRES_DB" ]
        exec createdb -U "$POSTGRES_USER" -T template0 "$1"
    ' restore-check "$restore_db"
    docker exec -i doneit-postgres sh -eu -c '
        export PGPASSWORD=${POSTGRES_PASSWORD:-}
        exec pg_restore --exit-on-error --single-transaction --no-owner --no-privileges \
            -U "$POSTGRES_USER" -d "$1"
    ' restore-check "$restore_db" < "$dump"
    docker exec doneit-postgres sh -eu -c '
        export PGPASSWORD=${POSTGRES_PASSWORD:-}
        exec psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$1" -c "
            SELECT (SELECT COUNT(*) FROM users) AS users,
                   (SELECT COUNT(*) FROM tasks) AS tasks,
                   (SELECT COUNT(*) FROM task_time_entries) AS time_entries,
                   (SELECT COUNT(*) FROM task_change_history) AS change_history,
                   (SELECT COUNT(*) FROM task_planning_history) AS planning_history;"
    ' restore-check "$restore_db"
    printf 'Restored test database: %s\n' "$restore_db"
)
```

Сопоставить пользователей, задачи, списания и историю со снимком исходной БД.
Одного exit code недостаточно. После проверки удалить **только созданную**
тестовую БД, предварительно сверив её имя:

```bash
docker exec doneit-postgres sh -eu -c '
    case "$1" in doneit_backup_restore_*_test) ;; *) exit 64;; esac
    [ "$1" != "$POSTGRES_DB" ]
    exec dropdb -U "$POSTGRES_USER" "$1"
' restore-check 'ТОЧНОЕ_ИМЯ_ТЕСТОВОЙ_БД'
```

Полное учение «потерян VPS → развёртывание локально» — отдельная DONEIT-6.

## 6. Что требуется отдельно от дампа

- Репозиторий/версия образа DoneIt, PostgreSQL 17, Liquibase и параметры локали.
- Имя БД и роль; глобальные роли/пароли PostgreSQL дамп одной БД не создаёт.
- Серверный `.env`, общий MCP YAML, конфиги Nginx, DNS и доступ к выпуску
  сертификата. Хранить секретные копии отдельно и приватно, не коммитить.
- Состояние mcp_tokens и persistent_logins входит в дамп: восстановление старой
  копии может вернуть ранее отозванные токены/входы. При аварии проверить и отозвать
  их по отдельной процедуре, особенно после компрометации.

Локальная копия погибнет вместе с VPS и доступна скомпрометированному серверу.
Ручная внешняя копия обязательна; возможная потеря данных отсчитывается от
последнего скачанного снимка. Без внешней копии нет защиты от потери VPS.
Автовыгрузка, PITR/WAL, шифрование архивов и UI backup не входят в этот MVP.

## 7. Проверки реализации

```bash
python3 scripts/test_backup_postgres.py
```

Linux-тесты не вызывают Docker и не меняют БД. Проверяют ошибки, нехватку места,
пустой/нечитаемый дамп, незавершённую публикацию, права, lock, ротацию, журнал
и scheduled-ветку с контролируемым московским временем.

01.11.2026 реальный локальный pg_dump из существующего PostgreSQL 17 восстановлен
в новую уникальную `_test` БД: совпали количества и отпечатки данных всех 24 таблиц,
включая 16 пользователей, 280 задач, 24 списания времени, 55 записей истории
изменений, 358 записей истории планирования, 15 MCP-токенов и 29 записей аудита.
Проверочная БД и временный дамп удалены, исходная локальная БД не изменялась.
Эти числа относятся к локальным fixtures, не к данным VPS. Первый production
dump и запуск cron ещё нужно проверить после установки по этой инструкции.

Источники: [pg_dump 17](https://www.postgresql.org/docs/17/app-pgdump.html),
[pg_restore 17](https://www.postgresql.org/docs/17/app-pgrestore.html),
[Ubuntu cron и timezone](https://manpages.ubuntu.com/manpages/jammy/man5/crontab.5.html).
