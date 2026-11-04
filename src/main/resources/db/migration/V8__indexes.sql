-- 조회 성능용 추가 인덱스 (docs/erd.md 5절). V1~V7 의 UNIQUE·FK 인덱스로 이미 덮이는 조회는 여기에 만들지 않는다.
-- 머지 뒤에는 이 파일을 고치지 않고 V9 이후로 추가한다.

-- 사용자별 AI 호출량 집계 (일일 한도 점검, 사용자 단위 장애 조사)
CREATE INDEX idx_ai_call_logs_user_created_at ON ai_call_logs (user_id, created_at);
