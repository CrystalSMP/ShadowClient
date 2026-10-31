alter table public.shadowclient_users add column if not exists server text;
alter table public.shadowclient_users add column if not exists show_badge boolean not null default true;
alter table public.shadowclient_users add column if not exists share_server boolean not null default true;

create table if not exists public.shadowclient_sessions (
  token text primary key,
  uuid text not null,
  expires_at timestamptz not null
);
create index if not exists shadowclient_sessions_uuid_idx on public.shadowclient_sessions (uuid);

create table if not exists public.shadowclient_friends (
  requester text not null,
  target text not null,
  status text not null default 'pending' check (status in ('pending', 'accepted')),
  created_at timestamptz not null default now(),
  primary key (requester, target)
);
create index if not exists shadowclient_friends_target_idx on public.shadowclient_friends (target);

alter table public.shadowclient_sessions enable row level security;
alter table public.shadowclient_friends enable row level security;
