-- Eigene Capes (Uploads der Nutzer) mit Moderation
create table if not exists public.shadowclient_capes (
  id         text primary key,
  owner      text not null,
  path       text not null,
  status     text not null default 'pending' check (status in ('pending', 'approved', 'rejected')),
  created_at timestamptz not null default now()
);
create index if not exists shadowclient_capes_owner_idx on public.shadowclient_capes (owner);
create index if not exists shadowclient_capes_status_idx on public.shadowclient_capes (status);
alter table public.shadowclient_capes enable row level security;

-- Ausgewähltes Cape: 'b:<id>' = eingebautes Cape, 'c:<id>' = eigenes (freigegebenes) Cape
alter table public.shadowclient_users add column if not exists cape text;

-- Öffentlicher Storage-Bucket für Cape-Bilder (Schreiben nur über die Edge Function)
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('capes', 'capes', true, 65536, array['image/png'])
on conflict (id) do nothing;
