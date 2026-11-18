-- 004 安全加固 第 2/3 段（按顺序运行 1 → 2 → 3）
create or replace function hw_private.archive_snapshot()
returns trigger language plpgsql security definer set search_path = '' as $$
declare last timestamptz;
begin
if tg_op = 'UPDATE' and new.data is not distinct from old.data then return new; end if;
if tg_op = 'DELETE' and not exists (select 1 from auth.users u where u.id = old.user_id) then return old; end if;
select max(h.saved_at) into last from public.hw_snapshot_history h where h.user_id = old.user_id;
if tg_op = 'DELETE' or last is null or last < old.updated_at - interval '1 minute' then
insert into public.hw_snapshot_history (user_id, version, data, device, saved_at)
values (old.user_id, old.version, old.data, old.device, old.updated_at);
end if;
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
