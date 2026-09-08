-- 애플리케이션은 Asia/Seoul 기준으로 동작한다.
-- 저장은 timestamptz(UTC) 를 사용하고, 표시 시점에 KST 로 변환한다.
DO $$
BEGIN
    EXECUTE format('ALTER DATABASE %I SET timezone TO ''Asia/Seoul''', current_database());
END
$$;
