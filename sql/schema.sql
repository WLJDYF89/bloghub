-- =============================================================================
--  BlogHub 建库建表脚本 (schema.sql)
-- =============================================================================
--  数据库：MySQL 8.0+（本脚本在 MySQL 8.2.0 + InnoDB 上验证通过）
--  字符集：utf8mb4 / utf8mb4_0900_ai_ci（支持 emoji 与全部中文）
--  说明  ：本文件与线上 bloghub 库的真实结构逐字段一致，可直接用于全新部署。
--
--  执行方式（任选其一）：
--    A. 命令行（推荐，注意用 --default-character-set=utf8mb4）
--         mysql -uroot -p --default-character-set=utf8mb4 < sql/schema.sql
--    B. 已进入 mysql 客户端后
--         source sql/schema.sql;
--    C. 图形化工具（Navicat / DataGrip / IDEA Database）直接打开本文件执行
--
--  完整的初始化顺序（新机器从零启动）：
--    1) sql/schema.sql          —— 建库 + 建表 + role 基础数据（本文件）
--    2) sql/seed_articles.sql   —— 文章示例数据（可选，仅用于演示）
--    3) sql/seed_categories.sql —— 分类示例数据（可选，依赖上一步的文章 id）
--    4) 注册第一个账号，再手动把它提升为管理员（见文件末尾「首个管理员」）
--
--  注意：本脚本会 DROP 并重建所有表，库中现有数据会全部丢失！
--        只想升级结构而保留数据时，请勿直接执行，改用 ALTER TABLE。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 0. 建库
-- ---------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `bloghub`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `bloghub`;

-- 建表期间临时关闭外键检查，避免受环境中已有约束的影响
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `article_tag`;
DROP TABLE IF EXISTS `article`;
DROP TABLE IF EXISTS `tag`;
DROP TABLE IF EXISTS `category`;
DROP TABLE IF EXISTS `user`;
DROP TABLE IF EXISTS `role`;


