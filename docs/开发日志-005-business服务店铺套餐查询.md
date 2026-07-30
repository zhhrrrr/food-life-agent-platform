# 开发日志 005：business 服务店铺套餐查询

## 一、本次开发目标

本次开始开发第二个业务微服务：

```text
food-business-service
```

第一版目标不是做完整商家后台，而是先跑通前台查询能力：

```text
1. 店铺分类查询
2. 店铺详情查询
3. 按分类查询店铺
4. 按名称查询店铺
5. 店铺套餐查询
6. 套餐详情查询
```

这一步是后续交易服务的基础。后面 `food-trade-service` 下单时，需要调用 business 服务校验：

```text
店铺是否存在
套餐是否存在
套餐是否上架
套餐价格和快照
```

## 二、本次新增服务结构

新增服务：

```text
food-business-service
```

模块结构：

```text
food-business-service
  food-business-api
  food-business-app
  food-business-trigger
  food-business-domain
  food-business-infrastructure
  food-business-types
```

父工程已加入：

```text
food-business-service
```

## 三、领域划分

当前 business 服务内部先划分两个核心领域：

```text
shop domain
店铺和店铺分类。

package domain
美食套餐。
```

后续内容评价也会放入 business 服务：

```text
content domain
探店笔记、评价、评论、点赞、收藏、Feed。
```

## 四、本次新增领域模型

### 1. ShopCategoryEntity

文件：

```text
food-business-domain/src/main/java/com/foodlife/business/domain/shop/model/ShopCategoryEntity.java
```

作用：

```text
店铺分类领域模型。
```

字段：

```text
id
name
icon
sort
```

### 2. ShopEntity

文件：

```text
food-business-domain/src/main/java/com/foodlife/business/domain/shop/model/ShopEntity.java
```

作用：

```text
美食店铺领域模型。
```

字段：

```text
id
name
categoryId
images
area
address
longitude
latitude
avgPrice
sold
comments
score
openHours
```

### 3. MealPackageEntity

文件：

```text
food-business-domain/src/main/java/com/foodlife/business/domain/packagee/model/MealPackageEntity.java
```

作用：

```text
店铺套餐领域模型。
```

字段：

```text
id
shopId
name
description
coverImage
price
originalPrice
stock
sold
status
useRule
```

说明：

```text
Java 中 package 是关键字，所以代码包名使用 packagee。
业务模型仍然叫 MealPackage。
```

## 五、本次新增仓储接口

### 1. IShopRepository

文件：

```text
food-business-domain/src/main/java/com/foodlife/business/domain/shop/repository/IShopRepository.java
```

方法：

```text
listCategories
findShopById
listShopsByCategory
listShopsByName
```

### 2. IPackageRepository

文件：

```text
food-business-domain/src/main/java/com/foodlife/business/domain/packagee/repository/IPackageRepository.java
```

方法：

```text
findById
listByShopId
```

## 六、本次新增领域服务

### 1. ShopDomainService

文件：

```text
food-business-domain/src/main/java/com/foodlife/business/domain/shop/service/ShopDomainService.java
```

作用：

```text
封装店铺查询领域逻辑。
```

当前能力：

```text
1. 查询店铺分类列表
2. 根据 ID 查询店铺
3. 根据分类分页查询店铺
4. 根据名称分页查询店铺
```

### 2. PackageDomainService

文件：

```text
food-business-domain/src/main/java/com/foodlife/business/domain/packagee/service/PackageDomainService.java
```

作用：

```text
封装套餐查询领域逻辑。
```

当前能力：

```text
1. 根据 ID 查询套餐
2. 根据店铺 ID 查询套餐列表
```

## 七、本次新增数据库和表

新增数据库：

```text
food_business_db
```

SQL 文件：

```text
docs/sql/food_business_db.sql
```

当前表：

```text
shop_category
shop
meal_package
```

当前演示数据：

```text
shop_category: 3 条
shop: 3 条
meal_package: 4 条
```

验证结果：

```text
food_business_db 中存在：
meal_package
shop
shop_category
```

## 八、本次新增基础设施层

### 1. PO

```text
ShopCategoryPO
ShopPO
MealPackagePO
```

位置：

```text
food-business-infrastructure/src/main/java/com/foodlife/business/infrastructure/dao/po
```

### 2. Mapper

```text
IShopCategoryMapper
IShopMapper
IMealPackageMapper
```

位置：

```text
food-business-infrastructure/src/main/java/com/foodlife/business/infrastructure/dao
```

### 3. Repository 实现

```text
ShopRepository
PackageRepository
```

位置：

```text
food-business-infrastructure/src/main/java/com/foodlife/business/infrastructure/repository
```

作用：

```text
将 MySQL PO 转换为领域 Entity。
领域层不直接感知 MyBatis-Plus。
```

## 九、本次新增 HTTP 接口

### 1. 健康检查

```http
GET /health
```

返回：

```json
{"code":"0000","message":"success","data":"food-business-service ok"}
```

### 2. 店铺分类列表

```http
GET /api/shop-category/list
```

### 3. 店铺详情

```http
GET /api/shop/{id}
```

示例：

```http
GET /api/shop/1
```

### 4. 按分类查询店铺

```http
GET /api/shop/of/category?categoryId=1&current=1
```

### 5. 按名称查询店铺

```http
GET /api/shop/of/name?name=Hot&current=1
```

### 6. 套餐详情

```http
GET /api/package/{id}
```

示例：

```http
GET /api/package/1
```

### 7. 查询店铺套餐

```http
GET /api/package/of/shop?shopId=1
```

## 十、服务配置

服务端口：

```text
8201
```

数据库：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/food_business_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: root
```

认证配置：

```text
当前查询接口全部放行。
```

放行接口：

```text
/health
/api/shop-category/**
/api/shop/**
/api/package/**
```

## 十一、验证结果

### 1. Maven 验证

执行：

```bash
mvn -q test
```

结果：

```text
通过
```

执行：

```bash
mvn -q -pl :food-business-app -am -DskipTests package
```

结果：

```text
通过
```

### 2. 接口验证

已验证：

```text
GET /health
GET /api/shop-category/list
GET /api/shop/1
GET /api/shop/of/category?categoryId=1&current=1
GET /api/shop/of/name?name=Hot&current=1
GET /api/package/1
GET /api/package/of/shop?shopId=1
```

结果：

```text
全部成功返回数据。
```

## 十二、当前结论

`food-business-service` 第一版已经完成：

```text
1. DDD 多模块骨架
2. food_business_db 建库建表
3. 店铺分类数据
4. 店铺数据
5. 套餐数据
6. 店铺查询接口
7. 套餐查询接口
8. 服务启动和接口联调验证
```

## 十三、下一步建议

下一步建议有两个方向：

```text
方向 A：继续完善 business 服务
增加套餐库存快照接口，为交易服务下单做准备。

方向 B：开始 food-trade-service
实现普通购买订单，调用 business 服务查询套餐。
```

推荐先做方向 A：

```text
为 food-trade-service 准备一个套餐交易快照接口。
```

原因：

```text
交易下单不能直接依赖前端传来的价格和套餐名称，
必须从 business 服务获取套餐快照。
```
