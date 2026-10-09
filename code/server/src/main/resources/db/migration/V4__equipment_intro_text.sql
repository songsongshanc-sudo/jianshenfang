-- 器械图文改为可含图片的正文，原来的 1000 字不够放下多段文字和图片地址。

ALTER TABLE equipment MODIFY COLUMN intro TEXT NULL;
