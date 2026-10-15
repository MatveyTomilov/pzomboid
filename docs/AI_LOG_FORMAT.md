# Формат диагностических данных для внешнего ИИ

Путь: `{реальный cachedir}/BlackBox/sessions/{UTC-pid-random}/`. `-cachedir` учитывается через ZomboidFileSystem. Тестовый override — только `-Dblackbox.outputDir=...` в тестовых процессах.

В папке: `session.json`, `capabilities.json`, `mods.json`, `AI_HANDOFF.md`, `jfr/`, `original-logs/` и JSONL:

| Канал | Содержимое |
|---|---|
| frames | Каждый наблюдаемый кадр BALANCED/DEEP; LIGHT — длинные кадры; секундные сводки интервалов |
| lua | Медленные границы и агрегаты count/inclusive/exclusive/max/histogram |
| java-events | Запросы/снимки стеков JVM на триггерах |
| errors | Дедуплицированные occurrence с достоверными first/last/count и интервальными счётчиками |
| world | Загруженные/ближние зомби и агрегаты update |
| streaming | Медленные операции и суммы по этапам |
| assets | Texture lookup и lifecycle completion |
| system | MXBean CPU/heap/non-heap/GC/threads |
| context | Меню/мир/пауза, водитель/пассажир, скорость km/h, SP/MP/Host при доступности |
| recorder-health | Drops, очередь, ошибки регистратора, состояние компонентов, режимы, временные опоры |

Ротации: `channel-001.jsonl`. `trigger-N.jsonl` содержит окно последних доступных 30 секунд и следующих 15; event_id повторяется из обычных каналов. Дедупликация: `(session_id,event_id)`.

Общие поля каждой строки: `schema_version=1`, `session_id`, `process_id`, `process_role`, `timestamp_utc`, `monotonic_ns`, `event_type`, `measurement_source`, `measurement_status`, `thread_id`, `frame_id` (null если отсутствует), `event_id`, `data`.

Статусы: VERIFIED — реально проверенный явно указанный уровень; PROTOTYPED — реализация без подтверждения игрового поведения; BLOCKED — конкретная ошибка/препятствие; UNSUPPORTED — нет подходящего безопасного механизма. Нельзя повышать PROTOTYPED до VERIFIED только из-за компиляции или существования файла.

Не сопоставлять nanoTime между JVM напрямую; использовать UTC-опоры каждой JVM с учётом погрешности часов. Не считать отсутствие события нулевой нагрузкой: сначала проверить capabilities и drops. Суммы вложенных Lua/streaming-интервалов не являются независимыми частями кадра. Статистический JFR sample не является точной длительностью метода.

Безопасный ZIP — подмножество локальных фактов. Он не содержит raw JFR и полные оригинальные логи; их отсутствие объясняется PRIVACY.md. java-profile.jsonl — ограниченная типизированная проекция JFR, без исходных имён потоков/путей/JVM arguments. Для native JFR monotonic_ns=null с причиной, поскольку independently observed nanoTime отсутствует; native UTC сохраняется. Для custom LongFrame использована source monotonic опора, дополнительно есть jfr_native_timestamp_utc. Имена модов/Workshop ID сохраняются, личные идентификаторы и непроверяемый свободный текст исключаются. BlackBox не выполняет анализ ИИ и ничего не отправляет наружу.
