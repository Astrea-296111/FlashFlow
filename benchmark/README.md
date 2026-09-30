# Reproducible experiments

All final measurements use real native MySQL 8.4.9, Redis 7.4.2 and RocketMQ 5.3.3 on 2026-09-30. The environment does not provide a Docker daemon. Compose configuration parsing is separately validated; full image startup is not claimed.

- Final paired trials: [20260930T123027Z-paired-final](results/20260930T123027Z-paired-final/).
- Final 42-test verification: [20260930T122848Z-tests-final](results/20260930T122848Z-tests-final/).
- Complete real-broker faults and offsets: [20260930T124455Z-faults](results/20260930T124455Z-faults/).
- 100000 synthetic-row index experiment: [20260930T114725Z-sql-index](results/20260930T114725Z-sql-index/).
- Early real-broker/payment smoke: [20260930T114718Z-smoke](results/20260930T114718Z-smoke/).

Other folders retain successful prior runs and failed experiments. They are not silently merged into the final results. The failed observed group includes a zero-accepted trial caused by initially shared test/demo Redis state; that entire group is excluded. Test Redis is now DB1, demo DB0. The early failed MQ replay fixture used a local SQL session time zone incorrectly; final Python connections set UTC explicitly.

Install `requirements.txt`, set the same development JWT_SECRET used by the API, and install k6 1.3.0. Run `scripts/smoke.py`, `scripts/run.py`, `scripts/sql_index.py` and `scripts/faults.py` as described in the runbook. Native faults require explicit worker PID/Java/runtime home/ROCKETMQ_HOME and `.local/classpath.txt`; Docker faults run the packaged compiled probe inside the worker network. Local synthetic user tokens are never included in evidence.

RPS includes sold-out rejections. HTTP 202 is a queued reservation, not a committed order. SQL observations poll every 25ms for both variants. Median per-run percentiles are not merged population percentiles. Resource samples have 0.5s polling and metric-update lag. Detailed conclusions: [performance](../docs/07-performance-report.md), [faults](../docs/06-failure-injection.md).
