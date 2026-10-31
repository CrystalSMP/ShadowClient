import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import type { Account } from "@shared/types";
import { api } from "./api";

interface Ctx {
  accounts: Account[];
  active: Account | null;
  setActive: (uuid: string) => void;
  reload: () => Promise<void>;
  remove: (uuid: string) => Promise<void>;
}

const AccountsContext = createContext<Ctx | null>(null);

export function AccountsProvider({ children }: { children: ReactNode }) {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);

  const reload = useCallback(async () => {
    const list = await api.listAccounts().catch(() => []);
    setAccounts(list);
    setActiveId((cur) => (cur && list.some((a) => a.uuid === cur) ? cur : list[0]?.uuid ?? null));
  }, []);

  useEffect(() => { reload(); }, [reload]);

  const remove = useCallback(async (uuid: string) => { await api.removeAccount(uuid); await reload(); }, [reload]);
  const active = accounts.find((a) => a.uuid === activeId) ?? null;

  return (
    <AccountsContext.Provider value={{ accounts, active, setActive: setActiveId, reload, remove }}>
      {children}
    </AccountsContext.Provider>
  );
}

export function useAccounts() {
  const ctx = useContext(AccountsContext);
  if (!ctx) throw new Error("useAccounts außerhalb von AccountsProvider");
  return ctx;
}
