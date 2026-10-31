-- ShadowClient: Status, Direktnachrichten, Einladungen, Partys
-- In Supabase → SQL Editor einfügen und "Run" drücken.

alter table public.shadowclient_users add column if not exists status text;

create table if not exists public.shadowclient_messages (
  id         bigserial primary key,
  sender     text not null,
  recipient  text not null,
  kind       text not null default 'text' check (kind in ('text', 'invite', 'party')),
  body       text,
  server     text,
  party_id   text,
  created_at timestamptz not null default now(),
  read_at    timestamptz
);
create index if not exists shadowclient_messages_recipient_idx on public.shadowclient_messages (recipient, read_at);
create index if not exists shadowclient_messages_pair_idx on public.shadowclient_messages (sender, recipient, created_at desc);
alter table public.shadowclient_messages enable row level security;

create table if not exists public.shadowclient_parties (
  id         text primary key,
  leader     text not null,
  created_at timestamptz not null default now()
);
create table if not exists public.shadowclient_party_members (
  party_id  text not null references public.shadowclient_parties(id) on delete cascade,
  uuid      text not null unique,
  joined_at timestamptz not null default now(),
  primary key (party_id, uuid)
);
alter table public.shadowclient_parties enable row level security;
alter table public.shadowclient_party_members enable row level security;

-- Alte Nachrichten (älter als 30 Tage) jeden Tag löschen
create extension if not exists pg_cron;
select cron.unschedule(jobid) from cron.job where jobname = 'shadowclient-message-cleanup';
select cron.schedule('shadowclient-message-cleanup', '17 4 * * *',
  $$delete from public.shadowclient_messages where created_at < now() - interval '30 days'$$);
