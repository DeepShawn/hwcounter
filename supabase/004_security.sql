-- 作业通 · 数据库安全加固（只改 hw_ 开头的对象，不影响同一项目里的其它应用）
-- 在 Supabase 控制台 → SQL Editor 中整段运行一次即可；可以重复运行。
--
-- 防的是什么：
-- 1. 账号被盗 / 设备被人拿到后删光数据：客户端不能再删除云端数据和历史版本；删除前也会自动存进历史。
-- 2. 刷版本把好的历史挤掉：历史版本 1 分钟内最多存一份，保留“最近 50 份 + 最近 90 天每天最后一份”，
--    就算有人连续上传几百次垃圾数据，旧的好版本也还在。
-- 3. 刷接口：上传、下单、发布股价、生成邀请码、发放模拟币都有频率上限；上传数据最大 5 MB。
-- 4. 暴力猜邀请码：注册失败按 IP 计数，同一 IP 1 小时失败 10 次、全站 10 分钟失败 100 次就暂停注册（配合 Edge Function hw-register）。
-- 5. 权限收紧：anon / authenticated 对 hw_ 表只保留必需的权限（原来连 TRUNCATE 都有）；数据只能通过检查过的函数写入。
-- 6. 审计：上传时班级 / 学生 / 作业明显变少会记一条审计日志，方便事后追查。

create schema if not exists hw_private;
revoke all on schema hw_private from public, anon;
grant usage on schema hw_private to authenticated;  -- RLS 策略里要调用 hw_private.my_admin() 等

-- ---------------------------------------------------------------- 1. 最小权限
revoke all on public.hw_snapshots, public.hw_snapshot_history, public.hw_invites, public.hw_members, public.hw_prices,
  public.hw_positions, public.hw_orders, public.hw_trades, public.hw_cash_log from anon, authenticated;

-- 读：按各表原有的 RLS 策略过滤
grant select on public.hw_snapshots to anon, authenticated;            -- anon 只能看到打开了“允许未登录预览”的
grant select on public.hw_snapshot_history, public.hw_invites, public.hw_members, public.hw_prices,
  public.hw_positions, public.hw_orders, public.hw_trades, public.hw_cash_log to authenticated;
-- 写：只允许直接改“允许未登录预览”这一列；数据本身只能通过 hw_push 上传
grant update (public_read) on public.hw_snapshots to authenticated;

-- 客户端不能删除数据和历史版本
drop policy if exists "hw_snapshots_delete_own" on public.hw_snapshots;
drop policy if exists "hw_snapshots_insert_own" on public.hw_snapshots;
drop policy if exists "hw_history_delete_own" on public.hw_snapshot_history;

-- ---------------------------------------------------------------- 2. 频率限制
create table if not exists hw_private.rate (
  user_id uuid not null,
  kind text not null,
  win timestamptz not null,
  n int not null,
  primary key (user_id, kind)
);
alter table hw_private.rate enable row level security;

-- 固定窗口计数：同一用户同一类操作在 p_window 内超过 p_max 次就报错（报错会回滚这次计数）
create or replace function hw_private.hit(p_kind text, p_max int, p_window interval)
returns void language plpgsql security definer set search_path = '' as $$
declare uid uuid := auth.uid(); c int;
begin
  if uid is null then return; end if;
  insert into hw_private.rate as r (user_id, kind, win, n) values (uid, p_kind, now(), 1)
  on conflict (user_id, kind) do update set
    n = case when r.win < now() - p_window then 1 else r.n + 1 end,
    win = case when r.win < now() - p_window then now() else r.win end
  returning r.n into c;
  if c > p_max then raise exception '操作太频繁，请稍后再试' using errcode = 'P0001'; end if;
end $$;
revoke all on function hw_private.hit(text, int, interval) from public, anon, authenticated;

-- ---------------------------------------------------------------- 3. 审计日志
create table if not exists hw_private.audit (
  id bigint generated always as identity primary key,
  user_id uuid,
  action text not null,
  detail jsonb,
  at timestamptz not null default now()
);
create index if not exists hw_audit_user_idx on hw_private.audit (user_id, at desc);
alter table hw_private.audit enable row level security;

