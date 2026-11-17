# DaGa - Plugin đá gà cho Paper 1.21.11

Lệnh chính: /daga (gõ /daga giup để xem đủ lệnh)

## Build ra file jar
Cách 1 (máy có JDK 21 + Maven):
    mvn package
    -> jar nằm ở target/DaGa.jar

Cách 2 (không cần cài gì): tạo repo GitHub, đẩy toàn bộ thư mục này lên.
Vào tab Actions -> Build DaGa -> tải artifact "DaGa-jar".

## Cài đặt
- Bỏ DaGa.jar vào plugins/ của server Paper 1.21.11, Java 21.
- Muốn cược tiền: cài Vault + 1 plugin economy (ví dụ EssentialsX). Không có thì vẫn đấu không cược được.
- Chỉnh tỉ lệ, cooldown, drop trong plugins/DaGa/config.yml, dùng /daga reload để áp dụng.

## Quyền
- daga.use (mặc định ai cũng có)
- daga.admin (op): /daga give, /daga reload
