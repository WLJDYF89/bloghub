-- BlogHub 文章种子数据
-- 作者均来自现有 user 表；当前 category/tag 表为空，因此 category_id 使用 NULL。

USE bloghub;
SET NAMES utf8mb4;

START TRANSACTION;

INSERT INTO article (title, content, author, status, category_id, create_time, update_time)
VALUES (
  '把博客当作长期项目，而不是一次性作业',
  '刚开始写博客时，我总想一次就写出很完整的内容，结果反而迟迟无法发布。后来我把目标改成留下一个可继续修改的版本，写作才真正持续下来。

先发布，再迭代。文章不是考试答卷，它更像一块会持续生长的笔记。',
  'user',
  1,
  NULL,
  '2026-07-02 09:20:00',
  '2026-07-03 10:05:00'
);

INSERT INTO article (title, content, author, status, category_id, create_time, update_time)
VALUES (
  '在 Vue 页面里处理异步状态的一点经验',
  '列表页通常至少有三类状态：加载中、加载成功和加载失败。把它们写在模板里并不难，难的是每次请求都保持同样的处理顺序。

我的做法是统一使用 loading、error、data 三个变量，并在请求开始时重置旧错误。这样切换筛选条件时，不会短暂显示上一次的数据。',
  'zhangsan',
  1,
  NULL,
  '2026-07-05 14:10:00',
  '2026-07-05 16:42:00'
);

INSERT INTO article (title, content, author, status, category_id, create_time, update_time)
VALUES (
  '从零搭建个人写作工作流',
  '写作流程不需要很复杂。我现在只保留三个步骤：随时收集想法、每周整理一次提纲、有空时扩写成正文。

真正有效的工具往往很朴素。一个固定的收件箱、一套简单的标签，再加上定期清理，已经足够支撑长期输出。',
  'lisi',
  1,
  NULL,
  '2026-07-09 08:35:00',
  '2026-07-10 11:18:00'
);

INSERT INTO article (title, content, author, status, category_id, create_time, update_time)
VALUES (
  'Java 接口参数校验的边界',
  '接口参数校验不应该只依赖前端。前端校验负责提升体验，后端校验负责守住数据边界，两者解决的是不同问题。

对于必填字段、长度、枚举和关联 ID，最好在进入业务逻辑前完成检查。错误信息要明确到字段，但不要暴露内部实现细节。',
  'user',
  1,
  NULL,
  '2026-07-14 19:05:00',
  '2026-07-15 09:30:00'
);

INSERT INTO article (title, content, author, status, category_id, create_time, update_time)
VALUES (
  '我为什么开始记录每周复盘',
  '每周复盘并不是为了写一份漂亮报告，而是避免忙了一周却说不清完成了什么。

我通常只回答三个问题：本周最重要的事情是什么、哪里被卡住了、下周准备先做什么。写完之后，下一周的行动会清楚很多。',
  'luosheng',
  1,
  NULL,
  '2026-07-19 21:15:00',
  '2026-07-20 08:50:00'
);

INSERT INTO article (title, content, author, status, category_id, create_time, update_time)
VALUES (
  '把复杂表单拆成可维护的小块',
  '一个包含几十个字段的表单，如果全部写在一个组件里，很快就会出现状态混乱和样式重复。

可以按照业务含义拆分字段组，让每个子组件只关心自己的输入和校验。父组件负责提交，子组件通过明确的事件向上传递结果。',
  'admin',
  1,
  NULL,
  '2026-07-24 10:40:00',
  '2026-07-26 13:20:00'
);

INSERT INTO article (title, content, author, status, category_id, create_time, update_time)
VALUES (
  '从一次慢查询中学到的索引常识',
  '慢查询并不总是数据库性能不足。更多时候，问题出在查询条件和索引顺序没有对上。

排查时先看执行计划，再确认过滤字段的选择性。联合索引需要注意最左匹配原则，不能只因为字段出现在索引里就认为一定会被使用。',
  'zhangsan',
  1,
  NULL,
  '2026-07-28 15:25:00',
  '2026-07-29 09:45:00'
);

