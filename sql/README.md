# BlogHub 数据库脚本

## 文件说明

| 文件 | 用途 | 必需 |
|---|---|---|
| `schema.sql` | 建库 + 建表 + `role` 基础数据 | ✅ 必需 |
| `seed_articles.sql` | 22 篇示例文章（演示用） | 可选 |
| `seed_categories.sql` | 6 个示例分类，并回填文章的 `category_id` | 可选 |

## 从零初始化

```bash
# 1. 建库建表（必需）
mysql -uroot -p --default-character-set=utf8mb4 < sql/schema.sql

# 2. 示例数据（可选，按顺序执行）
mysql -uroot -p --default-character-set=utf8mb4 < sql/seed_articles.sql
mysql -uroot -p --default-character-set=utf8mb4 < sql/seed_categories.sql
```

已进入 mysql 客户端时也可以：

```sql
source sql/schema.sql;
source sql/seed_articles.sql;
source sql/seed_categories.sql;
```

## 重要提醒

1. **`schema.sql` 会 DROP 并重建全部表，库中现有数据全部丢失。**
   只想改结构而保留数据时，请勿执行，改用 `ALTER TABLE`；升级前先 `
   mysqldump -uroot -p bloghub > backup.sql`。

2. **`seed_categories.sql` 依赖 `seed_articles.sql`。**
   它按硬编码的文章 id（7~22）回填分类，这些 id 是 `seed_articles.sql`
   在**空表**中顺序插入得到的自增值。若 `article` 表里已有别的数据，
   回填会打偏——此时请手工指定分类，或先清空 `article` 表再执行。

3. **`seed_*.sql` 默认按「所有文章都属于已有用户」的前提编写**
   （`article.author` 存的是 `user.username`）。
   若库里还没有 `user` 记录，文章的作者名就是「悬空」的：公开列表能显示，
   但对应账号登录后在自己的「我的文章」里看不到这些文章。
   需要示例作者可见时，先注册同名账号。

4. **两个 seed 文件是 GBK 编码**，而文件内部声明了 `SET NAMES utf8mb4`。
   用 `<` 重定向导入时中文可能乱码。建议改用客户端内 `source` 命令，
   或先把文件转成 UTF-8。`schema.sql` 是 UTF-8，无此问题。

5. **MySQL 保留字 `user`** 在所有手写 SQL 中必须加反引号：

   ```sql
   SELECT * FROM `user` WHERE username = 'alice';
   ```

6. **角色 id 被代码硬编码**，不要改：

   | role_id | role_code | 含义 |
   |---|---|---|
   | 1 | ROLE_ADMIN | 超级管理员 |
   | 2 | ROLE_USER | 普通用户 |

7. **`user.password` 存的是 BCrypt 哈希**（`$2a$10$...`，60 字符，自带随机盐），
   **不是 MD5**。手工插账号时不能写 `MD5('123456')`，要用应用生成的 BCrypt 值
   （见 `server/src/main/java/guat/lxy/service/PasswordService.java`
   与 `../.verify-bcrypt/MakeBcrypt.java`）。
   早期版本的账号可能还是 32 位 MD5，登录成功后会由
   `PasswordService#verifyAndUpgradeIfLegacy` 自动升级成 BCrypt。

## 首个管理员

注册接口固定写入 `role_id = 2`，项目没有「注册成管理员」的入口，
所以首次部署需要手工提升：

```sql
-- 1) 先在页面上注册账号，假设用户名是 alice
-- 2) 提升为管理员
UPDATE `user` SET role_id = 1 WHERE username = 'alice';
-- 3) 重新登录（JWT 里不含角色，后端每次请求查库取最新角色）
```

## 表结构速览

```
role ──< user ──< article >── category
                   │
                   └──< article_tag >── tag
```

- `article.author` 关联 `user.username`（字符串关联，未建外键）
- `article.category_id` 关联 `category.id`，NULL 表示未归类
  （接口传 `categoryId = 0` 是「清空分类」的哨兵值，落库为 NULL）
- `article.status`：1 = 已发布（公开列表只查 `status = 1`），其它值视为草稿
- `tag` / `article_tag` 当前后端代码未使用，属预留表
