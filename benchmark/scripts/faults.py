#!/usr/bin/env python3
"""Real broker experiments; scoped worker control requires explicit native arguments.

Docker: python benchmark/scripts/faults.py
Native: --native-pid PID --native-java /path/java --native-home /path/runtime-home
Native only: generate .local/classpath.txt first with Maven dependency:build-classpath.
"""
import argparse, concurrent.futures, datetime, hashlib, json, os, pathlib, re, signal, subprocess, tempfile, time
from common import *

def eventually(check, timeout=60):
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        value=check()
        if value:return value
        time.sleep(.05)
    raise AssertionError('Convergence deadline exceeded')

def scalar(sql,params=()):
    with database() as db,db.cursor() as c:
        c.execute(sql,params);row=c.fetchone()
        return next(iter(row.values())) if row else None

def broker_progress(args,directory,label):
    if args.native_pid:
        home=os.environ.get('ROCKETMQ_HOME')
        if not home:raise RuntimeError('Native experiments require ROCKETMQ_HOME for broker offset evidence')
        command=[args.native_java,'-Duser.home='+args.native_home,'-Xms64m','-Xmx128m','-cp',home+'/conf:'+home+'/lib/*','org.apache.rocketmq.tools.command.MQAdminStartup','consumerProgress','-n',os.environ.get('MQ_NAMESERVER','localhost:9876'),'-g','flashflow-order-worker']
    else:command=['docker','compose','exec','-T','broker','sh','mqadmin','consumerProgress','-n','namesrv:9876','-g','flashflow-order-worker']
    p=subprocess.run(command,cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=45)
    (directory/('consumer-progress-'+label+'.txt')).write_text(p.stdout)
    assert p.returncode==0,p.stdout[-1000:]
    match=re.search(r'Consume Diff Total:\s*(\d+)',p.stdout)
    if not match:raise AssertionError('Missing official Broker offset summary')
    return int(match.group(1))

class Worker:
    def __init__(self,args,directory):self.args=args;self.directory=directory;self.pid=args.native_pid;self.child=None
    def stop(self):
        if not self.pid:
            subprocess.run(['docker','compose','stop','worker'],cwd=ROOT,check=True,stdout=subprocess.DEVNULL);return
        command=pathlib.Path(f'/proc/{self.pid}/cmdline').read_bytes().split(b'\0')
        if str(ROOT/'target/flashflow-1.0.0.jar').encode() not in command and b'FlashFlow/target/flashflow-1.0.0.jar' not in command:
            raise RuntimeError('Refusing to stop a process that is not the explicit FlashFlow jar')
        os.kill(self.pid,signal.SIGTERM)
        eventually(lambda: not pathlib.Path(f'/proc/{self.pid}').exists() or b'Z' in pathlib.Path(f'/proc/{self.pid}/stat').read_bytes().split()[2:3],30)
    def start(self,scheduler=True):
        if not self.pid:
            env={**os.environ,'SCHEDULING_ENABLED':str(scheduler).lower()}
            subprocess.run(['docker','compose','up','-d','--no-deps','--force-recreate','worker'],cwd=ROOT,env=env,check=True,stdout=subprocess.DEVNULL)
        else:
            env={**os.environ,'FLASHFLOW_ROLE':'worker','PORT':'8081','RATE_LIMIT_ENABLED':'false','SCHEDULING_ENABLED':str(scheduler).lower()}
            log=open(self.directory/f'worker-scheduler-{scheduler}-{int(time.time())}.log','w')
            self.child=subprocess.Popen([self.args.native_java,'-Xms128m','-Xmx384m','-Duser.home='+self.args.native_home,'-jar',str(ROOT/'target/flashflow-1.0.0.jar')],cwd=ROOT,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True);log.close();self.pid=self.child.pid
        def ready():
            try:return json.load(urllib.request.urlopen(os.environ.get('WORKER_URL','http://localhost:8081')+'/actuator/health',timeout=1))['status']=='UP'
            except Exception:return False
        eventually(ready,90)

