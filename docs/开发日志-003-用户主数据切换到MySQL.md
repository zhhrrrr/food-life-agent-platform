# 开发日志 003：用户主数据切换到 MySQL

## 一、本次开发目标

本次开发目标是把 `food-user-service` 的用户主数据从临时 Redis 仓储切换到本地 MySQL。

调整后数据职责变为：

```text
MySQL
保存用户主数据。

Redis
保存验证码和登录 Token。
```

也就是说：

```text
用户是谁、手机号、昵称、头像 -> MySQL
用户是否登录、Token 是否有效 -> Redis
```

## 二、本次新增文件

### 1. UserPO

文件：

```text
food-user-service/food-user-infrastructure/src/main/java/com/foodlife/user/infrastructure/dao/po/UserPO.java
```

作用：

```text
用户表对应的持久化对象。
```

对应 MySQL 表：

```text
food_user_db.user
```

字段：

```text
id
phone
password
nickName
icon
status
createTime
updateTime
```

### 2. IUserMapper

文件：

```text
food-user-service/food-user-infrastructure/src/main/java/com/foodlife/user/infrastructure/dao/IUserMapper.java
```

作用：

```text
MyBatis-Plus 用户表 Mapper。
```

继承：

```text
BaseMapper<UserPO>
```

### 3. MySqlUserRepository

文件：

```text
food-user-service/food-user-infrastructure/src/main/java/com/foodlife/user/infrastructure/repository/MySqlUserRepository.java
```

作用：

```text
用 MySQL 实现用户领域仓储接口 IUserRepository。
```

当前实现方法：

```text
findByPhone
根据手机号查询用户。

save
保存新用户。
```

## 三、本次修改文件

### 1. RedisUserRepository

文件：

```text
food-user-service/food-user-infrastructure/src/main/java/com/foodlife/user/infrastructure/repository/RedisUserRepository.java
```

本次修改：

```text
增加 ConditionalOnProperty。
```

现在只有当配置为：

```yaml
food:
  user:
    repository:
      type: redis
```

才会启用 Redis 用户仓储。

默认情况下不再使用 Redis 保存用户主数据。

### 2. application.yml

文件：

```text
food-user-service/food-user-app/src/main/resources/application.yml
```

本次修改：

```yaml
food:
  user:
    repository:
      type: mysql
```

表示用户仓储默认使用 MySQL。

### 3. infrastructure pom.xml

文件：

```text
food-user-service/food-user-infrastructure/pom.xml
```

本次补充：

```text
lombok
```

用于 `UserPO` 生成 getter / setter。

## 四、当前用户数据流向

### 1. 发送验证码

```text
POST /api/user/code
```

数据进入：

```text
Redis: food:login:code:{phone}
```

### 2. 登录

```text
POST /api/user/login
```

流程：

```text
1. 从 Redis 校验验证码
2. 根据手机号查询 MySQL user 表
3. 如果用户不存在，则插入 MySQL user 表
4. 生成 Token
5. 将登录态写入 Redis Hash
6. 返回 Token
```

### 3. 查询当前用户

```text
GET /api/user/me
```

流程：

```text
1. 请求头携带 authorization
2. food-auth-starter 查询 Redis Token
3. 写入 UserHolder
4. UserController 返回当前用户
```

### 4. 登出

```text
POST /api/user/logout
```

数据删除：

```text
Redis: food:login:token:{token}
```

## 五、当前 MySQL 表

SQL 文件：

```text
docs/sql/food_user_db.sql
```

当前已创建：

```text
food_user_db.user
```

表结构核心字段：

```text
id
phone
password
nick_name
icon
status
create_time
update_time
```

## 六、为什么保留 RedisUserRepository

虽然现在默认使用 MySQL，但仍然保留 `RedisUserRepository`。

原因：

```text
1. 本地快速演示时可以切回 Redis
2. 方便无数据库环境下跑登录闭环
3. 作为仓储接口可替换实现的示例
```

切换方式：

```yaml
food:
  user:
    repository:
      type: redis
```

默认方式：

```yaml
food:
  user:
    repository:
      type: mysql
```

## 七、验证结果

已执行：

```bash
mvn -q test
```

结果：

```text
通过
```

已执行：

```bash
mvn -q -pl :food-user-app -am -DskipTests package
```

结果：

```text
通过
```

## 八、下一步建议

下一步建议实际启动服务，并用接口请求验证完整链路：

```text
1. 启动 Redis
2. 启动 MySQL
3. 启动 food-user-service
4. 请求 /api/user/code
5. 从日志中获取验证码
6. 请求 /api/user/login
7. 查询 MySQL user 表是否生成用户
8. 携带 Token 请求 /api/user/me
9. 请求 /api/user/logout
```

验证通过后，`food-user-service` 就可以作为第一个完成闭环的微服务。

之后再开始开发：

```text
food-business-service
```

也就是店铺、分类、套餐和内容评价服务。
