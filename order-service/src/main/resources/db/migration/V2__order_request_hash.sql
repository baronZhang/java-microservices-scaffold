-- 请求指纹：同一个幂等键再次提交时，比对请求内容是否一致。
-- 不一致说明客户端把幂等键用错了（比如复用了上一次的键），应该明确报错，而不是悄悄返回上一张订单
ALTER TABLE orders ADD COLUMN request_hash CHAR(64);
