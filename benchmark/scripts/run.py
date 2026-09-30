#!/usr/bin/env python3
import argparse, csv, datetime, hashlib, json, os, pathlib, platform, re, statistics, subprocess, tempfile, threading, time
from common import *

def percentile(values,p):
    if not values:return None
    s=sorted(values);i=(len(s)-1)*p;lo=int(i);hi=min(lo+1,len(s)-1);return s[lo]+(s[hi]-s[lo])*(i-lo)
def observe(sku,seen,stop):
    with database() as db,db.cursor() as c:
        while not stop.is_set():
            c.execute('SELECT reservation_id FROM orders WHERE sku_id=%s',(sku,));at=time.time()*1000
            for r in c.fetchall():seen.setdefault(r['reservation_id'],at)
            stop.wait(.025)
def resource_samples(rows,stop):
    names=('process_cpu_usage','jvm_memory_used_bytes','hikaricp_connections_active','hikaricp_connections_pending','jvm_threads_live_threads','jvm_gc_pause_seconds_count')
    while not stop.is_set():
        row={'epoch_ms':time.time()*1000}
        for role,url in [('api',BASE_URL),('worker',os.environ.get('WORKER_URL','http://localhost:8081'))]:
            try:
                body=urllib.request.urlopen(url+'/actuator/prometheus',timeout=2).read().decode()
                values={name:[] for name in names}
                for line in body.splitlines():
                    if not line or line.startswith('#'):continue
                    name=re.split('[{ ]',line,1)[0]
                    if name in values and (name!='jvm_memory_used_bytes' or 'area="heap"' in line):values[name].append(float(line.rsplit(' ',1)[1]))
                row[role]={key:sum(value) for key,value in values.items() if value}
            except Exception as e:row[role]={'unavailable':type(e).__name__}
        rows.append(row);stop.wait(.5)
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--requests',type=int,default=3000);parser.add_argument('--v-us',type=int,default=40);parser.add_argument('--stock',type=int,default=100);parser.add_argument('--repeats',type=int,default=3);parser.add_argument('--label',default='paired');args=parser.parse_args()
    wait_ready();users,admin=seed(args.requests)
    directory=ROOT/'benchmark/results'/f'{datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")}-{args.label}';directory.mkdir(parents=True)
    env={'date':datetime.datetime.now(datetime.timezone.utc).isoformat(),'git_commit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'os':platform.platform(),'cpu':os.cpu_count(),'cpu_quota':pathlib.Path('/sys/fs/cgroup/cpu.max').read_text().strip() if pathlib.Path('/sys/fs/cgroup/cpu.max').exists() else None,'memory_limit':pathlib.Path('/sys/fs/cgroup/memory.max').read_text().strip() if pathlib.Path('/sys/fs/cgroup/memory.max').exists() else None,'stock':args.stock,'vus':args.v_us,'requests':args.requests,'repeats':args.repeats,'duration':'fixed iterations, at most 120s','api_instances':1,'worker_instances':1,'java':'21.0.12.1','spring_boot':'3.5.16','mysql':'8.4.9','redis':'7.4.2','rocketmq':'5.3.3','execution_backend':os.environ.get('EXECUTION_BACKEND','Docker Compose'),'k6':'1.3.0','limiter':'disabled for paired capacity experiment','completion_measurement':'load-generator request start to first externally visible committed order; SQL observer polls every 25ms for BOTH variants'}
    dump(directory/'environment.json',env)
    # Reusable tokens are kept in a private temporary file, never exported with results.
    with tempfile.TemporaryDirectory(prefix='flashflow-auth-') as private:
        auth=pathlib.Path(private)/'auth.json';dump(auth,{'users':users});auth.chmod(0o600)
        # Prime HTTP/JWT paths; excludes order stock and measured iterations.
        for _ in range(50):request('/api/v1/events')
        results=[]
        for repeat in range(args.repeats):
            for mode in (['baseline','optimized'] if repeat%2==0 else ['optimized','baseline']):
                event,sku=fixture(admin,args.stock,mode,f'{args.label}-{repeat+1}-{mode}')
                target=directory/f'{mode}-{repeat+1}';target.mkdir();seen={};resources=[];stop=threading.Event();observer=threading.Thread(target=observe,args=(sku,seen,stop),daemon=True);observer.start();sampler=threading.Thread(target=resource_samples,args=(resources,stop),daemon=True);sampler.start()
                case_env={**os.environ,'AUTH_FILE':str(auth),'REQUESTS':str(args.requests),'VUS':str(args.v_us),'MODE':mode,'SKU_ID':str(sku),'SUMMARY_FILE':str(target/'k6-summary.json'),'BASE_URL':BASE_URL}
                started=time.monotonic()
                with open(target/'k6.log','w') as log:
                    p=subprocess.run([os.environ.get('K6_BIN','k6'),'run','--log-format','raw','--console-output',str(target/'accepted.jsonl'),str(ROOT/'benchmark/k6/entry.js')],env=case_env,stdout=log,stderr=subprocess.STDOUT)
                load_finished_ms=time.time()*1000
                accepted=[]
                for line in (target/'accepted.jsonl').read_text().splitlines():
                    try:
                        item=json.loads(line[line.index('{'):]);
                        if 'rid' in item:accepted.append(item)
                    except (ValueError,json.JSONDecodeError):pass
                deadline=time.monotonic()+60
                while time.monotonic()<deadline and not all(r['rid'] in seen for r in accepted):time.sleep(.05)
                stop.set();observer.join(5);sampler.join(5);dump(target/'resource-samples.json',{'interval_s':.5,'note':'Actuator process_cpu_usage is a sampled recent CPU ratio, not instantaneous peak or host utilization; sampling and observer overhead apply equally to both variants.','samples':resources})
                report,orders=audit(sku);report['accepted_requests']=len(accepted);report['all_accepted_committed']=len(orders)==len(accepted);report['load_exit_code']=p.returncode
                # Redis is audited after convergence, through the application's bounded fixture inventory.
                if mode=='optimized':
                    import socket
                    with socket.create_connection((os.environ.get('REDIS_HOST','localhost'),int(os.environ.get('REDIS_PORT','6379'))),timeout=2) as conn:
                        key=f'flashflow:{{sku:{sku}}}:stock'.encode();conn.sendall(b'*2\r\n$3\r\nGET\r\n$'+str(len(key)).encode()+b'\r\n'+key+b'\r\n');raw=conn.recv(4096).split(b'\r\n');report['redis_stock']=int(raw[1]);report['redis_matches_db']=report['redis_stock']==report['available_stock']
                latencies=[seen[r['rid']]-r['start_ms'] for r in accepted if r['rid'] in seen]
                dump(target/'validation.json',report);dump(target/'completion.json',{'observations':[dict(r,observed_commit_ms=seen.get(r['rid']),completion_ms=seen.get(r['rid'],r['start_ms'])-r['start_ms']) for r in accepted],'p50_ms':percentile(latencies,.5),'p95_ms':percentile(latencies,.95),'p99_ms':percentile(latencies,.99),'poll_interval_ms':25})
                with open(target/'orders.csv','w') as f:
                    writer=csv.DictWriter(f,fieldnames=['order_no','reservation_id','user_id','status','created_at','expire_at']);writer.writeheader();writer.writerows(orders)
                (target/'prometheus-snapshot').mkdir();
                for name,url in [('api',BASE_URL),('worker',os.environ.get('WORKER_URL','http://localhost:8081'))]:
                    try:(target/'prometheus-snapshot'/f'{name}.prom').write_bytes(urllib.request.urlopen(url+'/actuator/prometheus',timeout=5).read())
                    except Exception as e:(target/'prometheus-snapshot'/f'{name}-unavailable.txt').write_text(type(e).__name__)
                summary=json.load(open(target/'k6-summary.json'));m=summary['metrics'];record={'mode':mode,'repeat':repeat+1,'rps':m['http_reqs']['values']['rate'],'request_count':m['http_reqs']['values']['count'],'p50_ms':m['http_req_duration']['values']['p(50)'],'p95_ms':m['http_req_duration']['values']['p(95)'],'p99_ms':m['http_req_duration']['values']['p(99)'],'unexpected_rate':m['http_req_failed']['values']['rate'],'accepted':len(accepted),'accepted_entry_p99_ms':m.get('accepted_entry_latency',{}).get('values',{}).get('p(99)'), 'completion_p95_ms':percentile(latencies,.95),'completion_p99_ms':percentile(latencies,.99),'post_load_convergence_s':max(0,(max(seen.values())-load_finished_ms)/1000) if seen else None,'audit':report}
                results.append(record);dump(directory/'comparison.json',results);print(json.dumps(record,ensure_ascii=False),flush=True)
                assert p.returncode==0 and len(accepted)==min(args.stock,args.requests) and report['conservation'] and report['db_nonnegative'] and report['oversell']==0 and report['duplicate_orders']==0 and report['all_accepted_committed'] and report.get('redis_matches_db',True),report
        (directory/'README.md').write_text('# Repeated paired burst experiment\n\nAll trials retained. Counterbalanced execution order; identical stock/users/VUs. RPS includes sold-out rejections and is not sustained successful-order TPS. Completion includes SQL observer overhead and its 25ms polling uncertainty. JWT fixtures excluded. See environment.json, comparison.json, per-run summaries, accepted timings, orders and invariant audits.\n')
        print('Evidence:',directory,flush=True)
if __name__=='__main__':main()