def probe(args,directory,topic,body,count=1,level=0,key='probe'):
    payload=directory/(key+'.json');dump(payload,body) if not isinstance(body,str) else payload.write_text(body)
    if not args.native_pid:
        remote='/app/.local/'+payload.name
        subprocess.run(['docker','compose','cp',str(payload),'worker:'+remote],cwd=ROOT,check=True,stdout=subprocess.DEVNULL)
        command=['docker','compose','exec','-T','worker','java','-Duser.home=/app','-Dloader.path=/app/probes','-Dloader.main=MqProbe','-cp','/app/flashflow.jar','org.springframework.boot.loader.launch.PropertiesLauncher','namesrv:9876',topic,remote,str(count),str(level),key]
    else:
        classpath=(ROOT/'.local/classpath.txt').read_text().strip()
        command=[args.native_java,'-Duser.home='+args.native_home,'-cp',classpath,str(ROOT/'benchmark/java/MqProbe.java'),os.environ.get('MQ_NAMESERVER','localhost:9876'),topic,str(payload),str(count),str(level),key]
    p=subprocess.run(command,cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=60)
    (directory/(key+'-producer.log')).write_text(p.stdout)
    assert p.returncode==0,p.stdout[-1500:]

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--native-pid',type=int);parser.add_argument('--native-java');parser.add_argument('--native-home');args=parser.parse_args()
    if args.native_pid and not(args.native_java and args.native_home):parser.error('Native process control requires java and home paths')
    wait_ready();users,admin=seed(40)
    directory=ROOT/'benchmark/results'/f'{datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")}-faults';directory.mkdir(parents=True)
    worker=Worker(args,directory);report={'execution_backend':'native' if args.native_pid else 'Docker Compose','scenarios':{}}
    try:
        worker.stop();event,sku=fixture(admin,20,'optimized','worker-outage')
        def submit(user):return request(f'/api/v1/seckill/{sku}','POST',token=user['token'])
        with concurrent.futures.ThreadPoolExecutor(max_workers=20) as pool:accepted=list(pool.map(submit,users[:20]))
        backlog=scalar('SELECT COUNT(*) FROM orders WHERE sku_id=%s',(sku,));assert backlog==0
        assert len(accepted)==20 and all(r['delivery']=='ACKNOWLEDGED' for r in accepted)
        dump(directory/'backlog-before-restart.json',{'sku_id':sku,'broker_acknowledged':20,'orders':backlog,'reservation_ids':[r['reservationId'] for r in accepted]})
        diff_before=broker_progress(args,directory,'worker-stopped');assert diff_before>=20
        recovery_start=time.monotonic();worker.start();eventually(lambda:scalar('SELECT COUNT(*) FROM orders WHERE sku_id=%s',(sku,))==20)
        elapsed=time.monotonic()-recovery_start;audit_result,rows=audit(sku)
        assert audit_result['conservation'] and audit_result['orders']==20 and audit_result['available_stock']==0
        eventually(lambda:broker_progress(args,directory,'worker-recovered')==0,30)
        report['scenarios']['worker_outage']={'acknowledged_while_stopped':20,'orders_while_stopped':backlog,'recovery_including_jvm_start_s':elapsed,'broker_diff_before':diff_before,'broker_diff_after':0,'audit':audit_result}
        # A: publish an actual previously consumed command twenty more times.
        rid=accepted[0]['reservationId']
        with database() as db,db.cursor() as c:
            c.execute('SELECT user_id,event_id,sku_id,expire_at,created_at FROM seckill_reservation WHERE reservation_id=%s',(rid,));r=c.fetchone()
        epoch=lambda value:int(value.replace(tzinfo=datetime.timezone.utc).timestamp())*1000+value.microsecond//1000
        command={'reservationId':rid,'userId':r['user_id'],'eventId':r['event_id'],'skuId':r['sku_id'],'createdAt':epoch(r['created_at']),'expiresAt':epoch(r['expire_at'])}
        def duplicate_counter():
            raw=urllib.request.urlopen(os.environ.get('WORKER_URL','http://localhost:8081')+'/actuator/prometheus',timeout=2).read().decode()
            matches=[float(line.split()[-1]) for line in raw.splitlines() if line.startswith('flashflow_message_duplicate_total')]
            return sum(matches)
        duplicate_before=duplicate_counter()
        probe(args,directory,'flashflow-orders',command,20,key='duplicate-delivery')
        eventually(lambda:duplicate_counter()>=duplicate_before+20);after,rows=audit(sku)
        assert scalar('SELECT COUNT(*) FROM orders WHERE reservation_id=%s',(rid,))==1 and after['available_stock']==0
        report['scenarios']['duplicate_delivery']={'additional_broker_acknowledged':20,'orders_for_reservation':1,'audit':after}
        # Malformed and null JSON are poison messages, durably quarantined.
        before=scalar('SELECT COUNT(*) FROM message_quarantine')
        malformed='{invalid-'+str(time.time_ns());bodies=[malformed,'null'];hashes=[hashlib.sha256(s.encode()).hexdigest() for s in bodies]
        probe(args,directory,'flashflow-orders',malformed,key='malformed');probe(args,directory,'flashflow-orders','null',key='null-command')
        eventually(lambda:all(scalar('SELECT COUNT(*) FROM message_quarantine WHERE message_hash=%s',(h,))==1 for h in hashes))
        report['scenarios']['poison_messages']={'quarantine_rows_present':2,'new_rows_added':scalar('SELECT COUNT(*) FROM message_quarantine')-before,'note':'Repeated identical null body retains one durable hash row by design'}
        # Disable all maintenance scans: closure can only come from the real delay consumer.
        worker.stop();worker.start(scheduler=False);event,delayed_sku=fixture(admin,1,'optimized','actual-delayed-close')
        received=request(f'/api/v1/seckill/{delayed_sku}','POST',token=users[20]['token'])
        eventually(lambda:scalar('SELECT COUNT(*) FROM orders WHERE sku_id=%s',(delayed_sku,))==1)
        no=scalar('SELECT order_no FROM orders WHERE sku_id=%s',(delayed_sku,))
        expires=time.time()+3
        with database() as db,db.cursor() as c:c.execute('UPDATE orders SET expire_at=%s WHERE order_no=%s',(datetime.datetime.fromtimestamp(expires,datetime.timezone.utc).replace(tzinfo=None),no))
        start=time.monotonic();probe(args,directory,'flashflow-timeouts',{'orderNo':no,'expiresAt':int(expires*1000)},level=2,key='actual-delay')
        eventually(lambda:scalar('SELECT status FROM orders WHERE order_no=%s',(no,))=='CLOSED',30)
        observed=time.monotonic()-start;assert scalar('SELECT available_stock FROM ticket_sku WHERE id=%s',(delayed_sku,))==1
        report['scenarios']['real_delayed_close']={'delay_level':2,'nominal_delay_s':5,'scheduling_enabled':False,'closed_after_probe_start_s':observed,'order_no':no,'db_stock':1,'redis_release':'pending until maintenance restart'}
        worker.stop();worker.start();eventually(lambda:scalar("SELECT COUNT(*) FROM stock_release_outbox WHERE reservation_id=%s AND status='DONE'",(received['reservationId'],))==1)
        after,rows=audit(delayed_sku);report['scenarios']['real_delayed_close']['final_audit']=after
        dump(directory/'faults.json',report);print(json.dumps(report,ensure_ascii=False,indent=2),flush=True)
    finally:
        dump(directory/'faults.json',report)
        if worker.pid:dump(directory/'native-worker.json',{'pid':worker.pid})
    print('Evidence:',directory,flush=True)

if __name__=='__main__':main()
