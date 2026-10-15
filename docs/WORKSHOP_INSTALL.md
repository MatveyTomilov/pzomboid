# Установка PZ BlackBox

## Кандидатный пакет

Нужны Project Zomboid **42.21**, Java **25** (штатная JVM игры), установленный агент **ZombieBuddy 2.3.4**. Другие моды не требуются. Этот пакет не обновляет и не устанавливает ZombieBuddy.

1. Полностью закрыть игру.
2. Из ZIP взять `Contents/mods/PZBlackBox` и положить как `%USERPROFILE%\Zomboid\mods\PZBlackBox`. Не создавать двойную вложенность `PZBlackBox/PZBlackBox`.
3. Включить **PZ BlackBox** и **ZombieBuddy** для отдельной тестовой игры.
4. При обычном запросе ZombieBuddy одобрить конкретный JAR BlackBox. Не выбирать глобальный allow-all и не менять политику других модов.
5. Запустить тестовую игру. Запись начинается сама, режим BALANCED.

Дистрибутив:

```text
Contents/mods/PZBlackBox/
  common/mod.info
  42/mod.info
  42/media/java/PZBlackBox.jar
  42/media/lua/shared/PZBlackBox.lua
```

`javaJarFile=media/java/PZBlackBox.jar` — общий путь, а не принудительно client/server. В каждой JVM JAR должен быть действительно загружен агентом. Штатный Coop Host не наследует клиентский `-javaagent`; BlackBox не исправляет это автоматической правкой launcher.

## Управление

Без назначения новых клавиш и без зависимости от Mod Options. Из штатной Lua-консоли:

```lua
PZBlackBox.status()
PZBlackBox.markProblem()
PZBlackBox.exportSession()
PZBlackBox.setMode("LIGHT")
PZBlackBox.setMode("BALANCED")
PZBlackBox.setMode("DEEP") -- не более 60 секунд, дороже обычного режима
PZBlackBox.stop()
```

Для постоянного выбора режима можно вручную создать `{cachedir}/BlackBox/blackbox.properties`: `mode=LIGHT` или BALANCED, `jfr=true`, `automaticExport=true`. BlackBox не создаёт этот файл сам и не редактирует конфигурации игры. До публикации пользователю достаточно значения по умолчанию.

## Подпись

Кандидат **unsigned**: подпись автора не фабрикуется, чужой `.zbs` не копируется. На установленной политике с разрешением unsigned требуется индивидуальное одобрение хеша. Если политика пользователя запрещает unsigned, установка блокируется до настоящей авторской подписи; политику обходить нельзя.

Для публикации автор должен настроить собственный Ed25519-ключ штатным механизмом ZombieBuddy, связать публичный ключ со своей Steam-личностью и получить соседний `PZBlackBox.jar.zbs`. Приватный ключ никогда не коммитить. Описание и процедура: [ZombieBuddy v2.3.4 Mod Signing](https://github.com/zed-0xff/ZombieBuddy/blob/v2.3.4/doc/ModSigning.md). Фактическая подпись/проверка доверия автора в этом проекте пока не выполнена. Публикация в Workshop требует отдельного разрешения владельца.

## Воспроизводимая сборка

`blackbox/build.ps1` принимает GameDir, GradleHome и EcjJar. Gradle запускается Java из игры; системные JAVA_HOME/PATH не меняются. Задача build компилирует, выполняет синтетические/трансформационные тесты и формирует ZIP. Только свои классы включаются в JAR, игровые файлы и ZombieBuddy — compile/test-only.

- Gradle 9.1.0: [официальный ZIP](https://downloads.gradle.org/distributions/gradle-9.1.0-bin.zip), SHA-256 `a17ddd85a26b6a7f5ddb71ff8b05fc5104c0202c6e64782429790c933686c806`.
- ECJ 3.43.0: [Maven Central](https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.43.0/ecj-3.43.0.jar), SHA-256 использованного экземпляра `c786468c65e906498e7e36ece4e0d04c6d3dd34c9a61b34a3a5b512801911a82`.
- Java Zulu 25.0.1 — установленная JVM игры. Target21 не требует новых игровых зависимостей.

Источники зависимостей: ZombieBuddy MIT, Byte Buddy Apache-2.0 предоставляется ZombieBuddy, ECJ EPL-2.0 используется только для сборки, Gradle Apache-2.0 — только сборочный инструмент. Их JAR не включены в наш дистрибутив. Собственный код — MIT.

Откат: закрыть игру и отключить PZBlackBox; при желании удалить только его локальную папку. BlackBox не записывает данные в мир и не добавляет игровые предметы. Диагностику `Zomboid/BlackBox` можно сохранить или удалить отдельно; сейвы и настройки игры не затрагиваются.
