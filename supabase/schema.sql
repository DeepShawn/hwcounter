-- 作业通 · Supabase 云端同步表结构
-- 在 Supabase 控制台 → SQL Editor 中整段粘贴运行即可（项目：hwcounter）

-- 每位老师一份全量数据快照（与 App 本地 JSON 同格式，不含 AI API Key）
create table if not exists public.hw_snapshots (
  user_id uuid primary key default auth.uid() references auth.users(id) on delete cascade,
  data jsonb not null,
  version bigint not null default 1,
  device text,
  updated_at timestamptz not null default now()
);
alter table public.hw_snapshots enable row level security;

create policy "hw_snapshots_select_own" on public.hw_snapshots
  for select to authenticated using ((select auth.uid()) = user_id);
create policy "hw_snapshots_insert_own" on public.hw_snapshots
  for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "hw_snapshots_update_own" on public.hw_snapshots
  for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "hw_snapshots_delete_own" on public.hw_snapshots
  for delete to authenticated using ((select auth.uid()) = user_id);

-- 历史版本：每次覆盖前自动备份，最多保留 30 份
create table if not exists public.hw_snapshot_history (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  version bigint not null,
  data jsonb not null,
  device text,
  saved_at timestamptz not null default now()
);
create index if not exists hw_snapshot_history_user_saved_idx on public.hw_snapshot_history (user_id, saved_at desc);
alter table public.hw_snapshot_history enable row level security;

create policy "hw_history_select_own" on public.hw_snapshot_history
  for select to authenticated using ((select auth.uid()) = user_id);
create policy "hw_history_delete_own" on public.hw_snapshot_history
  for delete to authenticated using ((select auth.uid()) = user_id);

create schema if not exists hw_private;

create or replace function hw_private.archive_snapshot()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.hw_snapshot_history (user_id, version, data, device, saved_at)
  values (old.user_id, old.version, old.data, old.device, old.updated_at);
  delete from public.hw_snapshot_history h
  where h.user_id = old.user_id
    and h.id not in (
      select h2.id from public.hw_snapshot_history h2
      where h2.user_id = old.user_id
      order by h2.saved_at desc, h2.id desc
      limit 30
    );
  return new;
end;
$$;

create trigger hw_snapshots_archive
  before update on public.hw_snapshots
  for each row execute function hw_private.archive_snapshot();

-- 乐观锁上传：p_base_version 必须等于云端当前版本，否则返回 CONFLICT；-1 表示强制覆盖
create or replace function public.hw_push(p_data jsonb, p_base_version bigint, p_device text)
returns table (version bigint, updated_at timestamptz)
language plpgsql
security invoker
set search_path = ''
as $$
declare
  uid uuid := auth.uid();
  cur bigint;
begin
  if uid is null then
    raise exception 'NOT_AUTHENTICATED' using errcode = '28000';
  end if;
  select s.version into cur from public.hw_snapshots s where s.user_id = uid for update;
  if cur is null then
    insert into public.hw_snapshots (user_id, data, version, device, updated_at)
    values (uid, p_data, 1, p_device, now());
    return query select 1::bigint, now();
  elsif p_base_version <> -1 and p_base_version is distinct from cur then
    raise exception 'CONFLICT:%', cur using errcode = 'P0001';
  else
    update public.hw_snapshots s
       set data = p_data, version = cur + 1, device = p_device, updated_at = now()
     where s.user_id = uid;
    return query select cur + 1, now();
  end if;
end;
$$;

grant select, insert, update, delete on public.hw_snapshots to authenticated;
grant select, delete on public.hw_snapshot_history to authenticated;
grant execute on function public.hw_push(jsonb, bigint, text) to authenticated;
