# Auth Service

[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=AlexOmarov_auth-service&metric=coverage)](https://sonarcloud.io/summary/new_code?id=AlexOmarov_auth-service)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=AlexOmarov_auth-service&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=AlexOmarov_auth-service)

Ktor-сервис для PoC решений в области аутентификации/авторизации

## Содержание
- [Введение](#Введение)
- [Используемые инструменты](#Используемые-инструменты)
- [Развертка](#Развертка)
- [Использование](#Использование-code-quality-инструментов)
- [Использование развёрнутой системы](#Использование-развёрнутой-системы)
- [Публикация](#Публикация)

### Введение
Проект содержит исходный код и docker-compose файл для развертки сервиса и окружения.

Основные функции сервиса:
 - управление ЖЦ системных (не персональных) данных учётной записи клиента;
 - аутентификация клиента;
 - предоставление данных для авторизации клиента в смежных системах.

Процесс импорта проекта в IntellijIDEA не требует дополнительных настроек локальной среды.   
Проект может быть импортирован в IDE с помощью меню `File->New->Project from existing sources->Gradle`.

### Используемые инструменты
Сервис построен с использованиием следующих инструментов:
- [Ktor](https://docs.spring.io/spring-framework/reference/web/webflux.html)
- [Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- [Reactive Redis](https://developer.redis.com/develop/java/spring/rate-limiting/fixed-window/reactive/)
- [Reactive Postgres](https://github.com/pgjdbc/r2dbc-postgresql)

Полный список используемых библиотек можно найти в файле [libs.versions.toml](gradle/libs.versions.toml)

### Развертка
Наиболее удобно развернуть сервис и все стоонние системы используя Docker.
```bash
docker-compose up -d
```
Compose включает в себя следующие инструменты, каждый из которых доступен для подключения по localhost:
1. Postgres - localhost:5432
2. Redis - localhost:6379
3. Otel Collector - localhost:4317/4318/4319
4. Сам сервис - localhost:8080

#### Локальная развертка
Сервис может разворачиваться и локально, с использованием собранного исходного кода вместо docker-образа.
Для этого необходимо изначально провести развертку с использованием Docker compose файла, 
после этого удалить контейнер с сервисом (service), 
собрать проект и запустить его либо через IDE, либо внеся изменения в docker compose файл -
раскомментировать строки build/context контейнера service и убрать настройку образа.

### Использование развёрнутой системы
`Healthcheck` API доступны по `/health` пути
Также для ручного вызова доступны API сервиса по адресу `localhost:8080`.   
API использует форматы данных Cbor и Json, выбирается в зависимости от переданных заголовков.

### Использование code-quality инструментов
Когда проект собирается с использованием `build` задачи gradle detekt и ktlint проверки проходят автоматически. 
Detekt отчет формируется по путям `./build/app/reports/detekt`, `./build/api/reports/detekt`.  
Также есть возможность запускать проверки вручную командой
```bash  
.\gradlew detekt
```  

Тестирование и измерение покрытия также проходят автоматически при вызове команды `build`. 
Покрытие измеряется инструментом kover, который в свою очередь использует движок JaCoCo.  
Отчеты по покрытию (xml для sonar-инструментов и html для локальной разработки)  
формируются по путям `./build/app/reports/kover/report.xml`, `./build/app/reports/kover/html/index.html`.  
Также есть возможность вызвать тестирование и измерение покрытия вручную, вызвав команду
```bash  
.\gradlew test koverPrintCoverage
```  
Процент покрытия также можно смотреть и в самой IDE.  
Для этого достаточно правой кнопкой мыши кликнуть на папку test и запустить задачу Run with Coverage.  
**Важно!** Тесты не пройдут, если одновременно в докере развернут compose!

Прохождение Quality Gate реализовано с использованием gradle плагина sonarqube. Вызвать прохождение можно командами:
```bash  
.\gradlew build
.\gradlew sonar -D"sonar.host.url"="<SONAR_HOST>" -D"sonar.token"="YOUR_TOKEN" -D"sonar.projectKey"="KEY" -D"sonar.organization"="ORG"
```  

При вызове sonar с помощью gradle задачи сгенерированный detekt отчет и kover отчет будут добавлен к анализу.

### Публикация
Для тестирования будет полезна возможность локальной публикации пакетов в mavenLocal.  
Для этого используется команда publishToMavenLocal.

```bash  
.\gradlew publishToMavenLocal
```  
В результате выполнения команды в .m2 папке пользователя появится артефакт auth-service-api, который будет содержать все  
необходимые dto-классы, proto-файлы и другие нужные для взаимодействия с сервисом интерфейсы.  