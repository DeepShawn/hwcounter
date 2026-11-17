-- 作业通 · 邀请码账号与模拟炒股（信誉分 / 关注度）
-- 管理员 = 拥有班级数据（hw_snapshots）的老师账号；普通用户凭管理员给的一次性邀请码注册（Edge Function hw-register），
-- 注册后只读查看管理员的班级数据，用管理员发放的模拟币（与真实货币无关）买卖“股票”。
-- 股价由管理员的 App 按班级数据算出后发布（hw_publish_prices），所有成交都在数据库里按已发布的价格完成，客户端不能自定价格。
-- 股票代码：A:学生id（个人关注度）、C:学生id（个人信誉分）、GA:组名（小组关注度）、GC:组名（小组信誉分）。
-- 成交价 = max(发布价, 1)；做空 = 卖出超过持有的数量（持仓为负）；保证金规则：现金 − 2 × 空头市值 ≥ 0，
-- 不满足时只允许让它变好的交易（卖出多头、买入平空）。

create schema if not exists hw_private;

-- ---------- 表 ----------
create table if not exists public.hw_invites (
  code text primary key,
  admin_id uuid not null references auth.users(id) on delete cascade,
  note text not null default '',
  initial_cash numeric(14,2) not null default 10000 check (initial_cash >= 0),
  created_at timestamptz not null default now(),
  used_at timestamptz,
  used_by uuid references auth.users(id) on delete set null,
  revoked boolean not null default false
);
create index if not exists hw_invites_admin_idx on public.hw_invites (admin_id, created_at desc);
create index if not exists hw_invites_used_by_idx on public.hw_invites (used_by);

create table if not exists public.hw_members (
  user_id uuid primary key references auth.users(id) on delete cascade,
  admin_id uuid not null references auth.users(id) on delete cascade,
  username text not null unique,
  display_name text not null default '',
  invite_code text,
  status text not null default 'active' check (status in ('active', 'disabled')),
  cash numeric(14,2) not null default 0,
  created_at timestamptz not null default now()
);
create index if not exists hw_members_admin_idx on public.hw_members (admin_id);

create table if not exists public.hw_prices (
  admin_id uuid not null references auth.users(id) on delete cascade,
  symbol text not null,
  name text not null default '',
  kind text not null default '',
  price numeric(10,2) not null,
  active boolean not null default true,
  updated_at timestamptz not null default now(),
  primary key (admin_id, symbol)
);

create table if not exists public.hw_positions (
  user_id uuid not null references auth.users(id) on delete cascade,
  admin_id uuid not null references auth.users(id) on delete cascade,
  symbol text not null,
  qty bigint not null default 0,
  avg_price numeric(14,4) not null default 0,
  realized numeric(14,2) not null default 0,
  updated_at timestamptz not null default now(),
  primary key (user_id, symbol)
);
create index if not exists hw_positions_admin_idx on public.hw_positions (admin_id);

create table if not exists public.hw_orders (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  admin_id uuid not null references auth.users(id) on delete cascade,
  symbol text not null,
  side text not null check (side in ('buy', 'sell')),
  qty bigint not null check (qty > 0),
  limit_price numeric(10,2) check (limit_price is null or limit_price > 0),
  status text not null default 'open' check (status in ('open', 'filled', 'cancelled')),
  reason text not null default '',
  fill_price numeric(10,2),
  created_at timestamptz not null default now(),
  done_at timestamptz
);
create index if not exists hw_orders_user_idx on public.hw_orders (user_id, created_at desc);
create index if not exists hw_orders_open_idx on public.hw_orders (admin_id, symbol) where status = 'open';

create table if not exists public.hw_trades (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  admin_id uuid not null references auth.users(id) on delete cascade,
  symbol text not null,
  side text not null,
  qty bigint not null,
  price numeric(10,2) not null,
  amount numeric(14,2) not null,
  realized numeric(14,2) not null default 0,
  cash_after numeric(14,2) not null,
  order_id bigint references public.hw_orders(id) on delete set null,
  created_at timestamptz not null default now()
);
create index if not exists hw_trades_user_idx on public.hw_trades (user_id, created_at desc);
create index if not exists hw_trades_admin_idx on public.hw_trades (admin_id, created_at desc);
create index if not exists hw_trades_order_idx on public.hw_trades (order_id);

