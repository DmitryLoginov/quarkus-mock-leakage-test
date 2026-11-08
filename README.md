# Лаборатория утечки Mockito-моков в `@QuarkusTest`

Небольшой проект на Quarkus 3.40.1.1, который одновременно показывает:

- штатную изоляцию `@InjectMock` между тестовыми методами;
- то, что обычный Mockito `@Mock` сам по себе не подменяет CDI-bean;
- воспроизводимую утечку stubbing у долгоживущего `@Mock`, вручную установленного в CDI;
- исправленный вариант с новым моком для каждого тестового метода;
- характерный симптом: тест проходит отдельно, но падает в полном прогоне.

Это не доказательство дефекта `@InjectMock`. На данном минимальном примере Quarkus очищает его stubbing. Проект воспроизводит тот же наблюдаемый симптом через неправильный жизненный цикл обычного Mockito-мока и помогает искать похожую конструкцию в реальном приложении.

## Требования

- JDK 21;
- Maven 3.9+;
- PowerShell или другая командная оболочка.

Все зависимости загружаются Maven. Версия Quarkus зафиксирована в `pom.xml` как `3.40.1.1`.

## Быстрый запуск

Обычный прогон исключает намеренно падающий тест:

```powershell
mvn clean test
```

Ожидаемый результат: `BUILD SUCCESS`, 11 тестов, 0 ошибок. В диагностике видны identity мока, число stubbings и полученное значение.

Только «пострадавший» метод проходит, потому что метод, настраивающий мок, не запускался:

```powershell
mvn -Pfailure-demo '-Dtest=InstalledMockitoMockFailureDemoTest#victim_expects_unconfigured_mock' test
```

Ожидаемый результат: `BUILD SUCCESS`, `stubbings=0`, `observed=null`.

Полный opt-in сценарий намеренно падает:

```powershell
mvn -Pfailure-demo test
```

Ожидаемый результат: `BUILD FAILURE` только в `victim_expects_unconfigured_mock()`:

```text
expected: <null> but was: <leaked greeting>
```

Перед падением оба метода печатают один identity, а второй метод видит `stubbings=1`. Это и есть контролируемая демонстрация просачивания поведения.

## Матрица экспериментов

| Тест | Что проверяет | Результат |
|---|---|---|
| `AInjectMockIsolationTest` | Обычный lifecycle `@InjectMock` | Между методами новый мок, старый stubbing отсутствует |
| `InjectMockPerClassProbeTest` | `@InjectMock` и `@TestInstance(PER_CLASS)` | Identity тот же, но Quarkus 3.40.1.1 очищает stubbing: `1 → 0` |
| `BRealBeanAfterInjectMockTest` | Тест после класса с `@InjectMock` | CDI снова использует реальный bean |
| `PlainMockitoMockControlTest` | Обычный Mockito `@Mock` без установки в Quarkus | Поле замокировано, но CDI продолжает использовать реальный bean |
| `InstalledMockitoMockLeakObservationTest` | Один `@Mock` установлен в CDI на весь `PER_CLASS` | Тот же identity и stubbing сохраняются во втором методе |
| `InstalledMockitoMockFixedTest` | Новый `@Mock` создаётся и устанавливается перед каждым методом | Во втором методе другой identity и 0 stubbings |
| `InstalledMockitoMockFailureDemoTest` | Setup и victim используют один долгоживущий мок | Отдельный victim зелёный; весь класс красный |

Основные классы приложения предельно просты: `GreetingService` делегирует вызов CDI-компоненту `GreetingSource`. Поэтому различия в результате определяются жизненным циклом моков, а не бизнес-логикой.

## Почему варианты ведут себя по-разному

`@InjectMock` из `io.quarkus.test` создаёт Mockito-мок и подменяет им normal-scoped CDI-bean для соответствующего Quarkus-теста. Управление его жизненным циклом остаётся у тестового расширения Quarkus.

`@Mock` из `org.mockito` только создаёт Mockito-мок в поле. CDI о нём ничего не знает. В демонстрации утечки этот мок вручную устанавливается через `QuarkusMock.installMockForType(...)`, причём всего один раз в `@BeforeAll`. Из-за `@TestInstance(PER_CLASS)` один объект мока и его stubbing переживают границу между методами.

Исправленный тест создаёт Mockito-мок через `MockitoAnnotations.openMocks(this)`, устанавливает его в CDI в `@BeforeEach`, а в `@AfterEach` закрывает полученный `AutoCloseable`. Каждый метод получает новый объект без прежних настроек.

Если объект мока по архитектурным причинам должен жить дольше одного метода, его можно сбрасывать явно:

```java
Mockito.reset(mock);             // удаляет stubbing и историю вызовов
Mockito.clearInvocations(mock);  // удаляет только историю вызовов, stubbing остаётся
```

`clearInvocations` поэтому не исправляет показанную здесь утечку поведения.

Подробнее о штатных механизмах: [Quarkus — Testing Your Application, Mock Support](https://quarkus.io/guides/getting-started-testing#mock-support).

## Что проверить в рабочем проекте

- Есть ли `@TestInstance(PER_CLASS)` на тесте, базовом классе или meta-аннотации.
- Не хранятся ли моки в `static`-полях, singleton-объектах или общих test fixtures.
- Не наследуются ли `@Mock`, setup или ручная установка моков из базового тестового класса.
- Для каждого `MockitoAnnotations.openMocks(...)` и `MockitoSession` выполняется ли закрытие/`finishMocking()`.
- Нет ли `QuarkusMock.installMockForType(...)` или `installMockForInstance(...)`, особенно в `@BeforeAll`.
- Не применяется ли `clearInvocations(...)` там, где нужен `reset(...)` или новый мок.
- Включён ли параллельный запуск тестов и могут ли классы одновременно менять один CDI mock/context.
- Не объединяют ли `QuarkusTestProfile`, test resources или callbacks несколько тестов в неожиданно общий lifecycle.
- Одинаково ли воспроизводится проблема при запуске класса, отдельного метода и полного suite; сравните identity и число stubbings диагностикой из `MockDiagnostics`.

Наблюдение про очистку `@InjectMock` относится именно к этому проекту и Quarkus 3.40.1.1. Оно не исключает иной результат при другом расширении, профиле, параллелизме, наследовании или ручном вмешательстве в CDI-контекст.
