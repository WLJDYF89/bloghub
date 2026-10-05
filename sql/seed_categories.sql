-- BlogHub 分类种子数据
-- 依据 seed_articles.sql 里已有文章的主题归纳出 6 个分类，并回填 article.category_id。
-- 执行前 category 表为空、所有文章 category_id 为 NULL；可重复执行（先按名字取 id）。

USE bloghub;
SET NAMES utf8mb4;

START TRANSACTION;

-- 1. 建立分类。name 有 UNIQUE 约束，重复执行时忽略已存在的名字
INSERT IGNORE INTO category (name, sort_order) VALUES
  ('写作方法', 1),
  ('前端开发', 2),
  ('后端开发', 3),
  ('数据库', 4),
  ('工程实践', 5),
  ('个人成长', 6);

-- 2. 回填文章所属分类（id 取自 seed_articles.sql 落库后的自增主键）
UPDATE article SET category_id = (SELECT id FROM category WHERE name = '写作方法') WHERE id IN (7, 9, 15);
UPDATE article SET category_id = (SELECT id FROM category WHERE name = '前端开发') WHERE id IN (8, 12, 16);
UPDATE article SET category_id = (SELECT id FROM category WHERE name = '后端开发') WHERE id IN (10, 14);
UPDATE article SET category_id = (SELECT id FROM category WHERE name = '数据库')   WHERE id IN (13, 19);
UPDATE article SET category_id = (SELECT id FROM category WHERE name = '工程实践') WHERE id IN (17, 18, 20);
UPDATE article SET category_id = (SELECT id FROM category WHERE name = '个人成长') WHERE id IN (11, 21, 22);

COMMIT;
