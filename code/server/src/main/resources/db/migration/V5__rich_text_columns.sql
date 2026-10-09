-- 公告和加盟文案改为富文本，原来的长度放不下标题、颜色和图片地址。

ALTER TABLE notice MODIFY COLUMN content TEXT NOT NULL;
ALTER TABLE franchise_page MODIFY COLUMN intro TEXT NULL;
ALTER TABLE franchise_page MODIFY COLUMN points TEXT NULL;
ALTER TABLE franchise_page MODIFY COLUMN support TEXT NULL;
ALTER TABLE franchise_page MODIFY COLUMN steps TEXT NULL;
