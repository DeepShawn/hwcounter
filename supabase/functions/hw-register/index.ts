// 作业通 · 凭邀请码注册普通用户（用户名 + 密码），一个邀请码只能用一次。
// 部署：Supabase Edge Function "hw-register"，verify_jwt = false（邀请码就是凭证）。
// 用户名映射成内部邮箱 <用户名>@member.hwcounter.invalid（.invalid 是保留域名，不会发邮件），登录时 App 自动补全。
import { createClient } from "npm:@supabase/supabase-js@2.45.4";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const DOMAIN = "member.hwcounter.invalid";
const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { ...CORS, "Content-Type": "application/json" } });

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json(405, { error: "只支持 POST" });
  let body: Record<string, unknown>;
  try { body = await req.json(); } catch { return json(400, { error: "请求格式不对" }); }
  const code = String(body.code ?? "").trim().toUpperCase();
  const username = String(body.username ?? "").trim().toLowerCase();
  const password = String(body.password ?? "");
  const name = String(body.name ?? "").trim().slice(0, 40);
  if (!/^[A-Z0-9]{8}$/.test(code)) return json(400, { error: "邀请码是 8 位字母和数字" });
  if (!/^[a-z0-9_]{3,20}$/.test(username)) return json(400, { error: "用户名 3–20 位，只能用字母、数字、下划线" });
  if (password.length < 6 || password.length > 72) return json(400, { error: "密码至少 6 位" });

  const db = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });

  // 防暴力猜邀请码：同一 IP 1 小时失败 10 次、全站 10 分钟失败 100 次就暂停（见 supabase/004_security.sql）
  const ip = (req.headers.get("cf-connecting-ip") ?? req.headers.get("x-forwarded-for")?.split(",")[0] ?? req.headers.get("x-real-ip") ?? "").trim();
  const guard = await db.rpc("hw_register_guard", { p_ip: ip });
  if (guard.error) console.error("hw_register_guard", guard.error.message); // 守卫出错时不挡正常注册
  else if (guard.data === false) return json(429, { error: "尝试次数太多，请 1 小时后再试" });
  const record = (ok: boolean) => db.rpc("hw_register_guard", { p_ip: ip, p_ok: ok });

  const { data: taken } = await db.from("hw_members").select("user_id").eq("username", username).maybeSingle();
  if (taken) return json(409, { error: "这个用户名已被使用" });

  // 先占用邀请码（一条 UPDATE，并发时只有一个人能占到）
  const claimed = await db.from("hw_invites").update({ used_at: new Date().toISOString() })
    .eq("code", code).is("used_at", null).eq("revoked", false).select("admin_id, initial_cash");
  if (claimed.error) return json(500, { error: "服务器出错，请稍后再试" });
  const inv = claimed.data?.[0];
  if (!inv) { await record(false); return json(400, { error: "邀请码无效、已作废或已被使用" }); }
  const release = () => db.from("hw_invites").update({ used_at: null }).eq("code", code).is("used_by", null);

  const created = await db.auth.admin.createUser({
    email: `${username}@${DOMAIN}`, password, email_confirm: true,
    user_metadata: { app: "hwcounter", role: "member", username },
  });
  if (created.error || !created.data.user) {
    await release();
    const msg = created.error?.message ?? "";
    if (/already|registered|exists/i.test(msg)) return json(409, { error: "这个用户名已被使用" });
    if (/password/i.test(msg)) return json(400, { error: "密码太简单：需要同时包含小写字母、大写字母、数字和符号（如 Abc123!）" });
    return json(400, { error: `注册失败：${msg}` });
  }
  const uid = created.data.user.id;
  const cash = Number(inv.initial_cash ?? 0);
  const ins = await db.from("hw_members").insert({ user_id: uid, admin_id: inv.admin_id, username, display_name: name || username, invite_code: code, cash });
  if (ins.error) {
    await db.auth.admin.deleteUser(uid);
    await release();
    return json(400, { error: ins.error.code === "23505" ? "这个用户名已被使用" : "注册失败，请稍后再试" });
  }
  await db.from("hw_invites").update({ used_by: uid }).eq("code", code);
  if (cash > 0) await db.from("hw_cash_log").insert({ user_id: uid, admin_id: inv.admin_id, amount: cash, note: "注册赠送", cash_after: cash });
  await record(true);
  return json(200, { ok: true, username, email: `${username}@${DOMAIN}` });
});
