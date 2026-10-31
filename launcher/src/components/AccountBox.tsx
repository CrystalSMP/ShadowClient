import { LogIn } from "lucide-react";
import { useAccounts } from "../lib/accounts";

export function headUrl(uuid: string) {
  // Öffentlicher Avatar-Dienst; bei Fehler zeigt das img einfach den Initialbuchstaben-Hintergrund.
  return `https://mc-heads.net/avatar/${uuid}/64`;
}

export default function AccountBox({ onLogin }: { onLogin: () => void }) {
  const { active } = useAccounts();
  if (!active) {
    return (
      <button className="nav-item account-login" onClick={onLogin} title="Anmelden">
        <LogIn size={20} /><span>Anmelden</span>
      </button>
    );
  }
  return (
    <div className="account-box" title={active.name}>
      <div className="avatar" data-letter={active.name[0]}>
        <img src={headUrl(active.uuid)} alt="" onError={(e) => (e.currentTarget.style.display = "none")} />
      </div>
      <span className="account-name">{active.name}</span>
    </div>
  );
}