create table if not exists public.hw_cash_log (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  admin_id uuid not null references auth.users(id) on delete cascade,
  amount numeric(14,2) not null,
  note text not null default '',
  cash_after numeric(14,2) not null,
  created_at timestamptz not null default now()
);
create index if not exists hw_cash_log_user_idx on public.hw_cash_log (user_id, created_at desc);
create index if not exists hw_cash_log_admin_idx on public.hw_cash_log (admin_id);

alter table public.hw_invites enable row level security;
alter table public.hw_members enable row level security;
alter table public.hw_prices enable row level security;
alter table public.hw_positions enable row level security;
alter table public.hw_orders enable row level security;
alter table public.hw_trades enable row level security;
alter table public.hw_cash_log enable row level security;

-- ---------- 帮助函数 ----------
-- 当前登录的普通用户所属的管理员（停用的不算）；不是普通用户时为 null
create or replace function hw_private.my_admin()
returns uuid language sql stable security definer set search_path = '' as $$
  select m.admin_id from public.hw_members m where m.user_id = (select auth.uid()) and m.status = 'active'
$$;

-- 当前登录的是不是普通用户（含停用的）
create or replace function hw_private.is_member()
returns boolean language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.hw_members m where m.user_id = (select auth.uid()))
$$;

grant usage on schema hw_private to authenticated;
grant execute on function hw_private.my_admin() to authenticated;
grant execute on function hw_private.is_member() to authenticated;

-- ---------- 读取权限 ----------
-- 普通用户能只读查看自己管理员的班级数据
drop policy if exists "hw_snapshots_member_read" on public.hw_snapshots;
create policy "hw_snapshots_member_read" on public.hw_snapshots
  for select to authenticated using (user_id = (select hw_private.my_admin()));

drop policy if exists "hw_invites_admin_read" on public.hw_invites;
create policy "hw_invites_admin_read" on public.hw_invites
  for select to authenticated using (admin_id = (select auth.uid()));

drop policy if exists "hw_members_read" on public.hw_members;
create policy "hw_members_read" on public.hw_members
  for select to authenticated using (user_id = (select auth.uid()) or admin_id = (select auth.uid()));

drop policy if exists "hw_prices_read" on public.hw_prices;
create policy "hw_prices_read" on public.hw_prices
  for select to authenticated using (admin_id = (select auth.uid()) or admin_id = (select hw_private.my_admin()));

drop policy if exists "hw_positions_read" on public.hw_positions;
create policy "hw_positions_read" on public.hw_positions
  for select to authenticated using (user_id = (select auth.uid()) or admin_id = (select auth.uid()));

drop policy if exists "hw_orders_read" on public.hw_orders;
create policy "hw_orders_read" on public.hw_orders
  for select to authenticated using (user_id = (select auth.uid()) or admin_id = (select auth.uid()));

drop policy if exists "hw_trades_read" on public.hw_trades;
create policy "hw_trades_read" on public.hw_trades
  for select to authenticated using (user_id = (select auth.uid()) or admin_id = (select auth.uid()));

drop policy if exists "hw_cash_log_read" on public.hw_cash_log;
create policy "hw_cash_log_read" on public.hw_cash_log
  for select to authenticated using (user_id = (select auth.uid()) or admin_id = (select auth.uid()));

grant select on public.hw_invites, public.hw_members, public.hw_prices, public.hw_positions, public.hw_orders, public.hw_trades, public.hw_cash_log to authenticated;

-- ---------- 身份 ----------
-- 当前账号：管理员（老师）或普通用户
create or replace function public.hw_me()
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare uid uuid := auth.uid(); m public.hw_members;
begin
  if uid is null then raise exception 'NOT_AUTHENTICATED' using errcode = '28000'; end if;
  select * into m from public.hw_members where user_id = uid;
  if m.user_id is null then
    return jsonb_build_object('role', 'admin', 'user_id', uid);
  end if;
  return jsonb_build_object('role', 'member', 'user_id', uid, 'admin_id', m.admin_id, 'username', m.username,
    'display_name', m.display_name, 'status', m.status, 'cash', m.cash);