-- 数据规模：班级数、学生数、作业数
create or replace function hw_private.data_size(d jsonb)
returns jsonb language sql immutable set search_path = '' as $$
  select jsonb_build_object(
    'classes', coalesce(jsonb_array_length(case when jsonb_typeof(d->'classes') = 'array' then d->'classes' end), 0),
    'students', coalesce((select sum(case when jsonb_typeof(c->'students') = 'array' then jsonb_array_length(c->'students') else 0 end)
                          from jsonb_array_elements(case when jsonb_typeof(d->'classes') = 'array' then d->'classes' else '[]'::jsonb end) c), 0),
    'assignments', coalesce((select sum(case when jsonb_typeof(c->'assignments') = 'array' then jsonb_array_length(c->'assignments') else 0 end)
                             from jsonb_array_elements(case when jsonb_typeof(d->'classes') = 'array' then d->'classes' else '[]'::jsonb end) c), 0)
  )
$$;
revoke all on function hw_private.data_size(jsonb) from public, anon, authenticated;

-- ---------------------------------------------------------------- 4. 历史版本：防刷、不能删
create or replace function hw_private.archive_snapshot()
returns trigger language plpgsql security definer set search_path = '' as $$
declare last timestamptz;
begin
  -- 只改了“允许未登录预览”开关：数据没变，不存历史
  if tg_op = 'UPDATE' and new.data is not distinct from old.data then return new; end if;
  -- 删除账号时级联删除：账号已经不在了，不再存历史（否则外键会挡住删除账号）
  if tg_op = 'DELETE' and not exists (select 1 from auth.users u where u.id = old.user_id) then return old; end if;
  select max(h.saved_at) into last from public.hw_snapshot_history h where h.user_id = old.user_id;
  -- 1 分钟内最多存一份（删除前一定存）
  if tg_op = 'DELETE' or last is null or last < old.updated_at - interval '1 minute' then
    insert into public.hw_snapshot_history (user_id, version, data, device, saved_at)
    values (old.user_id, old.version, old.data, old.device, old.updated_at);
  end if;
  -- 保留：最近 50 份 + 最近 90 天里每天最后一份
  delete from public.hw_snapshot_history h
  where h.user_id = old.user_id
    and h.id not in (
      select h2.id from public.hw_snapshot_history h2 where h2.user_id = old.user_id
      order by h2.saved_at desc, h2.id desc limit 50)
    and h.id not in (
      select distinct on (date_trunc('day', h3.saved_at)) h3.id from public.hw_snapshot_history h3
      where h3.user_id = old.user_id and h3.saved_at > now() - interval '90 days'
      order by date_trunc('day', h3.saved_at), h3.saved_at desc, h3.id desc);
  if tg_op = 'DELETE' then return old; end if;
  return new;
end $$;

drop trigger if exists hw_snapshots_archive on public.hw_snapshots;
create trigger hw_snapshots_archive
  before update or delete on public.hw_snapshots
  for each row execute function hw_private.archive_snapshot();

-- ---------------------------------------------------------------- 5. 上传：校验、限流、审计
create or replace function public.hw_push(p_data jsonb, p_base_version bigint, p_device text)
returns table (version bigint, updated_at timestamptz)
language plpgsql security definer set search_path = '' as $$
declare
  uid uuid := auth.uid();
  cur bigint;
  old_data jsonb;
  a jsonb; b jsonb;
begin
  if uid is null then raise exception 'NOT_AUTHENTICATED' using errcode = '28000'; end if;
  if hw_private.is_member() then raise exception '普通用户只能查看，不能上传数据'; end if;
  if p_data is null or jsonb_typeof(p_data) <> 'object'
     or (p_data ? 'classes' and jsonb_typeof(p_data->'classes') <> 'array') then
    raise exception '数据格式不对';
  end if;
  if octet_length(p_data::text) > 5 * 1024 * 1024 then raise exception '数据太大（超过 5 MB），请先清理不需要的记录'; end if;
  perform hw_private.hit('push', 200, interval '10 minutes');
  p_device := left(coalesce(p_device, ''), 80);

  select s.version, s.data into cur, old_data from public.hw_snapshots s where s.user_id = uid for update;
  if cur is null then
    insert into public.hw_snapshots (user_id, data, version, device, updated_at)
    values (uid, p_data, 1, p_device, now());
    return query select 1::bigint, now();
    return;
  elsif p_base_version <> -1 and p_base_version is distinct from cur then
    raise exception 'CONFLICT:%', cur using errcode = 'P0001';
  end if;

  -- 数据明显变少（班级变少，或学生 / 作业少了一半以上）：记审计日志（上一版本已自动进历史，可恢复）
  a := hw_private.data_size(old_data); b := hw_private.data_size(p_data);
  if (b->>'classes')::int < (a->>'classes')::int
     or (b->>'students')::int * 2 < (a->>'students')::int
     or (b->>'assignments')::int * 2 < (a->>'assignments')::int then
    insert into hw_private.audit (user_id, action, detail)
    values (uid, 'push_shrink', jsonb_build_object('from', a, 'to', b, 'version', cur, 'device', p_device, 'force', p_base_version = -1));
  end if;

  update public.hw_snapshots s
     set data = p_data, version = cur + 1, device = p_device, updated_at = now()
   where s.user_id = uid;
  return query select cur + 1, now();