-- ---------------------------------------------------------------------------
-- 1. role —— 系统角色权限表
--    role_id 取值被后端硬编码引用：
--      1 = 超级管理员（RoleIdConstant.ADMIN_ROLE_ID）
--      2 = 普通用户  （RoleIdConstant.USER_ROLE_ID）
--    ⚠ 不要修改 1 / 2 这两个 id，前端 auth.js 用 roleId === 1 判断管理员。
-- ---------------------------------------------------------------------------
CREATE TABLE `role` (
  `role_id`   bigint      NOT NULL AUTO_INCREMENT COMMENT '角色主键ID',
  `role_code` varchar(32) NOT NULL                COMMENT '角色权限标识（Spring Security识别）',
  `role_name` varchar(32) NOT NULL                COMMENT '角色中文名称',
  PRIMARY KEY (`role_id`),
  UNIQUE KEY `uk_role_code` (`role_code`) COMMENT '角色标识唯一索引，保证权限唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统角色权限表';


-- ---------------------------------------------------------------------------
-- 2. user —— 用户信息表
--    ⚠ user 是 MySQL 保留字，所有手写 SQL 都要加反引号：
--         SELECT * FROM `user` WHERE username = ?;
--    username 即 article.author 的取值来源（按用户名关联，未建外键）。
--    password 存 BCrypt 哈希：形如 $2a$10$...，固定 60 字符，自带随机盐。
--    列宽 128 是为将来的 Argon2（最长约 97 字符）预留的余量。
--    历史遗留：早期版本存的是无盐 MD5（32 位十六进制）。登录时
--    PasswordService#verifyAndUpgradeIfLegacy 会在验密通过后自动把它改写成
--    BCrypt，所以库里可能短暂出现两种格式并存，属正常现象。
-- ---------------------------------------------------------------------------
CREATE TABLE `user` (
  `user_id`     bigint       NOT NULL AUTO_INCREMENT COMMENT '用户主键ID',
  `username`    varchar(32)  NOT NULL                COMMENT '登录用户名',
  `password`    varchar(128) NOT NULL                COMMENT '登录密码（加密存储）',
  `nickname`    varchar(32)  NOT NULL DEFAULT ''     COMMENT '用户昵称',
  `role_id`     bigint       NOT NULL                COMMENT '角色ID：1-超级管理员，2-普通用户',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '账号创建时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '账号更新时间',
  `status`      tinyint      NOT NULL DEFAULT '1'    COMMENT '账号状态：0-禁用，1-正常',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_username` (`username`) COMMENT '用户名唯一索引，防止重复注册',
  KEY `idx_role_id` (`role_id`)         COMMENT '角色ID索引，权限快速查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户信息表';


-- ---------------------------------------------------------------------------
-- 3. category —— 文章分类表
--    name 有唯一约束，后端新增/改名时靠它兜底重复校验
--    （CategoryServiceImpl 捕获 DuplicateKeyException 转成「分类名已存在」）。
-- ---------------------------------------------------------------------------
CREATE TABLE `category` (
  `id`          bigint      NOT NULL AUTO_INCREMENT COMMENT '分类主键ID',
  `name`        varchar(32) NOT NULL                COMMENT '分类名称',
  `sort_order`  int         NOT NULL DEFAULT '0'    COMMENT '排序值，越小越靠前',
  `create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章分类表';


-- ---------------------------------------------------------------------------
-- 4. tag / article_tag —— 标签表与关联表
--    当前后端代码尚未使用（无对应 Mapper），保留以兼容既有数据；
--    如确认不需要可整段删除。
-- ---------------------------------------------------------------------------
CREATE TABLE `tag` (
  `id`   bigint      NOT NULL AUTO_INCREMENT COMMENT '标签主键ID',
  `name` varchar(32) NOT NULL                COMMENT '标签名称',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章标签表';

CREATE TABLE `article_tag` (
  `article_id` bigint NOT NULL COMMENT '文章ID',
  `tag_id`     bigint NOT NULL COMMENT '标签ID',
  PRIMARY KEY (`article_id`,`tag_id`),
  KEY `idx_article_tag_tag` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章-标签关联表';


-- ---------------------------------------------------------------------------
-- 5. article —— 博客文章数据表
--    关联关系：author → user.username（按字符串关联，未建外键）
--              category_id → category.id（可为 NULL，表示未归类）
--
--    ⚠ 后端两处哨兵约定，改表结构时必须留意：
--      1) category_id = NULL 表示「未归类」；接口传 categoryId = 0 是「清空分类」
--         的哨兵值，SQL 里会转成 NULL（见 ArticleMapper.xml 的 updateArticle）。
--      2) status = 1 表示已发布（公开列表只查 status = 1），
--         其它值（0 / 2 …）对游客一律按草稿处理，只有作者本人能看到。
--
--    索引说明：
--      idx_article_author_status 覆盖「我的文章」按作者+状态过滤并按更新时间排序，
--      是 /article/my、/article/overview 的主索引。
--      title / content 的关键词搜索用的是 LIKE '%kw%'，前导通配符无法走索引，
--      数据量大时应换成全文索引或搜索引擎；idx_title 只在前缀匹配时有效。
-- ---------------------------------------------------------------------------
CREATE TABLE `article` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '文章主键ID',
  `title`       varchar(128) NOT NULL                COMMENT '文章标题',
  `content`     text         NOT NULL                COMMENT '文章正文内容',
  `author`      varchar(32)  NOT NULL                COMMENT '作者昵称',
  `category_id` bigint       DEFAULT NULL            COMMENT '所属分类ID，NULL 表示未归类',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '文章发布时间',
  `update_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '文章更新时间',
  `status`      tinyint      NOT NULL DEFAULT '1'    COMMENT '文章状态：0-下架/删除，1-正常公开',
  PRIMARY KEY (`id`),
  KEY `idx_create_time` (`create_time`) COMMENT '发布时间索引，分页排序查询',
  KEY `idx_title` (`title`)             COMMENT '标题索引，支持关键词搜索',
  KEY `idx_article_category` (`category_id`),
  KEY `idx_article_author_status` (`author`,`status`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='博客文章数据表';

SET FOREIGN_KEY_CHECKS = 1;


-- ---------------------------------------------------------------------------
-- 6. 基础数据：角色必须存在，否则注册时 role_id 会指向不存在的角色
-- ---------------------------------------------------------------------------
INSERT INTO `role` (`role_id`, `role_code`, `role_name`) VALUES
  (1, 'ROLE_ADMIN', '超级管理员'),
  (2, 'ROLE_USER',  '普通用户');


-- ---------------------------------------------------------------------------
-- 7. 首个管理员怎么来？
--    项目没有「注册成管理员」的入口（注册接口固定写 role_id = 2），
--    所以第一次部署要先注册一个普通账号，再手工提升为管理员：
--
--      -- 1) 先在页面上注册账号，假设用户名是 alice
--      -- 2) 提升为管理员
--      UPDATE `user` SET role_id = 1 WHERE username = 'alice';
--      -- 3) 重新登录（JWT 里不含角色，后端每次请求查库取最新角色）
--
--    如果只想验证接口，也可以用 MD5('123456') 直接插一条测试管理员：
--      INSERT INTO `user` (username, password, nickname, role_id, status)
--      VALUES ('admin', MD5('123456'),
--              '管理员', 1, 1);
--    ⚠ 仅限本地调试，任何对外环境都不要用弱口令。
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- 8. 快速自检：执行完可用下面的语句确认结构和基础数据
-- ---------------------------------------------------------------------------
-- SHOW TABLES;                                   -- 应看到 6 张表
-- SELECT * FROM `role`;                          -- 应有 2 行
-- SELECT COUNT(*) FROM information_schema.statistics
--   WHERE table_schema = 'bloghub';              -- 索引数量