INSERT INTO article (title, content, author, status, category_id, create_time, update_time)
VALUES (
  '用 Redis 做登录态缓存时要注意什么',
  '登录态缓存适合存放短期、可过期、可重建的数据。真正需要长期保存的用户信息，仍然应该以数据库为准。

缓存键要有稳定命名，过期时间要和业务风险匹配。退出登录时除了删除缓存，还要考虑旧 token 在剩余有效期内是否会被继续使用。',
  'user',
  1,
  NULL,
  '2026-08-03 11:05:00',
  '2026-08-04 15:36:00'
);

INSERT INTO article (title, content, author, status, category_id, create_time, update_time)
VALUES (
  '写作不是输出，而是整理思路',
  '很多时候，我以为自己已经想清楚了一件事，直到开始写才发现逻辑里还有空白。

写作会强迫我们把模糊的判断变成明确句子。这个过程本身就有价值，发布只是顺带发生的结果。',
  'lisi',
  1,
  NULL,
  '2026-08-08 08:10:00',
  '2026-08-09 10:28:00'
);

INSERT INTO article (title, content, author, status, category_id, create_time, update_time)
VALUES (
  '一个页面从拥挤到清晰的间距调整',
  '界面显得拥挤，很多时候不是元素太多，而是元素之间的距离没有层级。

同一组信息内部应该更近，不同组之间应该更远。调整时先确定组内间距，再放大组间差距，页面结构通常就会立刻清楚。',
  'luosheng',
  1,
  NULL,
  '2026-08-13 16:00:00',
  '2026-08-14 09:12:00'
);

INSERT INTO article (title, content, author, status, category_id, create_time, update_time)
VALUES (
  '让代码评审真正有帮助的三件小事',
  '有效的代码评审先从理解改动目标开始。没有上下文时，直接讨论某一行实现很容易偏离重点。

评论时说明问题的影响和建议方向，而不是只写一句这样不好。对于不影响正确性的风格偏好，可以选择不阻塞合并。',
  'admin',
  1,
  NULL,
  '2026-08-20 13:45:00',
  '2026-08-21 17:08:00'
);

INSERT INTO article (title, content, author, status, category_id, create_time, update_time)
VALUES (
  '前后端联调时如何给出有效反馈',
  '联调反馈如果只写接口报错，排查成本会很高。至少应该说明请求路径、请求参数、期望结果、实际结果以及发生时间。

如果能够附上浏览器控制台信息或后端日志片段，沟通会从反复猜测变成共同定位问题。',
  'zhangsan',
  1,
  NULL,
  '2026-08-28 10:15:00',
  '2026-08-28 14:52:00'
);

INSERT INTO article (title, content, author, status, category_id, create_time, update_time)
VALUES (
  '数据库迁移脚本应该怎么组织',
  '数据库变更应该和代码变更一样被认真管理。每次变更单独成文件，文件名带时间和用途，避免把多个无关改动混在一起。

上线前要确认执行顺序、回滚方式和历史数据兼容性。对于不可逆操作，应该先备份，再分阶段执行。',
  'user',
  1,
  NULL,
  '2026-09-04 09:00:00',
  '2026-09-05 11:26:00'
);

INSERT INTO article (title, content, author, status, category_id, create_time, update_time)
VALUES (
  '给旧项目补文档的可行路径',
  '给旧项目补文档，不要一开始就追求完整覆盖。更现实的做法是先记录启动方式、目录职责和最常见的三条业务流程。

后续每修一个问题，就顺手补上相关说明。文档随着维护逐步增长，比集中写一份很快过期的大文档更可靠。',
  'lisi',
  1,
  NULL,
  '2026-09-11 15:20:00',
  '2026-09-12 10:44:00'
);

INSERT INTO article (title, content, author, status, category_id, create_time, update_time)
VALUES (
  '保持学习节奏：把目标拆成可完成的小任务',
  '长期目标很容易让人有压力，因为它离当下太远。把它们拆成一到两个小时能完成的小任务，才更容易开始。

每完成一个小任务就记录结果，不追求一次学完。持续获得可验证的进展，比短期投入大量时间更稳定。',
  'luosheng',
  1,
  NULL,
  '2026-09-20 20:30:00',
  '2026-09-21 08:18:00'
);

COMMIT;
