#!/usr/bin/env python3
import datetime,time
from common import *

def main():
    wait_ready();users,admin=seed(2);user=users[0];event,sku=fixture(admin,2,'optimized','Real RocketMQ smoke')
    queued=request(f'/api/v1/seckill/{sku}','POST',token=user['token']);assert queued['status']=='QUEUED' and queued['delivery']=='ACKNOWLEDGED',queued
    started=time.monotonic();result=None
    while time.monotonic()-started<20:
        result=request('/api/v1/seckill/result/'+queued['reservationId'],token=user['token'])
        if result['status']=='SUCCESS':break
        time.sleep(.1)
    assert result['status']=='SUCCESS',result
    no=result['orderNo'];paid=request(f'/api/v1/orders/{no}/pay','POST',{'paymentKey':'smoke-'+no},user['token']);assert paid['status'] in ('PAID','ISSUED')
    paid_again=request(f'/api/v1/orders/{no}/pay','POST',{'paymentKey':'smoke-'+no},user['token']);assert paid_again['status'] in ('PAID','ISSUED')
    report,rows=audit(sku);assert report['orders']==1 and report['duplicate_orders']==0 and report['conservation']
    report.update(real_broker=True,result=result,payment=paid_again,end_to_end_observed_s=time.monotonic()-started)
    path=ROOT/'benchmark/results'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ-smoke');path.mkdir(parents=True);dump(path/'smoke.json',report)
    print('Real RocketMQ smoke passed:',path,flush=True)
if __name__=='__main__':main()
