-- 004 安全加固 第 3/3 段（按顺序运行 1 → 2 → 3）
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
perform hw_private.hit('invite', 200, interval '1 hour');
return new;
end $$;
drop trigger if exists hw_invites_rate on public.hw_invites;
create trigger hw_invites_rate before insert on public.hw_invites for each row execute function hw_private.limit_invites();
create or replace function hw_private.limit_cash()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
if new.note is distinct from '注册赠送' then perform hw_private.hit('cash', 100, interval '10 minutes'); end if;
return new;
end $$;
drop trigger if exists hw_cash_rate on public.hw_cash_log;
create trigger hw_cash_rate before insert on public.hw_cash_log for each row execute function hw_private.limit_cash();
create table if not exists hw_private.register_attempts (
id bigint generated always as identity primary key,
ip text not null,
ok boolean not null,
at timestamptz not null default now()
);
create index if not exists hw_register_attempts_ip_idx on hw_private.register_attempts (ip, at desc);
create index if not exists hw_register_attempts_at_idx on hw_private.register_attempts (at desc);
alter table hw_private.register_attempts enable row level security;
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
revoke execute on function public.hw_me(), public.hw_create_invites(text, numeric, int), public.hw_revoke_invite(text),
public.hw_set_member_status(uuid, text), public.hw_rename_member(uuid, text), public.hw_grant(uuid, numeric, text),
public.hw_remove_member(uuid), public.hw_place_order(text, text, bigint, numeric), public.hw_cancel_order(bigint),
public.hw_publish_prices(jsonb), public.hw_leaderboard() from public, anon;
revoke execute on function hw_private.my_admin(), hw_private.is_member() from public, anon;
grant execute on function hw_private.my_admin(), hw_private.is_member() to authenticated;
