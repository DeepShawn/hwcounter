\set ON_ERROR_STOP 0
\pset tuples_only on
-- 准备：老师 T、普通用户 M、陌生人 X
insert into auth.users values ('11111111-1111-1111-1111-111111111111','t@x'),('22222222-2222-2222-2222-222222222222','m@x'),('33333333-3333-3333-3333-333333333333','x@x') on conflict do nothing;
insert into public.hw_members (user_id, admin_id, username, cash) values ('22222222-2222-2222-2222-222222222222','11111111-1111-1111-1111-111111111111','mem',10000) on conflict do nothing;
create or replace function pg_temp.as_user(u text, r text) returns void language sql as $$ select set_config('request.jwt.claims', json_build_object('sub',u,'role',r)::text, false), set_config('role', r, false) $$;
create table if not exists public.t_res(k text, v text); grant all on public.t_res to anon, authenticated; truncate public.t_res;
-- 老师：首次上传、再上传、改预览开关、发布股价、生成邀请码、发币
select pg_temp.as_user('11111111-1111-1111-1111-111111111111','authenticated');
do $t$ declare v bigint; d jsonb := '{"classes":[{"id":"c1","students":[{"id":"s1"},{"id":"s2"},{"id":"s3"},{"id":"s4"}],"assignments":[{"id":"a1"},{"id":"a2"}]}]}'; n int;
begin
  select version into v from public.hw_push(d, 0, 'phone'); insert into public.t_res values ('T push first', v::text);
  select version into v from public.hw_push(d || '{"x":1}', 1, 'phone'); insert into public.t_res values ('T push 2', v::text);
  begin perform public.hw_push(d, 1, 'phone'); insert into public.t_res values ('T stale base -> conflict', 'NO CONFLICT(bad)'); exception when others then insert into public.t_res values ('T stale base -> conflict', sqlerrm); end;
  update public.hw_snapshots set public_read = true where user_id = auth.uid(); get diagnostics n = row_count; insert into public.t_res values ('T toggle preview', n || ' row');
  begin perform public.hw_publish_prices('[{"symbol":"C:s1","name":"甲","kind":"C","price":80}]'::jsonb); insert into public.t_res values ('T publish prices', 'ok'); exception when others then insert into public.t_res values ('T publish prices', 'ERR '||sqlerrm); end;
  begin perform public.hw_create_invites('n', 100, 2); insert into public.t_res values ('T create invites', 'ok'); exception when others then insert into public.t_res values ('T create invites', 'ERR '||sqlerrm); end;
  begin perform public.hw_grant('22222222-2222-2222-2222-222222222222', 50, 'bonus'); insert into public.t_res values ('T grant cash', 'ok'); exception when others then insert into public.t_res values ('T grant cash', 'ERR '||sqlerrm); end;
  insert into public.t_res values ('T read history', (select count(*)::text from public.hw_snapshot_history));
  -- 攻击（账号被盗）
  begin update public.hw_snapshots set data = '{}' where user_id = auth.uid(); insert into public.t_res values ('ATTACK direct overwrite', 'ALLOWED(bad)'); exception when others then insert into public.t_res values ('ATTACK direct overwrite', 'blocked'); end;
  begin delete from public.hw_snapshot_history; get diagnostics n = row_count; insert into public.t_res values ('ATTACK delete history', case when n=0 then 'blocked(0 rows)' else n||' DELETED(bad)' end); exception when others then insert into public.t_res values ('ATTACK delete history', 'blocked'); end;
  begin delete from public.hw_snapshots; get diagnostics n = row_count; insert into public.t_res values ('ATTACK delete snapshot', case when n=0 then 'blocked(0 rows)' else n||' DELETED(bad)' end); exception when others then insert into public.t_res values ('ATTACK delete snapshot', 'blocked'); end;
  begin execute 'truncate public.hw_snapshot_history'; insert into public.t_res values ('ATTACK truncate', 'ALLOWED(bad)'); exception when others then insert into public.t_res values ('ATTACK truncate', 'blocked'); end;
  begin insert into public.hw_cash_log (user_id, admin_id, amount, note, cash_after) values (auth.uid(), auth.uid(), 1e9, 'x', 1e9); insert into public.t_res values ('ATTACK forge cash log', 'ALLOWED(bad)'); exception when others then insert into public.t_res values ('ATTACK forge cash log', 'blocked'); end;
  begin perform public.hw_push('[1]'::jsonb, -1, 'x'); insert into public.t_res values ('ATTACK non-object', 'ALLOWED(bad)'); exception when others then insert into public.t_res values ('ATTACK non-object', 'blocked: '||sqlerrm); end;
  begin perform public.hw_push(jsonb_build_object('classes', jsonb_build_array(jsonb_build_object('x', repeat('a', 6000000)))), -1, 'x'); insert into public.t_res values ('ATTACK 6MB', 'ALLOWED(bad)'); exception when others then insert into public.t_res values ('ATTACK 6MB', 'blocked: '||sqlerrm); end;
  begin perform public.hw_push('{"classes":[]}'::jsonb, -1, 'wipe'); insert into public.t_res values ('wipe push (allowed, audited)', 'ok'); exception when others then insert into public.t_res values ('wipe push', 'ERR '||sqlerrm); end;
  begin for i in 1..205 loop perform public.hw_push(d, -1, 'flood'); end loop; insert into public.t_res values ('ATTACK flood 205 pushes', 'NOT LIMITED(bad)'); exception when others then insert into public.t_res values ('ATTACK flood 205 pushes', 'blocked: '||sqlerrm); end;
  begin perform public.hw_register_guard('1.1.1.1'); insert into public.t_res values ('ATTACK call register guard', 'ALLOWED(bad)'); exception when others then insert into public.t_res values ('ATTACK call register guard', 'blocked'); end;
