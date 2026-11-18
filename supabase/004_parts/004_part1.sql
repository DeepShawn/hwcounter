-- 004 安全加固 第 1/3 段（按顺序运行 1 → 2 → 3）
create schema if not exists hw_private;
revoke all on schema hw_private from public, anon;
grant usage on schema hw_private to authenticated;
revoke all on public.hw_snapshots, public.hw_snapshot_history, public.hw_invites, public.hw_members, public.hw_prices,
public.hw_positions, public.hw_orders, public.hw_trades, public.hw_cash_log from anon, authenticated;
grant select on public.hw_snapshots to anon, authenticated;
grant select on public.hw_snapshot_history, public.hw_invites, public.hw_members, public.hw_prices,
public.hw_positions, public.hw_orders, public.hw_trades, public.hw_cash_log to authenticated;
grant update (public_read) on public.hw_snapshots to authenticated;
drop policy if exists "hw_snapshots_delete_own" on public.hw_snapshots;
drop policy if exists "hw_snapshots_insert_own" on public.hw_snapshots;
drop policy if exists "hw_history_delete_own" on public.hw_snapshot_history;
create table if not exists hw_private.rate (
user_id uuid not null,
kind text not null,
win timestamptz not null,
n int not null,
primary key (user_id, kind)
);
alter table hw_private.rate enable row level security;
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
create table if not exists hw_private.audit (
id bigint generated always as identity primary key,
user_id uuid,
action text not null,
detail jsonb,
at timestamptz not null default now()
);
create index if not exists hw_audit_user_idx on hw_private.audit (user_id, at desc);
alter table hw_private.audit enable row level security;
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
