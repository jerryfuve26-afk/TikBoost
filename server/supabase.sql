-- TikBoost 3.0 · Base de datos de cuentas, límites y Premium
-- Ejecuta este archivo una sola vez en Supabase > SQL Editor.

create table if not exists public.profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  plan text not null default 'free' check (plan in ('free','premium')),
  daily_used integer not null default 0 check (daily_used >= 0),
  daily_date date not null default current_date,
  premium_until timestamptz,
  purchase_token text unique,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.profiles enable row level security;

drop policy if exists "profiles_select_own" on public.profiles;
create policy "profiles_select_own"
on public.profiles for select
to authenticated
using (auth.uid() = user_id);

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer set search_path = public
as $$
begin
  insert into public.profiles(user_id) values (new.id)
  on conflict (user_id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
after insert on auth.users
for each row execute procedure public.handle_new_user();

create or replace function public.consume_generation(p_user_id uuid, p_free_limit integer default 5, p_premium_limit integer default 100)
returns table(
  allowed boolean,
  plan text,
  used integer,
  daily_limit integer,
  remaining integer,
  premium_until timestamptz
)
language plpgsql
security definer set search_path = public
as $$
declare
  p public.profiles%rowtype;
  effective_plan text;
  limit_count integer;
begin
  insert into public.profiles(user_id) values (p_user_id)
  on conflict (user_id) do nothing;

  select * into p from public.profiles where user_id = p_user_id for update;

  if p.daily_date <> current_date then
    update public.profiles
      set daily_used = 0, daily_date = current_date, updated_at = now()
      where user_id = p_user_id
      returning * into p;
  end if;

  if p.plan = 'premium' and p.premium_until is not null and p.premium_until > now() then
    effective_plan := 'premium';
    limit_count := greatest(1, p_premium_limit);
  else
    effective_plan := 'free';
    limit_count := greatest(1, p_free_limit);
    if p.plan <> 'free' then
      update public.profiles set plan='free', premium_until=null, updated_at=now() where user_id=p_user_id returning * into p;
    end if;
  end if;

  if p.daily_used >= limit_count then
    allowed := false;
    plan := effective_plan;
    used := p.daily_used;
    daily_limit := limit_count;
    remaining := 0;
    premium_until := case when effective_plan='premium' then p.premium_until else null end;
    return next;
    return;
  end if;

  update public.profiles
    set daily_used = daily_used + 1, updated_at = now()
    where user_id = p_user_id
    returning * into p;

  allowed := true;
  plan := effective_plan;
  used := p.daily_used;
  daily_limit := limit_count;
  remaining := greatest(0, limit_count - p.daily_used);
  premium_until := case when effective_plan='premium' then p.premium_until else null end;
  return next;
end;
$$;

create or replace function public.refund_generation(p_user_id uuid)
returns void
language plpgsql
security definer set search_path = public
as $$
begin
  update public.profiles
  set daily_used = greatest(0, daily_used - 1), updated_at = now()
  where user_id = p_user_id and daily_date = current_date;
end;
$$;

revoke all on function public.consume_generation(uuid, integer, integer) from public, anon, authenticated;
revoke all on function public.refund_generation(uuid) from public, anon, authenticated;
grant execute on function public.consume_generation(uuid, integer, integer) to service_role;
grant execute on function public.refund_generation(uuid) to service_role;