end $$;
revoke all on function public.hw_push(jsonb, bigint, text) from public, anon;
grant execute on function public.hw_push(jsonb, bigint, text) to authenticated;

-- ---------------------------------------------------------------- 6. 其它写操作的频率上限
-- 用写入前触发器计数（不用改原来的函数）：下单每分钟最多 30 次；生成邀请码每小时最多 200 个；发放 / 收回模拟币每 10 分钟最多 100 次
create or replace function hw_private.limit_orders()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  perform hw_private.hit('order', 30, interval '1 minute');
  return new;
end $$;
drop trigger if exists hw_orders_rate on public.hw_orders;
create trigger hw_orders_rate before insert on public.hw_orders for each row execute function hw_private.limit_orders();

create or replace function hw_private.limit_invites()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  -- 每小时最多生成 200 个邀请码
  perform hw_private.hit('invite', 200, interval '1 hour');
  return new;
end $$;
drop trigger if exists hw_invites_rate on public.hw_invites;
create trigger hw_invites_rate before insert on public.hw_invites for each row execute function hw_private.limit_invites();

create or replace function hw_private.limit_cash()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  -- 发放 / 收回模拟币：每 10 分钟最多 100 次（成交写 hw_trades，不经过这里）
  if new.note is distinct from '注册赠送' then perform hw_private.hit('cash', 100, interval '10 minutes'); end if;
  return new;
end $$;
drop trigger if exists hw_cash_rate on public.hw_cash_log;
create trigger hw_cash_rate before insert on public.hw_cash_log for each row execute function hw_private.limit_cash();

-- ---------------------------------------------------------------- 7. 防暴力猜邀请码（Edge Function hw-register 调用，只有 service_role 能用）
create table if not exists hw_private.register_attempts (
  id bigint generated always as identity primary key,
  ip text not null,
  ok boolean not null,
  at timestamptz not null default now()
);
create index if not exists hw_register_attempts_ip_idx on hw_private.register_attempts (ip, at desc);
create index if not exists hw_register_attempts_at_idx on hw_private.register_attempts (at desc);
alter table hw_private.register_attempts enable row level security;

-- p_ok 为空：只检查是否被暂停；否则记一次结果。返回 true 表示允许继续
create or replace function public.hw_register_guard(p_ip text, p_ok boolean default null)
returns boolean language plpgsql security definer set search_path = '' as $$
declare v_ip text := left(coalesce(nullif(p_ip, ''), 'unknown'), 64);
begin
  if p_ok is not null then
    insert into hw_private.register_attempts (ip, ok) values (v_ip, p_ok);
    delete from hw_private.register_attempts r where r.at < now() - interval '7 days';
    return true;
  end if;
  if (select count(*) from hw_private.register_attempts a where a.ip = v_ip and not a.ok and a.at > now() - interval '1 hour') >= 10 then return false; end if;
  if (select count(*) from hw_private.register_attempts a where not a.ok and a.at > now() - interval '10 minutes') >= 100 then return false; end if;
  return true;
end $$;
revoke all on function public.hw_register_guard(text, boolean) from public, anon, authenticated;
grant execute on function public.hw_register_guard(text, boolean) to service_role;

-- ---------------------------------------------------------------- 8. 其它函数：不给 anon 执行
revoke execute on function public.hw_me(), public.hw_create_invites(text, numeric, int), public.hw_revoke_invite(text),
  public.hw_set_member_status(uuid, text), public.hw_rename_member(uuid, text), public.hw_grant(uuid, numeric, text),
  public.hw_remove_member(uuid), public.hw_place_order(text, text, bigint, numeric), public.hw_cancel_order(bigint),
  public.hw_publish_prices(jsonb), public.hw_leaderboard() from public, anon;
revoke execute on function hw_private.my_admin(), hw_private.is_member() from public, anon;
grant execute on function hw_private.my_admin(), hw_private.is_member() to authenticated;