end $$;

-- ---------- 管理员：邀请码与用户 ----------
create or replace function hw_private.require_admin()
returns uuid language plpgsql stable security definer set search_path = '' as $$
declare uid uuid := auth.uid();
begin
  if uid is null then raise exception 'NOT_AUTHENTICATED' using errcode = '28000'; end if;
  if exists (select 1 from public.hw_members where user_id = uid) then raise exception '普通用户不能管理' using errcode = '42501'; end if;
  return uid;
end $$;

-- 生成 p_count 个一次性邀请码（8 位，不含容易看错的字符），每个注册后发 p_cash 模拟币
create or replace function public.hw_create_invites(p_note text default '', p_cash numeric default 10000, p_count int default 1)
returns setof public.hw_invites language plpgsql security definer set search_path = '' as $$
declare uid uuid := hw_private.require_admin(); alphabet text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; c text; i int; k int; b bytea; r public.hw_invites;
begin
  if p_count < 1 or p_count > 50 then raise exception '一次最多生成 50 个邀请码'; end if;
  if p_cash < 0 or p_cash > 100000000 then raise exception '初始资金不合理'; end if;
  for i in 1..p_count loop
    loop
      b := decode(md5(gen_random_uuid()::text || clock_timestamp()::text), 'hex');
      c := '';
      for k in 0..7 loop c := c || substr(alphabet, (get_byte(b, k) % 32) + 1, 1); end loop;
      exit when not exists (select 1 from public.hw_invites where code = c);
    end loop;
    insert into public.hw_invites (code, admin_id, note, initial_cash) values (c, uid, coalesce(p_note, ''), p_cash) returning * into r;
    return next r;
  end loop;
end $$;

-- 作废还没用过的邀请码
create or replace function public.hw_revoke_invite(p_code text)
returns void language plpgsql security definer set search_path = '' as $$
declare uid uuid := hw_private.require_admin();
begin
  update public.hw_invites set revoked = true where code = upper(p_code) and admin_id = uid and used_at is null;
  if not found then raise exception '邀请码不存在或已使用'; end if;
end $$;

-- 停用 / 启用用户（停用后不能登录查看数据、不能交易，挂单撤销）
create or replace function public.hw_set_member_status(p_user uuid, p_status text)
returns void language plpgsql security definer set search_path = '' as $$
declare uid uuid := hw_private.require_admin();
begin
  if p_status not in ('active', 'disabled') then raise exception '状态不对'; end if;
  update public.hw_members set status = p_status where user_id = p_user and admin_id = uid;
  if not found then raise exception '没有这个用户'; end if;
  if p_status = 'disabled' then
    update public.hw_orders set status = 'cancelled', reason = '用户已停用', done_at = now() where user_id = p_user and status = 'open';
  end if;
end $$;

-- 改备注名
create or replace function public.hw_rename_member(p_user uuid, p_name text)
returns void language plpgsql security definer set search_path = '' as $$
declare uid uuid := hw_private.require_admin();
begin
  update public.hw_members set display_name = left(coalesce(p_name, ''), 40) where user_id = p_user and admin_id = uid;
  if not found then raise exception '没有这个用户'; end if;
end $$;

-- 发放（正数）或收回（负数）模拟币
create or replace function public.hw_grant(p_user uuid, p_amount numeric, p_note text default '')
returns numeric language plpgsql security definer set search_path = '' as $$
declare uid uuid := hw_private.require_admin(); newcash numeric;
begin
  if p_amount = 0 or abs(p_amount) > 100000000 then raise exception '金额不合理'; end if;
  update public.hw_members set cash = cash + p_amount where user_id = p_user and admin_id = uid returning cash into newcash;
  if newcash is null then raise exception '没有这个用户'; end if;
  if newcash < 0 then raise exception '收回后现金不能小于 0'; end if;
  insert into public.hw_cash_log (user_id, admin_id, amount, note, cash_after)
  values (p_user, uid, p_amount, coalesce(nullif(p_note, ''), case when p_amount > 0 then '管理员发放' else '管理员收回' end), newcash);
  return newcash;
