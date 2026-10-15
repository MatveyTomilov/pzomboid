# Результаты проверок

Первый полный synthetic run: **25 passed / 0 failed**. После независимого ревью и исправлений: **36 passed / 0 failed**. Финальный прогон после всех изменений фиксируется внизу перед выдачей кандидата.

Среда: Zulu25.0.1, установленный ZombieBuddy2.3.4, реальный projectzomboid.jar B42.21.0, Gradle9.1.0, ECJ3.43.0 target21. Игра/Host не запускались. Все synthetic outputs явно помечены, находятся в игнорируемом blackbox/build/test-output.

| Группа | Выполненные проверки |
|---|---|
| Сборка | compileJava/JAR/compileTests/testAgent/stageMod/modZip через Gradle |
| JSON | Unicode/escaping/roundtrip, null/NaN, неизвестные объекты, ограничение вложенности, восстановление после ошибки сериализации |
| Schema/time | Все каналы имеют envelope; source monotonic/UTC/thread/frame сохраняются без подмены writer timestamp |
| Кадры | Известная выборка интервалов, p95/p99/FPS/1%low, пустая выборка |
| Writer | 4 concurrent producers по 10000 offers, drops, ротация, файловый лимит, валидные строки после drain |
| Trigger | Synthetic окно до30/после15 секунд, исключение слишком старых данных |
| Lua | Inclusive/exclusive, настоящая array-wrapper dedup, scalar recursion, exception unwind |
| Errors | Reporting repeats и nested logger overload dedup; continuous producer не монополизирует sampler |
| Lifecycle | Повторный start/double close, отказ компонента, LIGHT/DEEP, первоначальный DEEP/expiry, shutdown всех subsystem totals |
| Registry | Unknown source остаётся unknown, metadata document больше128KiB экспортируется, per-JAR loaded различает файлы, installed copy fixtures и настоящий SHA-256 проверены |
| JFR | Реальная программная запись/чтение custom LongFrame; безопасная типизированная проекция без raw binary |
| ZIP | JSONL валиден, сообщения с личными данными исключены, raw JFR не включён, original local logs сохранены, repeated export disk budget |
| B42 package | Настоящий JavaModInfo.parse из ZombieBuddy2.3.4 читает staged42/mod.info и находит JAR |
| ByteBuddy | Настоящий PatchTransformer перевёл аннотации;19 patches/33 overloads преобразованы против реального game bytecode без инициализации игровых классов |
| Исполнение | Настоящий PatchEngine + изолированный test javaagent применили advice к synthetic target; результат/исключение сохранены; Origin descriptor отличает scalar/array overload даже когда scalar argument сам является массивом |
| Crash | Отдельная тестовая JVM аварийно завершена;50 заранее flushed JSONL записей читаются |
| Benchmark |100000 границ после прогрева; результат synthetic-benchmark.json, не игровой overhead |

Первый microbenchmark:16467400ns для100000 enter/exit,164.674ns/границу. Это результат конкретного синтетического прогона, **не 1–2% замедления игры**. Цель игрового overhead пока не проверена.

Независимое ревью первого implementation commit вернуло REQUEST CHANGES: startup DEEP, recursive Lua suppression, metadata limit, per-JAR attribution, error-drain starvation, missing shutdown aggregates. Добавлены регрессионные сценарии. Повторное ревью точного исправленного коммита требуется перед выдачей.

Предупреждение Java25 о deprecated sun.misc.Unsafe выводится установленным Byte Buddy при synthetic retransformation; не подавлялось изменением глобальных JVM flags. Ошибкой теста не является.

Не выполнены и не объявляются успешными: реальная графическая игровая сессия, MP с другом, длительный soak test, измерение overhead в игре и авторская Workshop-подпись.