end $t$;
reset role;
insert into public.t_res values ('audit rows (wipe)', (select count(*)::text from hw_private.audit where action='push_shrink'));
insert into public.t_res values ('history rows (coalesced, 1/min)', (select count(*)::text from public.hw_snapshot_history));
-- 普通用户：能读老师数据、能下单；不能上传、不能改预览开关
select pg_temp.as_user('22222222-2222-2222-2222-222222222222','authenticated');
do $t$ declare n int; r jsonb;
begin
  insert into public.t_res values ('M read teacher data', (select count(*)::text from public.hw_snapshots));
  begin r := public.hw_place_order('C:s1','buy',2,null); insert into public.t_res values ('M place order', r->>'status'); exception when others then insert into public.t_res values ('M place order', 'ERR '||sqlerrm); end;
  begin perform public.hw_push('{"classes":[]}'::jsonb, -1, 'x'); insert into public.t_res values ('ATTACK member push', 'ALLOWED(bad)'); exception when others then insert into public.t_res values ('ATTACK member push', 'blocked: '||sqlerrm); end;
  update public.hw_snapshots set public_read = false; get diagnostics n = row_count; insert into public.t_res values ('ATTACK member toggle teacher preview', case when n=0 then 'blocked(0 rows)' else n||' rows(bad)' end);
  begin update public.hw_members set cash = 1e9 where user_id = auth.uid(); insert into public.t_res values ('ATTACK member set own cash', 'ALLOWED(bad)'); exception when others then insert into public.t_res values ('ATTACK member set own cash', 'blocked'); end;
  begin for i in 1..35 loop perform public.hw_place_order('C:s1','buy',1,null); end loop; insert into public.t_res values ('ATTACK order flood 35/min', 'NOT LIMITED(bad)'); exception when others then insert into public.t_res values ('ATTACK order flood 35/min', 'blocked: '||sqlerrm); end;
end $t$;
reset role;
-- 陌生人（登录了别的账号）和未登录访客
select pg_temp.as_user('33333333-3333-3333-3333-333333333333','authenticated');
do $t$ declare n int; begin
  insert into public.t_res values ('X read history of others', (select count(*)::text from public.hw_snapshot_history));
  update public.hw_snapshots set public_read = false; get diagnostics n = row_count; insert into public.t_res values ('ATTACK stranger toggle', case when n=0 then 'blocked(0 rows)' else n||' rows(bad)' end);
end $t$;
reset role;
select pg_temp.as_user('','anon');
do $t$ declare n int; begin
  insert into public.t_res values ('anon read public preview', (select count(*)::text from public.hw_snapshots));
  begin perform public.hw_push('{}'::jsonb, -1, 'x'); insert into public.t_res values ('ATTACK anon push', 'ALLOWED(bad)'); exception when others then insert into public.t_res values ('ATTACK anon push', 'blocked'); end;
  begin perform count(*) from public.hw_snapshot_history; insert into public.t_res values ('ATTACK anon history', 'ALLOWED(bad)'); exception when others then insert into public.t_res values ('ATTACK anon history', 'blocked'); end;
  begin perform public.hw_me(); insert into public.t_res values ('ATTACK anon hw_me', 'ALLOWED(bad)'); exception when others then insert into public.t_res values ('ATTACK anon hw_me', 'blocked'); end;
end $t$;
reset role;
-- 注册守卫（service_role）：同一 IP 失败 10 次后挡住
set role service_role;
select public.hw_register_guard('9.9.9.9', false) from generate_series(1,10);
insert into public.t_res values ('guard after 10 fails same IP', (select public.hw_register_guard('9.9.9.9')::text));
insert into public.t_res values ('guard other IP', (select public.hw_register_guard('8.8.8.8')::text));
reset role;
-- 删除账号不被历史外键挡住
delete from auth.users where id = '11111111-1111-1111-1111-111111111111';
insert into public.t_res values ('delete teacher account', (select case when count(*)=0 then 'ok' else 'stuck' end from auth.users where id='11111111-1111-1111-1111-111111111111'));
select k || ' = ' || v from public.t_res;
