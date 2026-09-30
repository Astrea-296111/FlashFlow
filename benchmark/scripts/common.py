import base64, csv, hashlib, hmac, json, os, pathlib, time, urllib.request
import pymysql

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASE_URL = os.environ.get('BASE_URL', 'http://localhost:8080')
def database():
    return pymysql.connect(host=os.environ.get('DB_HOST','localhost'), port=int(os.environ.get('DB_PORT','3306')),
        user=os.environ.get('DB_USER','flashflow'), password=os.environ.get('DB_PASSWORD','local-development-only'),
        database=os.environ.get('DB_NAME','flashflow'), autocommit=True, init_command="SET time_zone='+00:00'", cursorclass=pymysql.cursors.DictCursor)
def jwt(uid,role='USER'):
    secret=os.environ['JWT_SECRET'];now=int(time.time())
    enc=lambda x:base64.urlsafe_b64encode(json.dumps(x,separators=(',',':')).encode()).rstrip(b'=')
    body=enc({'alg':'HS256','typ':'JWT'})+b'.'+enc({'iss':'flashflow','sub':str(uid),'iat':now,'exp':now+7200,'roles':[role]})
    return (body+b'.'+base64.urlsafe_b64encode(hmac.new(secret.encode(),body,hashlib.sha256).digest()).rstrip(b'=')).decode()
def request(path,method='GET',data=None,token=None):
    headers={}
    if token:headers['Authorization']='Bearer '+token
    if data is not None:headers['Content-Type']='application/json'
    r=urllib.request.Request(BASE_URL+path,data=json.dumps(data).encode() if data is not None else None,method=method,headers=headers)
    with urllib.request.urlopen(r,timeout=10) as response:
        body=response.read();return json.loads(body) if body else None
def dump(path,data):
    pathlib.Path(path).write_text(json.dumps(data,ensure_ascii=False,indent=2,default=str)+'\n')
def seed(n):
    # Local fixture users have unusable password hashes; authentication uses signed test JWTs.
    # The signing secret and tokens never enter evidence or version control.
    with database() as db,db.cursor() as c:
        c.executemany("INSERT INTO app_user(username,password_hash,role) VALUES(%s,'benchmark-login-disabled','USER') ON DUPLICATE KEY UPDATE username=VALUES(username)",[(f'bench-user-{i}',) for i in range(n)])
        c.execute("SELECT id,username FROM app_user WHERE username LIKE 'bench-user-%%'")
        rows=c.fetchall();rows.sort(key=lambda r:int(r['username'].split('-')[-1]));rows=rows[:n]
        c.execute("SELECT id FROM app_user WHERE username='admin' AND role='ADMIN'");admin=c.fetchone()
    if not admin:raise RuntimeError('Start API with DEMO_ENABLED=true first')
    return [{'id':r['id'],'token':jwt(r['id'])} for r in rows],jwt(admin['id'],'ADMIN')
def fixture(admin,stock,mode,label):
    now=time.time()
    iso=lambda t:__import__('datetime').datetime.fromtimestamp(t,__import__('datetime').timezone.utc).isoformat()
    event=request('/api/v1/admin/events','POST',{'name':label,'venue':'Benchmark fixture','saleStartAt':iso(now-60),'saleEndAt':iso(now+3600)},admin)['eventId']
    sku=request(f'/api/v1/admin/events/{event}/skus','POST',{'tierName':mode,'price':99,'stock':stock},admin)['skuId']
    if mode=='optimized':request(f'/api/v1/admin/skus/{sku}/warm','POST',token=admin)
    return event,sku
def wait_ready(timeout=90):
    start=time.monotonic()
    while time.monotonic()-start<timeout:
        try:
            if request('/actuator/health')['status']=='UP':return
        except Exception:pass
        time.sleep(.25)
    raise RuntimeError('API failed to become healthy')
def audit(sku):
    with database() as db,db.cursor() as c:
        c.execute('SELECT total_stock,available_stock FROM ticket_sku WHERE id=%s',(sku,));inventory=c.fetchone()
        c.execute("SELECT order_no,reservation_id,user_id,status,created_at,expire_at FROM orders WHERE sku_id=%s ORDER BY id",(sku,));rows=c.fetchall()
        c.execute('SELECT user_id,COUNT(*) AS n FROM orders WHERE sku_id=%s GROUP BY user_id HAVING COUNT(*)>1',(sku,));duplicates=c.fetchall()
    active=sum(r['status'] not in ('CLOSED','REFUNDED') for r in rows)
    report={'sku_id':sku,**inventory,'orders':len(rows),'active_orders':active,'oversell':max(0,active-inventory['total_stock']),
        'duplicate_orders':sum(r['n']-1 for r in duplicates),'conservation':inventory['available_stock']+active==inventory['total_stock'],'db_nonnegative':inventory['available_stock']>=0}
    return report,rows