end $$;

-- 移除用户：删除账户资料、持仓、委托、成交、资金记录（登录账号失效，无法再看数据）
create or replace function public.hw_remove_member(p_user uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare uid uuid := hw_private.require_admin();
begin
  if not exists (select 1 from public.hw_members where user_id = p_user and admin_id = uid) then raise exception '没有这个用户'; end if;
  delete from public.hw_trades where user_id = p_user and admin_id = uid;
  delete from public.hw_orders where user_id = p_user and admin_id = uid;
  delete from public.hw_positions where user_id = p_user and admin_id = uid;
  delete from public.hw_cash_log where user_id = p_user and admin_id = uid;
  delete from public.hw_members where user_id = p_user and admin_id = uid;
end $$;

-- ---------- 成交 ----------
-- 保证金：现金 − 2 × 空头市值
create or replace function hw_private.margin(p_user uuid, p_admin uuid, p_cash numeric)
returns numeric language sql stable security definer set search_path = '' as $$
  select p_cash - 2 * coalesce(sum(-p.qty * greatest(coalesce(pr.price, 1), 1)), 0)
  from public.hw_positions p left join public.hw_prices pr on pr.admin_id = p_admin and pr.symbol = p.symbol
  where p.user_id = p_user and p.qty < 0
$$;

-- 按价格 p_price 成交一笔；成功返回 null，失败返回原因
create or replace function hw_private.execute(p_user uuid, p_admin uuid, p_symbol text, p_side text, p_qty bigint, p_price numeric, p_order bigint)
returns text language plpgsql security definer set search_path = '' as $$
declare
  m public.hw_members; pos public.hw_positions;
  oldq bigint; newq bigint; avg numeric; realized numeric := 0; delta bigint; closing bigint;
  newcash numeric; before numeric; after numeric;
begin
  select * into m from public.hw_members where user_id = p_user and admin_id = p_admin for update;
  if m.user_id is null or m.status <> 'active' then return '账号不可交易'; end if;
  select * into pos from public.hw_positions where user_id = p_user and symbol = p_symbol for update;
  oldq := coalesce(pos.qty, 0); avg := coalesce(pos.avg_price, 0);
  delta := case when p_side = 'buy' then p_qty else -p_qty end;
  newq := oldq + delta;
  newcash := m.cash - delta * p_price;
  -- 持仓均价与已实现盈亏
  if oldq = 0 or sign(oldq) = sign(delta) then
    avg := (abs(oldq) * avg + abs(delta) * p_price) / abs(newq);
  else
    closing := least(abs(oldq), abs(delta));
    realized := closing * (p_price - avg) * sign(oldq);
    if abs(delta) > abs(oldq) then avg := p_price; elsif newq = 0 then avg := 0; end if;
  end if;
  -- 保证金检查
  before := hw_private.margin(p_user, p_admin, m.cash);
  insert into public.hw_positions (user_id, admin_id, symbol, qty, avg_price, realized, updated_at)
  values (p_user, p_admin, p_symbol, newq, avg, realized, now())
  on conflict (user_id, symbol) do update set qty = excluded.qty, avg_price = excluded.avg_price,
    realized = public.hw_positions.realized + excluded.realized, updated_at = now();
  after := hw_private.margin(p_user, p_admin, newcash);
  if after < 0 and after < before then
    raise exception 'HW_MARGIN';
  end if;
  update public.hw_members set cash = newcash where user_id = p_user;
  insert into public.hw_trades (user_id, admin_id, symbol, side, qty, price, amount, realized, cash_after, order_id)
  values (p_user, p_admin, p_symbol, p_side, p_qty, p_price, p_qty * p_price, realized, newcash, p_order);
  return null;
exception when raise_exception then
  if sqlerrm = 'HW_MARGIN' then return '资金不足（做空需要保留 2 倍空头市值的现金）'; end if;
  raise;
end $$;

-- 下单：p_limit 为空是市价单（立即按当前价成交）；限价单在价格到了时自动成交（买入：价格 ≤ 限价；卖出：价格 ≥ 限价）
create or replace function public.hw_place_order(p_symbol text, p_side text, p_qty bigint, p_limit numeric default null)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  uid uuid := auth.uid(); adm uuid := hw_private.my_admin(); pr public.hw_prices; px numeric; oid bigint; err text;
begin
  if uid is null then raise exception 'NOT_AUTHENTICATED' using errcode = '28000'; end if;
  if adm is null then raise exception '只有普通用户能交易（或账号已停用）'; end if;
  if p_side not in ('buy', 'sell') then raise exception '方向不对'; end if;
  if p_qty < 1 or p_qty > 1000000 then raise exception '数量要在 1 到 1000000 之间'; end if;
  if p_limit is not null and (p_limit <= 0 or p_limit > 100000) then raise exception '限价不合理'; end if;
  select * into pr from public.hw_prices where admin_id = adm and symbol = p_symbol;
  if pr.symbol is null or not pr.active then raise exception '没有这只股票或已停牌'; end if;
  px := greatest(pr.price, 1);
  insert into public.hw_orders (user_id, admin_id, symbol, side, qty, limit_price)
  values (uid, adm, p_symbol, p_side, p_qty, p_limit) returning id into oid;
  if p_limit is null or (p_side = 'buy' and px <= p_limit) or (p_side = 'sell' and px >= p_limit) then
    err := hw_private.execute(uid, adm, p_symbol, p_side, p_qty, px, oid);
    if err is null then
      update public.hw_orders set status = 'filled', fill_price = px, done_at = now() where id = oid;
      return jsonb_build_object('id', oid, 'status', 'filled', 'price', px);
    end if;
    update public.hw_orders set status = 'cancelled', reason = err, done_at = now() where id = oid;
    return jsonb_build_object('id', oid, 'status', 'cancelled', 'reason', err);
  end if;
  return jsonb_build_object('id', oid, 'status', 'open');
end $$;

create or replace function public.hw_cancel_order(p_id bigint)
returns void language plpgsql security definer set search_path = '' as $$
begin
  update public.hw_orders set status = 'cancelled', reason = '已撤单', done_at = now()
  where id = p_id and user_id = (select auth.uid()) and status = 'open';
  if not found then raise exception '委托不存在或已成交'; end if;
end $$;

-- 撮合某位管理员的挂单（价格更新后调用）
create or replace function hw_private.match_orders(p_admin uuid)
returns int language plpgsql security definer set search_path = '' as $$
declare o record; err text; n int := 0;
begin
  for o in
    select od.id, od.user_id, od.symbol, od.side, od.qty, od.limit_price, greatest(pr.price, 1) as px
    from public.hw_orders od join public.hw_prices pr on pr.admin_id = od.admin_id and pr.symbol = od.symbol
    where od.admin_id = p_admin and od.status = 'open' and pr.active
      and ((od.side = 'buy' and greatest(pr.price, 1) <= od.limit_price) or (od.side = 'sell' and greatest(pr.price, 1) >= od.limit_price))
    order by od.created_at, od.id
  loop
    err := hw_private.execute(o.user_id, p_admin, o.symbol, o.side, o.qty, o.px, o.id);
    if err is null then
      update public.hw_orders set status = 'filled', fill_price = o.px, done_at = now() where id = o.id; n := n + 1;
    else
      update public.hw_orders set status = 'cancelled', reason = err, done_at = now() where id = o.id;
    end if;
  end loop;
  return n;
end $$;

-- 管理员发布股价：[{symbol, name, kind, price}]；没出现的股票停牌（已有持仓按最后价格计算）。返回撮合成交的挂单数
create or replace function public.hw_publish_prices(p_prices jsonb)
returns int language plpgsql security definer set search_path = '' as $$
declare uid uuid := hw_private.require_admin();
begin
  if jsonb_typeof(p_prices) <> 'array' or jsonb_array_length(p_prices) > 2000 then raise exception '股价格式不对'; end if;
  insert into public.hw_prices (admin_id, symbol, name, kind, price, active, updated_at)
  select uid, x->>'symbol', left(coalesce(x->>'name', ''), 60), left(coalesce(x->>'kind', ''), 4),
         round(least(greatest((x->>'price')::numeric, 0), 100000), 2), true, now()
  from jsonb_array_elements(p_prices) x
  where coalesce(x->>'symbol', '') <> '' and (x->>'price') is not null
  on conflict (admin_id, symbol) do update set name = excluded.name, kind = excluded.kind, price = excluded.price, active = true, updated_at = now()
  where public.hw_prices.price is distinct from excluded.price or public.hw_prices.name is distinct from excluded.name or not public.hw_prices.active;
  update public.hw_prices set active = false, updated_at = now()
  where admin_id = uid and active and symbol not in (select x->>'symbol' from jsonb_array_elements(p_prices) x);
  return hw_private.match_orders(uid);
end $$;

-- 收益排行：同一位管理员下的所有用户（普通用户看同伴，管理员看自己的用户）
create or replace function public.hw_leaderboard()
returns table (user_id uuid, username text, display_name text, status text, cash numeric, stock_value numeric, equity numeric, invested numeric, profit numeric)
language plpgsql stable security definer set search_path = '' as $$
declare adm uuid := coalesce(hw_private.my_admin(), case when hw_private.is_member() then null else auth.uid() end);
begin
  if adm is null then raise exception '没有权限'; end if;
  return query
  select m.user_id, m.username, m.display_name, m.status, m.cash,
         coalesce(v.val, 0)::numeric,
         (m.cash + coalesce(v.val, 0))::numeric,
         coalesce(g.inv, 0)::numeric,
         (m.cash + coalesce(v.val, 0) - coalesce(g.inv, 0))::numeric
  from public.hw_members m
  left join lateral (
    select sum(p.qty * greatest(coalesce(pr.price, 1), 1)) as val
    from public.hw_positions p left join public.hw_prices pr on pr.admin_id = adm and pr.symbol = p.symbol
    where p.user_id = m.user_id
  ) v on true
  left join lateral (select sum(c.amount) as inv from public.hw_cash_log c where c.user_id = m.user_id) g on true
  where m.admin_id = adm
  order by 9 desc, 2;
end $$;

grant execute on function public.hw_me(), public.hw_create_invites(text, numeric, int), public.hw_revoke_invite(text),
  public.hw_set_member_status(uuid, text), public.hw_rename_member(uuid, text), public.hw_grant(uuid, numeric, text),
  public.hw_remove_member(uuid), public.hw_place_order(text, text, bigint, numeric), public.hw_cancel_order(bigint),
  public.hw_publish_prices(jsonb), public.hw_leaderboard() to authenticated;
revoke execute on function public.hw_me(), public.hw_create_invites(text, numeric, int), public.hw_revoke_invite(text),
  public.hw_set_member_status(uuid, text), public.hw_rename_member(uuid, text), public.hw_grant(uuid, numeric, text),
  public.hw_remove_member(uuid), public.hw_place_order(text, text, bigint, numeric), public.hw_cancel_order(bigint),
  public.hw_publish_prices(jsonb), public.hw_leaderboard() from anon, public;
revoke execute on function hw_private.execute(uuid, uuid, text, text, bigint, numeric, bigint), hw_private.match_orders(uuid),
  hw_private.margin(uuid, uuid, numeric), hw_private.require_admin() from public, anon, authenticated;

-- 普通用户不能上传自己的班级数据（他们只读看管理员的）
create or replace function hw_private.block_member_snapshot()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if exists (select 1 from public.hw_members where user_id = new.user_id) then raise exception '普通用户不能上传班级数据' using errcode = '42501'; end if;
  return new;
end $$;
drop trigger if exists hw_snapshots_block_member on public.hw_snapshots;
create trigger hw_snapshots_block_member before insert or update on public.hw_snapshots
  for each row execute function hw_private.block_member_snapshot();
