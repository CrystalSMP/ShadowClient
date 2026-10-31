-- ShadowClient: Spieler, die das Abzeichen aktiviert haben (opt-in).
create table if not exists public.shadowclient_users (
  uuid       text primary key,            -- Minecraft-UUID ohne Bindestriche (von Mojang bestätigt)
  name       text not null,
  last_seen  timestamptz not null default now()
);

create index if not exists shadowclient_users_last_seen_idx on public.shadowclient_users (last_seen desc);

-- Zugriff nur über die Edge Function (Service Role). Keine direkten Lese-/Schreibrechte für anon.
alter table public.shadowclient_users enable row level security;
