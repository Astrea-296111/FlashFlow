#!/usr/bin/env python3
import datetime, json, time
from common import *

def main():
    path=ROOT/'benchmark/results'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ-sql-index');path.mkdir(parents=True)
    with database() as db,db.cursor() as c:
        # Dedicated fixture table; application orders are never modified by this experiment.
        c.execute('DROP TABLE IF EXISTS benchmark_order_scan')
        c.execute('CREATE TABLE benchmark_order_scan(id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL,status VARCHAR(16) NOT NULL,expire_at DATETIME(3) NOT NULL) ENGINE=InnoDB')
        base=datetime.datetime(2026,9,30,0,0)
        for begin in range(0,100000,1000):
            rows=[(i+1,i%1000,'WAIT_PAY' if i%100==0 else 'ISSUED',base+datetime.timedelta(seconds=i)) for i in range(begin,begin+1000)]
            c.executemany('INSERT INTO benchmark_order_scan VALUES(%s,%s,%s,%s)',rows)
        query="EXPLAIN ANALYZE SELECT id FROM benchmark_order_scan WHERE status='WAIT_PAY' AND expire_at<='2026-09-30 12:00:00' ORDER BY expire_at LIMIT 100"
        def plans():
            result=[]
            for i in range(3):
                at=time.perf_counter();c.execute(query);r=c.fetchall();result.append({'repeat':i+1,'wall_ms':(time.perf_counter()-at)*1000,'plan':r})
            return result
        before=plans();c.execute('CREATE INDEX idx_status_expire ON benchmark_order_scan(status,expire_at,id)');after=plans()
        dump(path/'explain-analyze.json',{'rows':100000,'query':query,'before':before,'after':after,'fixture_table':'benchmark_order_scan','note':'Index experiment only; not 100000 real application orders'})
        c.execute('DROP TABLE benchmark_order_scan')
    print('SQL evidence:',path,flush=True)
if __name__=='__main__':main()
