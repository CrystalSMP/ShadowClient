-- ShadowClient: Sitzungen automatisch aufräumen
-- In Supabase → SQL Editor einfügen und "Run" drücken.

-- 1) Welcher Client die Sitzung hat: 'mod' (Spiel) oder 'launcher'
alter table public.shadowclient_sessions add column if not exists client text not null default 'mod';
alter table public.shadowclient_sessions add column if not exists created_at timestamptz not null default now();

-- 2) Neue Sitzung ersetzt die alte desselben Spielers im selben Client
create or replace function public.shadowclient_replace_sessions() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  delete from public.shadowclient_sessions
   where uuid = new.uuid and client = new.client and token <> new.token;
  delete from public.shadowclient_sessions where expires_at < now();
  return new;
end $$;

drop trigger if exists shadowclient_sessions_replace on public.shadowclient_sessions;
create trigger shadowclient_sessions_replace after insert on public.shadowclient_sessions
  for each row execute function public.shadowclient_replace_sessions();

-- 3) Einmalig: pro Spieler & Client nur die neueste Sitzung behalten
delete from public.shadowclient_sessions s
 using public.shadowclient_sessions n
 where s.uuid = n.uuid and s.client = n.client and s.expires_at < n.expires_at;
delete from public.shadowclient_sessions where expires_at < now();

-- 4) Alle 15 Minuten abgelaufene Sitzungen löschen
create extension if not exists pg_cron;
select cron.unschedule(jobid) from cron.job where jobname = 'shadowclient-session-cleanup';
select cron.schedule('shadowclient-session-cleanup', '*/15 * * * *',
  $$delete from public.shadowclient_sessions where expires_at < now()$$);
