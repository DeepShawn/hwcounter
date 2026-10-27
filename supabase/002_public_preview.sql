-- 作业通 · 未登录预览（只读）
-- 在 Supabase 控制台 → SQL Editor 中运行一次即可（项目：DeepShawn's Project）
-- 账号拥有者在 App 里打开“允许未登录预览”后，任何人无需登录即可只读查看该份数据；关闭后恢复私密。

alter table public.hw_snapshots add column if not exists public_read boolean not null default false;

drop policy if exists "hw_snapshots_public_preview" on public.hw_snapshots;
create policy "hw_snapshots_public_preview" on public.hw_snapshots
  for select to anon, authenticated
  using (public_read);

grant select on public.hw_snapshots to anon;
