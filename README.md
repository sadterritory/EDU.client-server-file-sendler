# 📁 FTP-подобный файлообменный клиент (Java Socket API)

## 📌 Техническое задание
Реализация клиент-серверной системы для передачи файлов между пользователями через TCP-соединение с использованием:
- Последовательной передачи бинарных данных блоками по 4096 байт
- Установленного соединения (persistent connection)
- Командного протокола для управления передачей
- Автоматического восстановления соединения

## 🌟 Особенности реализации

### 🔄 Протокол передачи
| Команда              | Формат                     | Описание                          |
|----------------------|----------------------------|-----------------------------------|
| `CLIENT_LIST`        | `CLIENT_LIST user1 user2`  | Список подключенных клиентов      |
| `RECEIVE_FILE`       | `RECEIVE_FILE filename size sender` | Уведомление о входящем файле |
| `ACK_FILE_RECEIVED`  | `ACK_FILE_RECEIVED filename` | Подтверждение получения файла    |
| `SEND_FILE`         | `SEND_FILE filename recipient` | Запрос на отправку файла       |
