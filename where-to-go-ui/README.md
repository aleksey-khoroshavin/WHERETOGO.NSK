# WhereToGo.NSK Android

Мобильная часть приложения WhereToGo.NSK

## Общая информация

Основу приложения составляет окно карты, на которой располагаются маркеры - места.
Также включены окна авторизации, окна работы с фильтрами, списками избранных и посещенных мест.

## Техническая информация

### Технологический стек

* Java 17.0.8
* Gradle 8.0.2

### Сборка проекта

        ./gradlew app:build

### Sonar

Для проверки тестового покрытия кода необходимо выполнить команду:

        ./gradlew sonarqube